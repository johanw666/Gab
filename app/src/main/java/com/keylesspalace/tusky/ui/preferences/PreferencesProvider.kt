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

package com.keylesspalace.tusky.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

val LocalPreferences = compositionLocalOf { TuskyPreferences() }

@Composable
fun PreferencesProvider(
    viewModel: PreferencesProviderViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val preferences by viewModel.tuskyPreferences.collectAsState()

    CompositionLocalProvider(LocalPreferences provides preferences, content)
}
