package org.siloserver.silo.android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Fork: opens the admin console, or null when the signed-in account isn't an
 * administrator. The profile menu reads it, so its many hosts don't each
 * thread a callback.
 */
val LocalAdminConsole = staticCompositionLocalOf<(() -> Unit)?> { null }
