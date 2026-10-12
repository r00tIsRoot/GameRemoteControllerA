package com.example.gameremotecontroller.play

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gameremotecontroller.data.ProfileRepository
import com.example.gameremotecontroller.data.ProfileRepositoryProvider
import com.example.gameremotecontroller.model.ButtonType
import com.example.gameremotecontroller.model.GameProfile
import com.example.gameremotecontroller.model.Mode
import com.example.gameremotecontroller.model.PadButton
import com.example.gameremotecontroller.net.ConnectionState
import com.example.gameremotecontroller.net.GameRemoteClient
import com.example.gameremotecontroller.net.InputMapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 03-android-client.md: currentMode는 소켓 유지한 채 UI만 전환.
 * 전환시 진행중 HOLD 키 전부 keyup 후 전환 (걸림 방지).
 */
class PlayViewModel(
  val client: GameRemoteClient,
  private val repo: ProfileRepository,
) : ViewModel() {
  val connection: StateFlow<ConnectionState> = client.state

  val profile: StateFlow<GameProfile?> =
    kotlinx.coroutines.flow.flow {
      repo.activeProfile().collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  private val _currentMode = MutableStateFlow(Mode.GAMEPAD)
  val currentMode: StateFlow<Mode> = _currentMode.asStateFlow()

  private val _pressedCodes = MutableStateFlow<Set<String>>(emptySet())
  val pressedCodes: StateFlow<Set<String>> = _pressedCodes.asStateFlow()

  // 코드 -> 카운트 (멀티터치 중복 DOWN 대응)
  private val pressCount = mutableMapOf<String, Int>()

  // 코드 -> 홀드 시작 시각. 2s마다 keydown 재전송(킵얼라이브)해
  // 서버 5s 자동릴리즈로 손가락 누르는 중 풀리는 것을 방지.
  private val pressStartedAt = mutableMapOf<String, Long>()

  private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val toastMessages: SharedFlow<String> = _toastMessages.asSharedFlow()

  private val _hostInput = MutableStateFlow("")
  val hostInput: StateFlow<String> = _hostInput.asStateFlow()

  private val _pinInput = MutableStateFlow("")
  val pinInput: StateFlow<String> = _pinInput.asStateFlow()

  lateinit var mapper: InputMapper

  init {
    viewModelScope.launch {
      repo.activeProfile().collect { p ->
        if (p != null) {
          mapper = InputMapper(client) { p.bindings }
          // 프로필별 마지막 모드 복원
          repo.lastMode(p.name).collect { m -> _currentMode.value = m }
        }
      }
    }
    viewModelScope.launch {
      client.incoming.collect { msg ->
        if (msg is com.example.gameremotecontroller.protocol.RemoteError) {
          _toastMessages.emit(msg.reason)
        }
      }
    }
    // 홀드 킵얼라이브: 1s마다 검사, 2s 넘게 누름 중이면 keydown 재전송.
    // 서버 5s 자동릴리즈보다 짧아 손가락 유지 중 풀림 방지. 50ms 연타가 아님.
    viewModelScope.launch {
      while (true) {
        kotlinx.coroutines.delay(1000)
        val now = System.currentTimeMillis()
        pressStartedAt.entries.toList().forEach { (code, ts) ->
          if (now - ts >= 2000 && _pressedCodes.value.contains(code)) {
            client.sendKeyDown(code)
            pressStartedAt[code] = now
          }
        }
      }
    }
  }

  fun onHostChange(v: String) {
    _hostInput.value = v
  }

  fun onPinChange(v: String) {
    _pinInput.value = v.filter { it.isDigit() }.take(6)
  }

  /** EDIT 저장: 프로필 upsert (DataStore) — profile 플로우가 자동 갱신됨. */
  fun saveProfile(p: GameProfile) {
    viewModelScope.launch {
      repo.upsertProfile(p)
      _toastMessages.emit("저장됨: ${p.name}")
    }
  }

  fun connect(
    host: String,
    pin: String = _pinInput.value,
  ) {
    val clean = host.trim()
    if (clean.isEmpty()) {
      viewModelScope.launch { _toastMessages.emit("PC 주소를 입력하세요") }
      return
    }
    val url =
      if (clean.contains("://")) clean
      else if (clean.contains(":")) "ws://$clean"
      else "ws://$clean:45321"
    client.connect(url, profile.value?.name ?: "", token = pin)
  }

  fun disconnect() = client.disconnect()

  /** FAB 원터치 전환: WS 유지, UI만 전환 (<100ms 목표). */
  fun switchMode() {
    // 걸림 방지: 진행중 HOLD 전부 keyup
    val pressed = _pressedCodes.value
    if (pressed.isNotEmpty()) {
      client.releaseAll(pressed)
      pressCount.clear()
      pressStartedAt.clear()
      _pressedCodes.value = emptySet()
    }
    val next = if (_currentMode.value == Mode.GAMEPAD) Mode.KVM else Mode.GAMEPAD
    _currentMode.value = next
    viewModelScope.launch {
      profile.value?.let { repo.setLastMode(it.name, next) }
      _toastMessages.emit(if (next == Mode.KVM) "KVM 모드" else "게임패드 모드")
    }
  }

  /** 홀드 시작 (HOLD 버튼 DOWN / 조이스틱 방향 진입 공통). */
  private fun holdDown(code: String) {
    val n = (pressCount[code] ?: 0) + 1
    pressCount[code] = n
    if (n == 1) {
      client.sendKeyDown(code)
      pressStartedAt[code] = System.currentTimeMillis()
      _pressedCodes.value = _pressedCodes.value + code
    }
  }

  /** 홀드 해제 (HOLD 버튼 UP / 조이스틱 방향 이탈 공통). */
  private fun holdUp(code: String) {
    val n = (pressCount[code] ?: 1) - 1
    if (n <= 0) {
      pressCount.remove(code)
      pressStartedAt.remove(code)
      client.sendKeyUp(code)
      _pressedCodes.value = _pressedCodes.value - code
    } else {
      pressCount[code] = n
    }
  }

  fun onButtonDown(btn: PadButton) {
    if (!::mapper.isInitialized) return
    when (btn.type) {
      ButtonType.TAP -> {
        if (btn.action.isNotBlank()) mapper.actionTap(btn.action)
      }
      ButtonType.HOLD -> {
        val code = profile.value?.bindings?.get(btn.action) ?: return
        holdDown(code)
      }
      ButtonType.TOGGLE -> {
        val code = profile.value?.bindings?.get(btn.action) ?: return
        if (_pressedCodes.value.contains(code)) {
          client.sendKeyUp(code)
          pressCount.remove(code)
          pressStartedAt.remove(code)
          _pressedCodes.value = _pressedCodes.value - code
        } else {
          holdDown(code)
        }
      }
      ButtonType.MOUSE_AREA -> Unit
      ButtonType.JOYSTICK -> Unit // 조이스틱은 onJoyDown/onJoyUp 경로 사용
    }
  }

  fun onButtonUp(btn: PadButton) {
    if (!::mapper.isInitialized) return
    if (btn.type != ButtonType.HOLD) return
    val code = profile.value?.bindings?.get(btn.action) ?: return
    holdUp(code)
  }

  /** 조이스틱 방향 진입 (논리액션: MOVE_LEFT 등). HOLD와 동일 홀드 기계. */
  fun onJoyDown(logicalAction: String) {
    val code = profile.value?.bindings?.get(logicalAction) ?: return
    holdDown(code)
  }

  /** 조이스틱 방향 이탈. */
  fun onJoyUp(logicalAction: String) {
    val code = profile.value?.bindings?.get(logicalAction) ?: return
    holdUp(code)
  }

  fun onKvmMouseMove(
    dx: Int,
    dy: Int,
  ) {
    val s = profile.value?.mouseSensitivity ?: 1.0f
    client.sendMouseMove((dx * s).toInt(), (dy * s).toInt())
  }

  override fun onCleared() {
    client.disconnect()
  }
}

class PlayViewModelFactory(
  private val context: Context,
  private val client: GameRemoteClient = GameRemoteClient(),
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    val repo = ProfileRepositoryProvider.get(context)
    return PlayViewModel(client, repo) as T
  }
}
