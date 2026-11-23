package org.siloserver.silo.android.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Only the sideload source set links the GitHub updater. */
@Suppress("UNUSED_PARAMETER")
internal object PhoneUpdates {
    const val available = false

    fun onLaunch(context: Context) = Unit

    @Composable
    fun Dialogs(ready: Boolean, launch: Int) = Unit

    @Composable
    fun Settings(onBackClick: () -> Unit) {
        // A saved navigation stack from a sideload build may contain this route.
        LaunchedEffect(Unit) { onBackClick() }
    }
}
