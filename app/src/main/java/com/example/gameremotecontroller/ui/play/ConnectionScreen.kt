package com.example.gameremotecontroller.ui.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gameremotecontroller.net.ConnectionState

@Composable
fun ConnectionScreen(
  host: String,
  pin: String,
  connection: ConnectionState,
  onHostChange: (String) -> Unit,
  onPinChange: (String) -> Unit,
  onConnect: (String) -> Unit,
  onDisconnect: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.padding(16.dp)) {
    Text("PC 연결", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = host,
      onValueChange = onHostChange,
      label = { Text("PC Tailscale 이름 또는 LAN IP") },
      placeholder = { Text("예: mypc 또는 192.168.0.10") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    val preview =
      if (host.isBlank()) "ws://<pc>:45321"
      else if (host.contains("://")) host else if (host.contains(":")) "ws://$host" else "ws://$host:45321"
    Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("포트 45321 고정 · P2까지 ws (이후 wss 검토)", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = pin,
      onValueChange = onPinChange,
      label = { Text("PIN 6자리 (PC 화면 표시)") },
      placeholder = { Text("예: 123456") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    when (connection) {
      is ConnectionState.Connected -> {
        Text("연결됨" + (connection.rttMs?.let { " · rtt ${it}ms" } ?: ""), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("끊기") }
      }
      is ConnectionState.Connecting -> {
        Text("연결 중…")
      }
      is ConnectionState.Error -> {
        Text("오류: ${connection.message}", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onConnect(host) }, modifier = Modifier.fillMaxWidth()) { Text("다시 연결") }
      }
      is ConnectionState.Disconnected -> {
        if (connection.reason != null) Text("끊김: ${connection.reason}")
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onConnect(host) }, modifier = Modifier.fillMaxWidth()) { Text("연결") }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text("최초 1회: PC 화면 PIN 6자리 → 위에 입력 후 연결", style = MaterialTheme.typography.bodySmall)
  }
}
