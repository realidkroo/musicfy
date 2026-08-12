// DataStore.kt

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

object PreferencesCache {

    private val snapshotState = mutableStateOf(emptyPreferences())

    @Volatile
    private var plain: Preferences = emptyPreferences()

    @Volatile
    private var warm: Boolean = false

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val snapshot: State<Preferences> get() = snapshotState

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

    fun current(store: DataStore<Preferences>): Preferences {
        if (warm) return plain
        val prefs = runBlocking(Dispatchers.IO) { store.data.first() }
        plain = prefs
        warm = true
        snapshotState.value = prefs
        return prefs
    }

    fun <T> set(store: DataStore<Preferences>, key: Preferences.Key<T>, value: T) {

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

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val store = LocalContext.current.applicationContext.dataStore

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
