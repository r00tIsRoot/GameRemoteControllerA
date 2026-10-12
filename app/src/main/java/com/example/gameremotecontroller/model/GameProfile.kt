package com.example.gameremotecontroller.model

import kotlinx.serialization.Serializable

enum class Mode {
  GAMEPAD,
  KVM,
}

enum class ButtonType {
  TAP,
  HOLD,
  TOGGLE,
  MOUSE_AREA,
  /** 8방향 가상 스틱. bindings의 MOVE_* 4종을 공유 사용. action 필드 무시. */
  JOYSTICK,
}

@Serializable
data class PadButton(
  val id: String,
  val label: String,
  /** 논리 액션명. 예: MOVE_LEFT, JUMP, ATTACK. MOUSE_AREA는 action="" 허용 */
  val action: String,
  val type: ButtonType,
  /** 0..1 fraction (가로 기준) */
  val x: Float,
  /** 0..1 fraction (세로 기준) */
  val y: Float,
  /** 0..1 fraction (짧은 변 기준) */
  val size: Float,
)

@Serializable
data class GameProfile(
  val name: String,
  /** 논리액션 -> PC 스캔코드명. GAMEPAD/KVM이 동일 bindings를 공유한다 (따로 매핑 금지). */
  val bindings: Map<String, String>,
  val buttons: List<PadButton>,
  val mouseSensitivity: Float = 1.0f,
  val keyHoldRepeatMs: Long = 50,
)

// ---- 논리 액션명 ----
object Actions {
  const val MOVE_LEFT = "MOVE_LEFT"
  const val MOVE_RIGHT = "MOVE_RIGHT"
  const val MOVE_UP = "MOVE_UP"
  const val MOVE_DOWN = "MOVE_DOWN"
  const val JUMP = "JUMP"
  const val ATTACK = "ATTACK"
  const val PICKUP = "PICKUP"
  const val POTION_HP = "POTION_HP"
  const val POTION_MP = "POTION_MP"
}

/** 메이플스토리 프리셋 바인딩 (논리액션 -> 스캔코드명). */
fun defaultMapleBindings(): Map<String, String> =
  mapOf(
    Actions.MOVE_LEFT to "Left",
    Actions.MOVE_RIGHT to "Right",
    Actions.MOVE_UP to "Up",
    Actions.MOVE_DOWN to "Down",
    Actions.JUMP to "Alt",
    Actions.ATTACK to "Ctrl",
    Actions.PICKUP to "Z",
    Actions.POTION_HP to "Q",
    Actions.POTION_MP to "W",
    "SKILL_1" to "Insert",
    "SKILL_2" to "Home",
    "SKILL_3" to "PageUp",
    "SKILL_4" to "Delete",
    "SKILL_5" to "End",
    "SKILL_6" to "PageDown",
    "F1" to "F1",
    "F2" to "F2",
    "F3" to "F3",
    "F4" to "F4",
    "F5" to "F5",
    "F6" to "F6",
  )

private fun huntingButtons(): List<PadButton> =
  listOf(
    // 방향 이동은 조이스틱(8방향, MOVE_* 공유). 구 D패드 4버튼 대체.
    PadButton("stick", "스틱", "", ButtonType.JOYSTICK, 0.03f, 0.50f, 0.40f),
    PadButton("jump", "점프", Actions.JUMP, ButtonType.TAP, 0.68f, 0.66f, 0.17f),
    PadButton("attack", "공격", Actions.ATTACK, ButtonType.HOLD, 0.82f, 0.50f, 0.17f),
    PadButton("pickup", "줍기", Actions.PICKUP, ButtonType.TAP, 0.56f, 0.78f, 0.14f),
    PadButton("hp", "HP", Actions.POTION_HP, ButtonType.TAP, 0.56f, 0.44f, 0.13f),
    PadButton("mp", "MP", Actions.POTION_MP, ButtonType.TAP, 0.68f, 0.38f, 0.13f),
  )

private fun bossButtons(): List<PadButton> =
  huntingButtons() +
    listOf(
      PadButton("s1", "Ins", "SKILL_1", ButtonType.TAP, 0.42f, 0.30f, 0.12f),
      PadButton("s2", "Home", "SKILL_2", ButtonType.TAP, 0.55f, 0.30f, 0.12f),
      PadButton("s3", "PgUp", "SKILL_3", ButtonType.TAP, 0.68f, 0.30f, 0.12f),
      PadButton("s4", "Del", "SKILL_4", ButtonType.TAP, 0.42f, 0.16f, 0.12f),
      PadButton("s5", "End", "SKILL_5", ButtonType.TAP, 0.55f, 0.16f, 0.12f),
      PadButton("s6", "PgDn", "SKILL_6", ButtonType.TAP, 0.68f, 0.16f, 0.12f),
    )

fun defaultHuntingProfile(): GameProfile =
  GameProfile(
    name = "사냥용",
    bindings = defaultMapleBindings(),
    buttons = huntingButtons(),
    mouseSensitivity = 1.0f,
  )

fun defaultBossProfile(): GameProfile =
  GameProfile(
    name = "보스용",
    bindings = defaultMapleBindings(),
    buttons = bossButtons(),
    mouseSensitivity = 0.8f,
  )
