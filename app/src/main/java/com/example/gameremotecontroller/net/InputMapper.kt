package com.example.gameremotecontroller.net

import android.util.Log

/**
 * 논리액션 -> 스캔코드 변환 + 전송 헬퍼.
 * GAMEPAD/KVM 모두 동일 bindings 참조 (따로 매핑 금지).
 * HOLD는 DOWN 1회 / UP 1회만 — 중간 auto-repeat 생성 금지.
 */
class InputMapper(
  private val client: GameRemoteClient,
  private val getBindings: () -> Map<String, String>,
) {
  private val tag = "InputMapper"

  private fun codeFor(logicalAction: String): String? {
    val code = getBindings()[logicalAction]
    if (code == null) Log.w(tag, "no binding for $logicalAction")
    return code
  }

  fun actionDown(logicalAction: String) {
    codeFor(logicalAction)?.let { client.sendKeyDown(it) }
  }

  fun actionUp(logicalAction: String) {
    codeFor(logicalAction)?.let { client.sendKeyUp(it) }
  }

  fun actionTap(logicalAction: String) {
    codeFor(logicalAction)?.let { client.sendTap(it) }
  }

  fun mouseMove(
    dx: Int,
    dy: Int,
  ) = client.sendMouseMove(dx, dy)

  fun mouseDown(btn: String = "left") = client.sendMouseDown(btn)

  fun mouseUp(btn: String = "left") = client.sendMouseUp(btn)

  fun wheel(delta: Int) = client.sendWheel(delta)
}
