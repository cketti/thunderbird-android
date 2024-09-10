package com.fsck.k9

import android.app.Application
import androidx.work.WorkManager
import app.k9mail.feature.telemetry.api.TelemetryManager
import app.k9mail.legacy.di.DI
import com.fsck.k9.backend.BackendManager
import com.fsck.k9.controller.ControllerExtension
import com.fsck.k9.crypto.EncryptionExtractor
import com.fsck.k9.notification.NotificationActionCreator
import com.fsck.k9.notification.NotificationResourceProvider
import com.fsck.k9.notification.NotificationStrategy
import com.fsck.k9.preferences.InMemoryStoragePersister
import com.fsck.k9.preferences.StoragePersister
import com.fsck.k9.storage.storageModule
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mockito.kotlin.mock

class TestApp : Application() {
    override fun onCreate() {
        Core.earlyInit()

        super.onCreate()
        DI.start(
            application = this,
            modules = coreModules + storageModule + testModule,
            allowOverride = true,
        )

        K9.init(this)
        Core.init(this)
    }
}

val testModule = module {
    single { AppConfig(emptyList()) }
    single { mock<CoreResourceProvider>() }
    single { mock<EncryptionExtractor>() }
    single<StoragePersister> { InMemoryStoragePersister() }
    single { mock<BackendManager>() }
    single { mock<NotificationResourceProvider>() }
    single { mock<NotificationActionCreator>() }
    single { mock<NotificationStrategy>() }
    single(named("controllerExtensions")) { emptyList<ControllerExtension>() }
    single { mock<WorkManager>() }
    single<TelemetryManager> {
        object : TelemetryManager {
            override fun isTelemetryFeatureIncluded(): Boolean = true
            override fun setEnabled(enable: Boolean) = Unit
        }
    }
}
