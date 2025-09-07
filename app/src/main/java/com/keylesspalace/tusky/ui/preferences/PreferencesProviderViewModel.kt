package com.keylesspalace.tusky.ui.preferences

/* Copyright 2025 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.settings.AppTheme
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.settings.PrefKeys.APP_THEME
import com.keylesspalace.tusky.util.getNonNullString
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PreferencesProviderViewModel @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    accountManager: AccountManager,
) : ViewModel() {

    val activeAccount = accountManager.activeAccount(viewModelScope)

    val tuskyPreferences: StateFlow<TuskyPreferences> = callbackFlow {

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            trySend(sharedPreferences.toTuskyPreferences())
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, sharedPreferences.toTuskyPreferences())

    private fun SharedPreferences.toTuskyPreferences(): TuskyPreferences {
        val tuskyTheme = this.getNonNullString(APP_THEME, AppTheme.DEFAULT.value)

        return TuskyPreferences(
            theme = AppTheme.of(tuskyTheme),
            useBlurhash = this.getBoolean(PrefKeys.USE_BLURHASH, true)
        )
    }
}
