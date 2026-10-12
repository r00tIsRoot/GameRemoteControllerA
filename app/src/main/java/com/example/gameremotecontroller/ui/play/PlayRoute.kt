package com.example.gameremotecontroller.ui.play

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gameremotecontroller.model.Mode
import com.example.gameremotecontroller.net.ConnectionState
import com.example.gameremotecontroller.play.PlayViewModel

/**
 * PLAY 루트: 연결바 + 모드별 화면 + FAB 원터치 전환.
 * WS 연결 유지한 채 모드 전환 (<100ms, 재접속 없음).
 */
@Composable
fun PlayRoute(
  viewModel: PlayViewModel,
  onOpenEdit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mode by viewModel.currentMode.collectAsStateWithLifecycle()
  val connection by viewModel.connection.collectAsStateWithLifecycle()
  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val pressed by viewModel.pressedCodes.collectAsStateWithLifecycle()
  val host by viewModel.hostInput.collectAsStateWithLifecycle()
  val pin by viewModel.pinInput.collectAsStateWithLifecycle()
  val ctx = LocalContext.current

  LaunchedEffect(Unit) {
    viewModel.toastMessages.collect { msg ->
      Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
  }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      // 상단 연결 상태바
      val statusText =
        when (val c = connection) {
          is ConnectionState.Connected -> "● 연결됨 ${profile?.name ?: ""}" + (c.rttMs?.let { " ${it}ms" } ?: "")
          is ConnectionState.Connecting -> "○ 연결 중…"
          is ConnectionState.Error -> "○ 오류: ${c.message}"
          is ConnectionState.Disconnected -> "○ 끊김 ${c.reason ?: ""}"
        }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          statusText,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f).padding(12.dp),
        )
        TextButton(onClick = onOpenEdit) { Text("편집") }
      }

      if (connection !is ConnectionState.Connected) {
        ConnectionScreen(
          host = host,
          pin = pin,
          connection = connection,
          onHostChange = viewModel::onHostChange,
          onPinChange = viewModel::onPinChange,
          onConnect = viewModel::connect,
          onDisconnect = viewModel::disconnect,
        )
      } else {
        when (mode) {
          Mode.GAMEPAD ->
            GamePadScreen(
              buttons = profile?.buttons ?: emptyList(),
              pressedCodes = pressed,
              bindings = profile?.bindings ?: emptyMap(),
              onDown = viewModel::onButtonDown,
              onUp = viewModel::onButtonUp,
              onJoyDown = viewModel::onJoyDown,
              onJoyUp = viewModel::onJoyUp,
              modifier = Modifier.weight(1f),
            )
          Mode.KVM ->
            KvmScreen(
              onMouseMove = viewModel::onKvmMouseMove,
              onMouseDown = { viewModel.client.sendMouseDown(it) },
              onMouseUp = { viewModel.client.sendMouseUp(it) },
              onWheel = { viewModel.client.sendWheel(it) },
              onKeyboard = { /* TODO: IME KeyEvent -> bindings 변환, 프로필 옵션 */ },
              onToGamePad = viewModel::switchMode,
              modifier = Modifier.weight(1f),
            )
        }
      }
    }

    // FAB 항상 표시, 단발 탭 전환
    FloatingActionButton(
      onClick = viewModel::switchMode,
      modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) {
      Text(if (mode == Mode.GAMEPAD) "KVM" else "패드")
    }
  }
}
