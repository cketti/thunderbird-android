package com.fsck.k9.appconfig

import com.fsck.k9.autocrypt.AutocryptStringProvider
import org.koin.dsl.module.applicationContext

val appConfigModule = applicationContext {
    bean { K9AutocryptStringProvider(get()) as AutocryptStringProvider }
}
