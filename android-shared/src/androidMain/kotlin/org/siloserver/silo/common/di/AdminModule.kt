package org.siloserver.silo.common.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.siloserver.silo.common.admin.AdminAccess
import org.siloserver.silo.common.admin.AdminConsoleViewModel
import org.siloserver.silo.common.admin.AdminSpecLoader

/** Fork: the admin console, shared by the phone and TV apps. */
val adminModule = module {
    single { AdminSpecLoader(androidContext()) }
    single { AdminAccess(get(), get()) }
    viewModel { AdminConsoleViewModel(get(), get(), get()) }
}
