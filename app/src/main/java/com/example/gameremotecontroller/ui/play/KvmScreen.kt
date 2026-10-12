package com.example.gameremotecontroller.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 풀화면 TouchPadBox + 하단바 [키보드][좌][우][게임패드로].
 * - 1손가락 드래그 → mouse_move (감도는 ViewModel에서 적용)
 * - 짧은 탭 → 좌클릭, 두손가락 탭 → 우클릭, 두손가락 수직드래그 → 휠
 * - 롱프레스+드래그 → mouse_down 유지
 */
@Composable
fun KvmScreen(
  onMouseMove: (Int, Int) -> Unit,
  onMouseDown: (String) -> Unit,
  onMouseUp: (String) -> Unit,
  onWheel: (Int) -> Unit,
  onKeyboard: () -> Unit,
  onToGamePad: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize()) {
    var status by remember { mutableStateOf("터치패드") }
    var downAt by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .pointerInput(Unit) {
          awaitEachGesture {
            val first = awaitFirstDown()
            downAt = System.currentTimeMillis()
            var pointers = mutableSetOf(first.id)
            var lastX = first.position.x
            var lastY = first.position.y
            var totalDy = 0f
            var moved = false
            var longPressed = false
            dragging = false

            while (true) {
              val event = awaitPointerEvent()
              event.changes.forEach { pointers.add(it.id) }
              val pressed = event.changes.filter { it.pressed }
              if (pressed.isEmpty()) break

              val cur = pressed.first()
              val dx = (cur.position.x - lastX).toInt()
              val dy = (cur.position.y - lastY).toInt()
              lastX = cur.position.x
              lastY = cur.position.y
              if (abs(dx) + abs(dy) > 2) moved = true
              val held = System.currentTimeMillis() - downAt

              if (pointers.size >= 2) {
                // 두손가락 수직드래그 → 휠
                totalDy += dy.toFloat()
                if (abs(totalDy) > 24) {
                  onWheel(if (totalDy < 0) 120 else -120)
                  totalDy = 0f
                }
              } else if (held > 400 && moved && !longPressed) {
                longPressed = true
                onMouseDown("left")
                dragging = true
                status = "드래그 중"
              } else if (longPressed || dragging) {
                onMouseMove(dx, dy)
              } else if (moved) {
                onMouseMove(dx, dy)
              }
              event.changes.forEach(PointerInputChange::consume)
            }

            val heldTotal = System.currentTimeMillis() - downAt
            if (longPressed || dragging) {
              onMouseUp("left")
              status = "터치패드"
            } else if (!moved && heldTotal < 250) {
              if (pointers.size >= 2) {
                // 두손가락 탭 → 우클릭
                onMouseDown("right")
                onMouseUp("right")
                status = "우클릭"
              } else {
                onMouseDown("left")
                onMouseUp("left")
                status = "좌클릭"
              }
            }
            dragging = false
          }
        },
    ) {
      Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(Modifier.fillMaxWidth()) {
      Button(onClick = onKeyboard, modifier = Modifier.weight(1f)) { Text("키보드") }
      Button(
        onClick = {
          onMouseDown("left")
          onMouseUp("left")
        },
        modifier = Modifier.weight(1f),
      ) { Text("좌") }
      Button(
        onClick = {
          onMouseDown("right")
          onMouseUp("right")
        },
        modifier = Modifier.weight(1f),
      ) { Text("우") }
      Button(onClick = onToGamePad, modifier = Modifier.weight(1.4f)) { Text("게임패드로") }
    }
  }
}
