// eqprofilerepositorykt
// this thing is part of eqprofile repository

package com.example.musicfy.eq.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// saved eq profile with metadata
@Serializable
data class SavedEQProfile(
    val id: String,                       // unique identifier
    val name: String,                     // display name
    val deviceModel: String,              // eg "sony wh-1000xm4"
    val bands: List<ParametricEQBand>,    // eq bands
    val preamp: Double = 0.0,             // preamp gain in db
    val isCustom: Boolean = false,        // whether this is a custom imported profile
    val isActive: Boolean = false,        // whether this profile is currently active
    val addedTimestamp: Long = System.currentTimeMillis()
)

// repository for managing eq profiles handles saving loading and activating eq
@Singleton
class EQProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "nanosonic_eq_profiles",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val _profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
    val profiles: StateFlow<List<SavedEQProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
    val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

    companion object {
        private const val KEY_PROFILES = "eq_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }

    init {
        loadProfiles()
    }

    // load all saved profiles from sharedpreferences
    private fun loadProfiles() {
        try {
            val profilesJson = prefs.getString(KEY_PROFILES, null)
            if (profilesJson != null) {
                val loadedProfiles = json.decodeFromString<List<SavedEQProfile>>(profilesJson)
                _profiles.value = loadedProfiles

                // load active profile
                val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
                _activeProfile.value = loadedProfiles.find { it.id == activeId }
            }
        } catch (e: Exception) {
            println("Error loading EQ profiles: ${e.message}")
            _profiles.value = emptyList()
            _activeProfile.value = null
        }
    }

    // save a new eq profile
    suspend fun saveProfile(profile: SavedEQProfile) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value.toMutableList()

        // check if profile with same id already exists
        val existingIndex = currentProfiles.indexOfFirst { it.id == profile.id }

        if (existingIndex >= 0) {
            // update existing profile
            currentProfiles[existingIndex] = profile
        } else {
            // add new profile
            currentProfiles.add(profile)
        }

        // save to sharedpreferences
        val profilesJson = json.encodeToString<List<SavedEQProfile>>(currentProfiles)
        prefs.edit { putString(KEY_PROFILES, profilesJson) }

        _profiles.value = currentProfiles
    }

    // delete a profile
    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles.removeAll { it.id == profileId }

        val profilesJson = json.encodeToString<List<SavedEQProfile>>(currentProfiles)
        prefs.edit { putString(KEY_PROFILES, profilesJson) }

        // if deleted profile was active clear active profile
        if (_activeProfile.value?.id == profileId) {
            _activeProfile.value = null
            prefs.edit { remove(KEY_ACTIVE_PROFILE_ID) }
        }

        _profiles.value = currentProfiles
    }

    // set a profile as active (only one profile can be active at a time) pass null to
    suspend fun setActiveProfile(profileId: String?) = withContext(Dispatchers.IO) {
        val currentProfiles = _profiles.value

        if (profileId == null) {
            // deactivate all profiles
            _activeProfile.value = null
            prefs.edit { remove(KEY_ACTIVE_PROFILE_ID) }
        } else {
            val profile = currentProfiles.find { it.id == profileId }
            _activeProfile.value = profile
            prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profileId) }
        }
    }

    // get all saved profiles
    fun getAllProfiles(): List<SavedEQProfile> {
        return _profiles.value
    }

    // get active profile
    fun getActiveProfile(): SavedEQProfile? {
        return _activeProfile.value
    }

    // import a custom eq profile from parametriceq data
    suspend fun importCustomProfile(
        name: String,
        parametricEQ: ParametricEQ
    ) = withContext(Dispatchers.IO) {
        // generate unique id for custom profile
        val id = "custom_${System.currentTimeMillis()}_${name.hashCode()}"

        val customProfile = SavedEQProfile(
            id = id,
            name = name,
            deviceModel = name,
            bands = parametricEQ.bands,  // already parametriceqband
            preamp = parametricEQ.preamp,
            isActive = false,
            isCustom = true // ensure this flag is set!
        )

        saveProfile(customProfile)
    }

    // get profiles sorted by type: autoeq first then custom profiles within each
    fun getSortedProfiles(): List<SavedEQProfile> {
        // only custom profiles are supported now
        return _profiles.value
            .filter { it.isCustom }
            .sortedByDescending { it.addedTimestamp }
    }
}
