package com.fastshare.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "fastshare_identity")
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "fastshare_settings")
