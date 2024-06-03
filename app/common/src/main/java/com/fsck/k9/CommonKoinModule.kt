package com.fsck.k9

import app.k9mail.core.featureflag.FeatureFlagFactory
import app.k9mail.core.featureflag.FeatureFlagProvider
import app.k9mail.core.featureflag.InMemoryFeatureFlagProvider
import app.k9mail.ui.widget.list.messageListWidgetModule
import app.k9mail.ui.widget.unread.UnreadWidgetUpdateListener
import app.k9mail.ui.widget.unread.unreadWidgetModule
import com.fsck.k9.account.newAccountModule
import com.fsck.k9.backends.backendsModule
import com.fsck.k9.controller.ControllerExtension
import com.fsck.k9.crypto.EncryptionExtractor
import com.fsck.k9.crypto.openpgp.OpenPgpEncryptionExtractor
import com.fsck.k9.feature.featureModule
import com.fsck.k9.featureflag.InMemoryFeatureFlagFactory
import com.fsck.k9.notification.notificationModule
import com.fsck.k9.preferences.K9StoragePersister
import com.fsck.k9.preferences.StoragePersister
import com.fsck.k9.resources.resourcesModule
import com.fsck.k9.storage.storageModule
import com.fsck.k9.widget.list.messageListWidgetConfigModule
import org.koin.core.qualifier.named
import org.koin.dsl.module

val commonAppModule = module {
    single {
        MessagingListenerProvider(
            listOf(
                get<UnreadWidgetUpdateListener>(),
            ),
        )
    }
    single(named("controllerExtensions")) { emptyList<ControllerExtension>() }
    single<EncryptionExtractor> { OpenPgpEncryptionExtractor.newInstance() }
    single<StoragePersister> { K9StoragePersister(get()) }
    single<FeatureFlagFactory> { InMemoryFeatureFlagFactory() }
    single<FeatureFlagProvider> {
        InMemoryFeatureFlagProvider(
            featureFlagFactory = get(),
        )
    }
}

val commonAppModules = listOf(
    commonAppModule,
    messageListWidgetConfigModule,
    messageListWidgetModule,
    unreadWidgetModule,
    notificationModule,
    resourcesModule,
    backendsModule,
    storageModule,
    newAccountModule,
    featureModule,
)
