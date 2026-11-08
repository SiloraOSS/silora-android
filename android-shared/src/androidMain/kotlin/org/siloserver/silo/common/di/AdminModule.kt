package org.siloserver.silo.common.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.siloserver.silo.common.admin.AdminAccess
import org.siloserver.silo.common.admin.AdminConsoleViewModel
import org.siloserver.silo.common.admin.AdminDashboardViewModel
import org.siloserver.silo.common.admin.AdminLogsViewModel
import org.siloserver.silo.common.admin.AdminRequestsViewModel
import org.siloserver.silo.common.admin.AdminService
import org.siloserver.silo.common.admin.AdminTasksViewModel
import org.siloserver.silo.common.admin.AdminSpecLoader

/** Fork: the admin console, shared by the phone and TV apps. */
val adminModule = module {
    single { AdminSpecLoader(androidContext()) }
    single { AdminAccess(get(), get()) }
    single { AdminService(get(), get()) }
    viewModel { AdminConsoleViewModel(get(), get(), get()) }
    viewModel { AdminDashboardViewModel(get()) }
    viewModel { AdminRequestsViewModel(get()) }
    viewModel { AdminTasksViewModel(get()) }
    viewModel { AdminLogsViewModel(get()) }
}
