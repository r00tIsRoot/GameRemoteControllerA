package com.example.gameremotecontroller.net

sealed interface ConnectionState {
  data class Disconnected(
    val reason: String? = null,
  ) : ConnectionState

  data object Connecting : ConnectionState

  data class Connected(
    val rttMs: Long? = null,
  ) : ConnectionState

  data class Error(
    val message: String,
  ) : ConnectionState
}
