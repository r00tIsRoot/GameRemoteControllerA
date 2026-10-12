package com.example.gameremotecontroller.net

import android.util.Log
import com.example.gameremotecontroller.protocol.Ack
import com.example.gameremotecontroller.protocol.Hello
import com.example.gameremotecontroller.protocol.HelloOk
import com.example.gameremotecontroller.protocol.KeyDown
import com.example.gameremotecontroller.protocol.KeyUp
import com.example.gameremotecontroller.protocol.MouseDown
import com.example.gameremotecontroller.protocol.MouseMove
import com.example.gameremotecontroller.protocol.MouseUp
import com.example.gameremotecontroller.protocol.Ping
import com.example.gameremotecontroller.protocol.RemoteError
import com.example.gameremotecontroller.protocol.RemoteJson
import com.example.gameremotecontroller.protocol.RemoteMessage
import com.example.gameremotecontroller.protocol.Wheel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * 02-protocol.md 구현.
 * - 재접속 1s/2s/5s 백오프, 재접속시 hello 재전송
 * - heartbeat ping 15s
 * - mouse_move 8ms 배치 coalescing + 125Hz 상한
 * - HOLD는 DOWN 1회 / UP 1회 + 2s 킵얼라이브(VM 담당), 서버 5s 자동릴리즈 회피
 * - 전송 액터: key/mouse/wheel/ping/hello를 단일 채널로 FIFO 순서 보장
 *   (down→up 역전으로 눌림 걸림 방지). mouse_move는 coalescing 후 합류.
 * - TAP은 down 후 40ms 최소 누름 후 up (폴링 게임에서 씹힘 방지)
 */
