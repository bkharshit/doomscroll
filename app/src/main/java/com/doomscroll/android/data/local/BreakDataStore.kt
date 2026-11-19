package com.doomscroll.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscroll.android.domain.limiter.BreakStateStore
import kotlinx.coroutines.flow.first

/**
 * Separate file (datastore/doomscroll_break.preferences_pb) so backup rules can exclude it:
 * restoring a backup must not hand out a fresh break allowance.
 */
private val Context.breakDataStore: DataStore<Preferences> by preferencesDataStore(name = "doomscroll_break")

class BreakDataStore(private val context: Context) : BreakStateStore {
    private val key = stringPreferencesKey("break_state")

    override suspend fun load(): String? = context.breakDataStore.data.first()[key]

    override suspend fun save(encoded: String) {
        context.breakDataStore.edit { it[key] = encoded }
    }
}
