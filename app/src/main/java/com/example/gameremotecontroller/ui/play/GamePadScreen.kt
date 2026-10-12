package com.example.gameremotecontroller.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.gameremotecontroller.model.Actions
import com.example.gameremotecontroller.model.ButtonType
import com.example.gameremotecontroller.model.PadButton
import kotlin.math.abs

private const val JOY_ADD = 0.40f
private const val JOY_KEEP = 0.25f

/**
 * 커스텀 버튼 그리드 + 조이스틱. 멀티터치 필수 — 각 컨트롤이 독립 gesture.
 * 버튼: ACTION_DOWN 즉시 keydown, UP 시 keyup. 10ms 햅틱.
 * 조이스틱: 8방향, 진입 0.40 / 유지 0.25 히스테리시스, 방향별 HOLD 홀드.
 */
@Composable
fun GamePadScreen(
  buttons: List<PadButton>,
  pressedCodes: Set<String>,
  bindings: Map<String, String>,
  onDown: (PadButton) -> Unit,
  onUp: (PadButton) -> Unit,
  onJoyDown: (String) -> Unit,
  onJoyUp: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  val toggling = remember { mutableStateMapOf<String, Boolean>() }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val shortSide = minOf(w, h)

    buttons.forEach { btn ->
      val dia = (btn.size * shortSide).coerceAtLeast(with(density) { 48.dp.toPx() })
      val ox = btn.x * w
      val oy = btn.y * h

      if (btn.type == ButtonType.JOYSTICK) {
        JoystickPad(
          btn = btn,
          diaPx = dia,
          onJoyDown = onJoyDown,
          onJoyUp = onJoyUp,
          modifier =
            Modifier.offset(
              x = with(density) { ox.toDp() },
              y = with(density) { oy.toDp() },
            ),
        )
      } else {
        val code = bindings[btn.action]
        val isPressed = code != null && pressedCodes.contains(code)
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier
              .offset(
                x = with(density) { ox.toDp() },
                y = with(density) { oy.toDp() },
              )
              .size(with(density) { dia.toDp() })
              .clip(CircleShape)
              .background(
                when {
                  isPressed -> MaterialTheme.colorScheme.primary
                  btn.type == ButtonType.TOGGLE && toggling[btn.id] == true ->
                    MaterialTheme.colorScheme.tertiary
                  else -> MaterialTheme.colorScheme.surfaceVariant
                },
              )
              .pointerInput(btn.id) {
                awaitEachGesture {
                  awaitFirstDown()
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  onDown(btn)
                  if (btn.type == ButtonType.TOGGLE) {
                    toggling[btn.id] = !(toggling[btn.id] ?: false)
                  }
                  waitForUpOrCancellation()
                  onUp(btn)
                }
              },
        ) {
          Text(btn.label, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

/**
 * 8방향 가상 스틱. 중심 기준 벡터로 방향 결정, 대각선은 2키 동시 홀드.
 * bindings MOVE_* 공유. 손을 떼면 전부 keyup.
 */
@Composable
private fun JoystickPad(
  btn: PadButton,
  diaPx: Float,
  onJoyDown: (String) -> Unit,
  onJoyUp: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val haptic = LocalHapticFeedback.current
  var knob by remember(btn.id) { mutableStateOf(Offset.Zero) }
  var radiusPx by remember { mutableFloatStateOf(0f) }

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .size(with(density) { diaPx.toDp() })
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .onSizeChanged { radiusPx = minOf(it.width, it.height) / 2f }
        .pointerInput(btn.id) {
          awaitEachGesture {
            val down = awaitFirstDown()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var active = emptySet<String>()
            try {
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.find { it.id == down.id } ?: continue
                if (!change.pressed) break
                change.consume()
                val r = radiusPx
                if (r <= 0f) continue
                val nx = ((change.position.x - r) / r).coerceIn(-1f, 1f)
                val ny = ((change.position.y - r) / r).coerceIn(-1f, 1f)
                val next =
                  buildSet {
                    if (nx < -JOY_ADD || (active.contains(Actions.MOVE_LEFT) && nx < -JOY_KEEP)) {
                      add(Actions.MOVE_LEFT)
                    }
                    if (nx > JOY_ADD || (active.contains(Actions.MOVE_RIGHT) && nx > JOY_KEEP)) {
                      add(Actions.MOVE_RIGHT)
                    }
                    if (ny < -JOY_ADD || (active.contains(Actions.MOVE_UP) && ny < -JOY_KEEP)) {
                      add(Actions.MOVE_UP)
                    }
                    if (ny > JOY_ADD || (active.contains(Actions.MOVE_DOWN) && ny > JOY_KEEP)) {
                      add(Actions.MOVE_DOWN)
                    }
                  }
                if (next != active) {
                  haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                  (next - active).forEach { onJoyDown(it) }
                  (active - next).forEach { onJoyUp(it) }
                  active = next
                }
                val raw = Offset(nx, ny)
                val len = kotlin.math.sqrt(raw.x * raw.x + raw.y * raw.y)
                val norm = if (len > 1f) raw / len else raw
                knob = norm * (r * 0.55f)
              }
            } finally {
              (active).forEach { onJoyUp(it) }
              knob = Offset.Zero
            }
          }
        },
  ) {
    // 노브
    Box(
      modifier =
        Modifier
          .offset(
            x = with(density) { knob.x.toDp() },
            y = with(density) { knob.y.toDp() },
          )
          .size(with(density) { (diaPx * 0.45f).toDp() })
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
    )
    if (abs(knob.x) < 1f && abs(knob.y) < 1f) {
      Text(btn.label, style = MaterialTheme.typography.labelSmall)
    }
  }
}
