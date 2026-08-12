// datastorekt
// this thing is for data store

package com.example.musicfy.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.musicfy.extensions.toEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// process wide in memory mirror of the preferences file why this exists
object PreferencesCache {
    // snapshot state so compose can subscribe written from a background collector
    private val snapshotState = mutableStateOf(emptyPreferences())

    // plain mirror for non compose callers so they never touch the snapshot system
    @Volatile
    private var plain: Preferences = emptyPreferences()

    @Volatile
    private var warm: Boolean = false

    // scope for preference writes deliberately not tied to any composition see set
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: State<Preferences> get() = snapshotState

    // starts the single collector that keeps the mirror current call once from
    fun start(context: Context, scope: CoroutineScope) {
        val store = context.applicationContext.dataStore
        scope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            store.data.collect { prefs ->
                plain = prefs
                warm = true
                snapshotState.value = prefs
            }
        }
    }

    // current preferences falls back to a single blocking read if something asks
    fun current(store: DataStore<Preferences>): Preferences {
        if (warm) return plain
        val prefs = runBlocking(Dispatchers.IO) { store.data.first() }
        plain = prefs
        warm = true
        snapshotState.value = prefs
        return prefs
    }

    fun <T> set(store: DataStore<Preferences>, key: Preferences.Key<T>, value: T) {
        // intentionally not remembercoroutinescope a toggle that dismisses its
        // would otherwise cancel its own write when the composable left composition
        writeScope.launch {
            store.edit { it[key] = value }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    PreferencesCache.current(this)[key]

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = PreferencesCache.current(this)[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

// observes a single preference no coroutine and no flow per call site
@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore
    // seeds the mirror before the first read so the first frame shows the stored
    // than the default a no op once the process is warm
    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key] ?: defaultValue }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore
    PreferencesCache.current(store)

    val valueState = remember(key, defaultValue) {
        derivedStateOf { PreferencesCache.snapshot.value[key].toEnum(defaultValue) }
    }

    return remember(key, store) {
        object : MutableState<T> {
            override var value: T
                get() = valueState.value
                set(value) = PreferencesCache.set(store, key, value.name)

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