class GameRemoteClient(
  private val okHttp: OkHttpClient = OkHttpClient.Builder().pingInterval(0, TimeUnit.MILLISECONDS).build(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val tag = "GameRemoteClient"

  private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
  val state: StateFlow<ConnectionState> = _state.asStateFlow()

  private val _incoming = MutableSharedFlow<RemoteMessage>(extraBufferCapacity = 64)
  val incoming: SharedFlow<RemoteMessage> = _incoming.asSharedFlow()

  private val sendMutex = Mutex()
  private var ws: WebSocket? = null
  private var lastUrl: String? = null
  private var lastProfile: String = ""
  private var lastToken: String = ""
  private var manualDisconnect = false
  private var reconnectJob: Job? = null
  private var heartbeatJob: Job? = null
  private val pingId = AtomicLong(0)
  private val pingSentAt = mutableMapOf<Long, Long>()

  // mouse coalescing
  private data class Delta(val dx: Int, val dy: Int)

  private val mouseChannel = Channel<Delta>(capacity = 256)
  private var mouseLoopStarted = false
  private val mouseLoopMutex = Mutex()

  // 전송 액터: 모든 송신(키/마우스버튼/휠/ping/hello)을 FIFO로 직렬화.
  // down→up 역전으로 키 걸림이 생기지 않게 한다.
  private val sendChannel = Channel<RemoteMessage>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (msg in sendChannel) sendInternal(msg)
    }
  }

  private fun send(msg: RemoteMessage) {
    sendChannel.trySend(msg)
  }

  fun connect(
    url: String,
    profileName: String,
    token: String = "",
  ) {
    manualDisconnect = false
    lastUrl = url
    lastProfile = profileName
    lastToken = token
    reconnectJob?.cancel()
    openSocket(url, profileName, token)
    startMouseLoopIfNeeded()
    startHeartbeat()
  }

  fun disconnect() {
    manualDisconnect = true
    reconnectJob?.cancel()
    heartbeatJob?.cancel()
    runCatching { ws?.close(1000, "bye") }
    ws = null
    _state.value = ConnectionState.Disconnected()
  }

  private fun openSocket(
    url: String,
    profileName: String,
    token: String,
  ) {
    _state.value = ConnectionState.Connecting
    runCatching { ws?.cancel() }
    val request = Request.Builder().url(url).build()
    ws =
      okHttp.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(
            webSocket: WebSocket,
            response: Response,
          ) {
            send(Hello(proto = 1, profile = profileName, token = token))
          }

          override fun onMessage(
            webSocket: WebSocket,
            text: String,
          ) {
            val msg = RemoteJson.decodeLenient(text) ?: return
            scope.launch { _incoming.emit(msg) }
            when (msg) {
              is HelloOk -> _state.value = ConnectionState.Connected()
              is RemoteError -> {
                // unauthorized만 연결 상태로 반영. not_in_foreground 등은
                // 일시적 드롭이므로 토스트(incoming)만 보내고 Connected 유지.
                if (msg.reason == "unauthorized") {
                  _state.value = ConnectionState.Error(msg.reason)
                }
              }
              is Ack -> {
                val sentAt = pingSentAt.remove(msg.id)
                val rtt = if (sentAt != null) System.currentTimeMillis() - sentAt else msg.rtt
                Log.d(tag, "ack id=${msg.id} rtt=$rtt")
                _state.value = ConnectionState.Connected(rttMs = rtt)
              }
              else -> Unit
            }
          }

          override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
          ) {
            Log.w(tag, "ws failure: ${t.message}")
            if (manualDisconnect) return
            _state.value = ConnectionState.Disconnected(t.message)
            scheduleReconnect()
          }

          override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
          ) {
            if (manualDisconnect) return
            _state.value = ConnectionState.Disconnected(reason)
            scheduleReconnect()
          }
        },
      )
  }

  private fun scheduleReconnect() {
    if (manualDisconnect || reconnectJob?.isActive == true) return
    reconnectJob =
      scope.launch {
        val backoffs = listOf(1000L, 2000L, 5000L)
        for (b in backoffs) {
          if (manualDisconnect) return@launch
          delay(b)
          if (manualDisconnect) return@launch
          val url = lastUrl ?: return@launch
          // 재접속시 hello 재전송 (openSocket onOpen에서 수행)
          openSocket(url, lastProfile, lastToken)
          // 연결 성공하면 종료 (다음 onMessage에서 Connected로 전이)
          delay(1500)
          if (_state.value is ConnectionState.Connected) return@launch
        }
      }
  }

  private fun startHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob =
      scope.launch {
        while (!manualDisconnect) {
          delay(15_000)
          val cur = _state.value
          if (cur is ConnectionState.Connected) {
            val id = pingId.incrementAndGet()
            pingSentAt[id] = System.currentTimeMillis()
            send(Ping(id = id))
          }
        }
      }
  }

  private fun startMouseLoopIfNeeded() {
    if (mouseLoopStarted) return
    mouseLoopStarted = true
    scope.launch {
      var accDx = 0
      var accDy = 0
      var lastFlush = 0L
      while (true) {
        // 8ms 배치 윈도우
        delay(8)
        mouseLoopMutex.withLock {
          var d = mouseChannel.tryReceive().getOrNull()
          while (d != null) {
            accDx += d.dx
            accDy += d.dy
            d = mouseChannel.tryReceive().getOrNull()
          }
        }
        if (accDx != 0 || accDy != 0) {
          val now = System.currentTimeMillis()
          // 125Hz 상한 (8ms 간격)
          if (now - lastFlush >= 8) {
            lastFlush = now
            send(MouseMove(dx = accDx, dy = accDy))
            accDx = 0
            accDy = 0
          }
        }
      }
    }
  }

  private suspend fun sendInternal(msg: RemoteMessage) {
    val socket = ws ?: return
    val text = RemoteJson.encode(msg)
    sendMutex.withLock {
      runCatching { socket.send(text) }.onFailure { Log.w(tag, "send fail: ${it.message}") }
    }
  }

  // ---- 전송 API (액터 경유, 호출 순서대로 전송) ----
  fun sendKeyDown(code: String) {
    send(KeyDown(code = code, ts = System.currentTimeMillis()))
  }

  fun sendKeyUp(code: String) {
    send(KeyUp(code = code, ts = System.currentTimeMillis()))
  }

  /**
   * TAP: down 즉시 + 40ms 최소 누름 후 up.
   * 폴링 방식 게임에서 0ms 탭이 씹히는 것을 방지. up은 액터에 40ms 후 합류
   * (그 사이 다른 입력이 먼저 가는 것이 시간순으로 정확).
   */
  fun sendTap(code: String) {
    send(KeyDown(code = code, ts = System.currentTimeMillis()))
    scope.launch {
      delay(40)
      send(KeyUp(code = code, ts = System.currentTimeMillis()))
    }
  }

  fun sendMouseMove(
    dx: Int,
    dy: Int,
  ) {
    scope.launch {
      mouseLoopMutex.withLock {
        mouseChannel.trySend(Delta(dx, dy))
      }
    }
  }

  fun sendMouseDown(btn: String = "left") {
    send(MouseDown(btn = btn))
  }

  fun sendMouseUp(btn: String = "left") {
    send(MouseUp(btn = btn))
  }

  fun sendWheel(delta: Int) {
    send(Wheel(delta = delta))
  }

  /** 모드 전환시 HOLD 걸림 방지: 누름 중인 코드 전부 keyup */
  fun releaseAll(pressedCodes: Set<String>) {
    pressedCodes.forEach { sendKeyUp(it) }
  }
}
