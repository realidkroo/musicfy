// DataStore.kt
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

/**
 * Process-wide in-memory mirror of the preferences file.
 *
 * Why this exists: `dataStore[key]` used to be `runBlocking(Dispatchers.IO) { data.first() }`.
 * Even once DataStore's own cache is warm, that still parks the calling thread while it hops to
 * the IO dispatcher and back. There are ~180 `rememberPreference` call sites, 19 of them on a
 * single settings screen, and every one paid that round-trip on the main thread during
 * composition. That is what made opening Settings hitch.
 *
 * Separately, each `rememberPreference` used to build its own `dataStore.data.map { }` chain and
 * collect it with `collectAsState`, so a screen with 19 preferences launched 19 coroutines and
 * held 19 collectors on the DataStore pipeline.
 *
 * Both problems collapse into one shared snapshot-state mirror: a single collector keeps
 * [snapshot] current, reads are plain map lookups against memory, and composables observe their
 * own key through `derivedStateOf` so a write to one preference only invalidates the composables
 * that actually read that preference.
 *
 * The public read API ([get]) is unchanged, so every existing call site benefits without edits.
 */
object PreferencesCache {
    /**
     * Snapshot state so Compose can subscribe. Written from a background collector, which is
     * safe — snapshot writes are thread-safe and readers observe the new value once the global
     * snapshot advances.
     */
    private val snapshotState = mutableStateOf(emptyPreferences())

    /**
     * Plain mirror for non-Compose callers, so they never touch the snapshot system. Reading
     * snapshot state outside composition would register a dependency in whatever snapshot
     * happens to be current, which is not what a ViewModel or a lyrics provider wants.
     */
    @Volatile
    private var plain: Preferences = emptyPreferences()

    @Volatile
    private var warm: Boolean = false

    /** Scope for preference writes. Deliberately not tied to any composition — see [set]. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: State<Preferences> get() = snapshotState

    /**
     * Starts the single collector that keeps the mirror current. Call once from Application
     * startup. Undispatched so that when DataStore can serve the first emission from its own
     * cache, the mirror is populated before startup continues.
     */
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

    /**
     * Current preferences.
     *
     * Falls back to a single blocking read if something asks before the collector's first
     * emission. That fallback is self-limiting: it populates the mirror, so across the whole
     * process it can happen at most once instead of once per call site.
     */
    fun current(store: DataStore<Preferences>): Preferences {
        if (warm) return plain
        val prefs = runBlocking(Dispatchers.IO) { store.data.first() }
        plain = prefs
        warm = true
        snapshotState.value = prefs
        return prefs
    }

    fun <T> set(store: DataStore<Preferences>, key: Preferences.Key<T>, value: T) {
        // Intentionally not rememberCoroutineScope(): a toggle that dismisses its own dialog
        // would otherwise cancel its own write when the composable left composition.
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

/**
 * Observes a single preference.
 *
 * No coroutine and no flow per call site. `derivedStateOf` recomputes a map lookup whenever any
 * preference changes but only invalidates its readers when *this* key's value actually changes,
 * so toggling one setting no longer recomposes every other setting row on screen.
 */
@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore
    // Seeds the mirror before the first read so the first frame shows the stored value rather
    // than the default. A no-op once the process is warm.
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
