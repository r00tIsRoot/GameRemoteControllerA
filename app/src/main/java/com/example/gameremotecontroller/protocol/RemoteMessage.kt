package com.example.gameremotecontroller.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 02-protocol.md 규격.
 * phone -> pc: keydown/keyup/key/mouse_move/mouse_down/mouse_up/wheel/ping
 * pc -> phone: ack/hello/hello_ok/error
 */
@Serializable
sealed interface RemoteMessage {
  val t: String
}

@Serializable
data class KeyDown(
  val code: String,
  val ts: Long,
  override val t: String = "keydown",
) : RemoteMessage

@Serializable
data class KeyUp(
  val code: String,
  val ts: Long,
  override val t: String = "keyup",
) : RemoteMessage

@Serializable
data class KeyTap(
  val code: String,
  val action: String = "tap",
  override val t: String = "key",
) : RemoteMessage

@Serializable
data class MouseMove(
  val dx: Int,
  val dy: Int,
  override val t: String = "mouse_move",
) : RemoteMessage

@Serializable
data class MouseDown(
  val btn: String = "left",
  override val t: String = "mouse_down",
) : RemoteMessage

@Serializable
data class MouseUp(
  val btn: String = "left",
  override val t: String = "mouse_up",
) : RemoteMessage

@Serializable
data class Wheel(
  val delta: Int,
  override val t: String = "wheel",
) : RemoteMessage

@Serializable
data class Ping(
  val id: Long,
  override val t: String = "ping",
) : RemoteMessage

@Serializable
data class Ack(
  val id: Long,
  val rtt: Long = 0,
  override val t: String = "ack",
) : RemoteMessage

@Serializable
data class Hello(
  val proto: Int = 1,
  val profile: String = "",
  val token: String = "",
  override val t: String = "hello",
) : RemoteMessage

@Serializable
data class HelloOk(
  override val t: String = "hello_ok",
) : RemoteMessage

@Serializable
data class RemoteError(
  val reason: String,
  override val t: String = "error",
) : RemoteMessage

object RemoteJson {
  val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  fun encode(msg: RemoteMessage): String =
    when (msg) {
      is KeyDown -> json.encodeToString(KeyDown.serializer(), msg)
      is KeyUp -> json.encodeToString(KeyUp.serializer(), msg)
      is KeyTap -> json.encodeToString(KeyTap.serializer(), msg)
      is MouseMove -> json.encodeToString(MouseMove.serializer(), msg)
      is MouseDown -> json.encodeToString(MouseDown.serializer(), msg)
      is MouseUp -> json.encodeToString(MouseUp.serializer(), msg)
      is Wheel -> json.encodeToString(Wheel.serializer(), msg)
      is Ping -> json.encodeToString(Ping.serializer(), msg)
      is Ack -> json.encodeToString(Ack.serializer(), msg)
      is Hello -> json.encodeToString(Hello.serializer(), msg)
      is HelloOk -> json.encodeToString(HelloOk.serializer(), msg)
      is RemoteError -> json.encodeToString(RemoteError.serializer(), msg)
    }

  /** pc->phone 수신용 최소 파싱 (t 분기). phone->pc echo가 와도 무시하지 않고 파싱. */
  fun decodeLenient(raw: String): RemoteMessage? =
    runCatching {
      val tree = json.parseToJsonElement(raw)
      val t = tree.jsonObject["t"]?.jsonPrimitive?.content ?: return@runCatching null
      when (t) {
        "ack" -> json.decodeFromJsonElement(Ack.serializer(), tree)
        "hello_ok" -> HelloOk()
        "error" -> json.decodeFromJsonElement(RemoteError.serializer(), tree)
        "hello" -> json.decodeFromJsonElement(Hello.serializer(), tree)
        "keydown" -> json.decodeFromJsonElement(KeyDown.serializer(), tree)
        "keyup" -> json.decodeFromJsonElement(KeyUp.serializer(), tree)
        "key" -> json.decodeFromJsonElement(KeyTap.serializer(), tree)
        "mouse_move" -> json.decodeFromJsonElement(MouseMove.serializer(), tree)
        "mouse_down" -> json.decodeFromJsonElement(MouseDown.serializer(), tree)
        "mouse_up" -> json.decodeFromJsonElement(MouseUp.serializer(), tree)
        "wheel" -> json.decodeFromJsonElement(Wheel.serializer(), tree)
        "ping" -> json.decodeFromJsonElement(Ping.serializer(), tree)
        else -> null
      }
    }.getOrNull()
}
