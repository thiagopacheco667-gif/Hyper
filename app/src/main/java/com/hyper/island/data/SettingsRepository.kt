package com.hyper.island.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Preferências persistentes do app. Na Etapa 4 recebe tamanho, posição, cores etc.
 * O serviço da ilha e a tela de configurações observam o mesmo fluxo.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val islandEnabled: Flow<Boolean> = store.data
        .map { it[KEY_ISLAND_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setIslandEnabled(enabled: Boolean) {
        store.edit { it[KEY_ISLAND_ENABLED] = enabled }
    }

    private companion object {
        val KEY_ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
    }
}
