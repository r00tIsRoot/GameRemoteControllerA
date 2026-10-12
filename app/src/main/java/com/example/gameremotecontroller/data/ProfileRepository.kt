package com.example.gameremotecontroller.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gameremotecontroller.model.GameProfile
import com.example.gameremotecontroller.model.Mode
import com.example.gameremotecontroller.model.defaultBossProfile
import com.example.gameremotecontroller.model.defaultHuntingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.gamepadDataStore: DataStore<Preferences> by preferencesDataStore(name = "gamepad_prefs")

interface ProfileRepository {
  val profiles: Flow<List<GameProfile>>
  val activeProfileId: Flow<String?>
  fun activeProfile(): Flow<GameProfile?>
  suspend fun saveProfiles(list: List<GameProfile>)
  suspend fun upsertProfile(profile: GameProfile)
  suspend fun deleteProfile(name: String)
  suspend fun setActiveProfile(name: String)
  fun lastMode(profileId: String): Flow<Mode>
  suspend fun setLastMode(
    profileId: String,
    mode: Mode,
  )
  suspend fun exportProfileJson(profileId: String): String
  suspend fun importProfileJson(json: String): GameProfile
}

class DataStoreProfileRepository(
  private val context: Context,
  private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : ProfileRepository {
  private val tag = "ProfileRepo"
  private val profilesKey = stringPreferencesKey("profiles_json")
  private val activeKey = stringPreferencesKey("active_profile_id")

  private fun modeKey(id: String) = stringPreferencesKey("last_mode_$id")

  override val profiles: Flow<List<GameProfile>> =
    context.gamepadDataStore.data.map { prefs ->
      val raw = prefs[profilesKey]
      if (raw.isNullOrBlank()) {
        // seed 기본 프로필
        val seed = listOf(defaultHuntingProfile(), defaultBossProfile())
        runCatching { context.gamepadDataStore.edit { it[profilesKey] = json.encodeToString(ListSerializer(GameProfile.serializer()), seed) } }
        seed
      } else {
        runCatching {
          json.decodeFromString(ListSerializer(GameProfile.serializer()), raw)
        }.getOrElse {
          Log.e(tag, "decode profiles fail: ${it.message}")
          emptyList()
        }
      }
    }

  override val activeProfileId: Flow<String?> =
    context.gamepadDataStore.data.map { it[activeKey] }

  /** profiles 플로우(초기 시드 포함) 기반 — activeProfile 단독 collect 시 null 고착 방지. */
  override fun activeProfile(): Flow<GameProfile?> =
    combine(profiles, activeProfileId) { list, active ->
      if (active == null) list.firstOrNull() else list.firstOrNull { it.name == active } ?: list.firstOrNull()
    }

  override suspend fun saveProfiles(list: List<GameProfile>) {
    context.gamepadDataStore.edit {
      it[profilesKey] = json.encodeToString(ListSerializer(GameProfile.serializer()), list)
    }
  }

  override suspend fun upsertProfile(profile: GameProfile) {
    val cur = profiles.first().toMutableList()
    val idx = cur.indexOfFirst { it.name == profile.name }
    if (idx >= 0) cur[idx] = profile else cur.add(profile)
    saveProfiles(cur)
  }

  override suspend fun deleteProfile(name: String) {
    saveProfiles(profiles.first().filterNot { it.name == name })
  }

  override suspend fun setActiveProfile(name: String) {
    context.gamepadDataStore.edit { it[activeKey] = name }
  }

  override fun lastMode(profileId: String): Flow<Mode> =
    context.gamepadDataStore.data.map { prefs ->
      runCatching { Mode.valueOf(prefs[modeKey(profileId)] ?: Mode.GAMEPAD.name) }.getOrDefault(Mode.GAMEPAD)
    }

  override suspend fun setLastMode(
    profileId: String,
    mode: Mode,
  ) {
    context.gamepadDataStore.edit { it[modeKey(profileId)] = mode.name }
  }

  override suspend fun exportProfileJson(profileId: String): String {
    val p = profiles.first().firstOrNull { it.name == profileId } ?: throw IllegalArgumentException("no profile $profileId")
    return json.encodeToString(GameProfile.serializer(), p)
  }

  override suspend fun importProfileJson(jsonStr: String): GameProfile {
    val p = json.decodeFromString(GameProfile.serializer(), jsonStr)
    upsertProfile(p)
    return p
  }
}

object ProfileRepositoryProvider {
  @Volatile
  private var instance: ProfileRepository? = null

  fun get(context: Context): ProfileRepository =
    instance ?: synchronized(this) {
      instance ?: DataStoreProfileRepository(context.applicationContext).also { instance = it }
    }
}
