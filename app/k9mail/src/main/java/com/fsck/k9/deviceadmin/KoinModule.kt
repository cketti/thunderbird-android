package com.fsck.k9.deviceadmin

import com.fsck.k9.protocol.eas.PolicyManager
import org.koin.dsl.module.applicationContext

val deviceAdminModule = applicationContext {
    bean { K9PolicyManager(get()) } bind PolicyManager::class
}
