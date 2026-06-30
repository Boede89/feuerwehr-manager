package de.feuerwehr.einsatzapp.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appDataStore by preferencesDataStore(name = "einsatzapp_prefs")
