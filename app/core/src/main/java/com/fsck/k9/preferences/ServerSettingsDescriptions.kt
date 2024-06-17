package com.fsck.k9.preferences

import com.fsck.k9.preferences.Settings.IntegerRangeSetting
import com.fsck.k9.preferences.Settings.SettingsDescription
import com.fsck.k9.preferences.Settings.SettingsUpgrader
import com.fsck.k9.preferences.Settings.StringSetting
import java.util.TreeMap

/**
 * Contains information to validate imported server settings with a given content version, and to upgrade those server
 * settings to the latest content version.
 */
@Suppress("MagicNumber")
internal class ServerSettingsDescriptions {
    val settings: Map<String, TreeMap<Int, SettingsDescription<*>>> by lazy {
        buildMap {
            put(
                HOST,
                Settings.versions(
                    Settings.V(1, StringSetting(null)),
                ),
            )
            put(
                PORT,
                Settings.versions(
                    Settings.V(1, IntegerRangeSetting(1, 65535, -1)),
                ),
            )
            put(
                CONNECTION_SECURITY,
                Settings.versions(
                    Settings.V(
                        1,
                        StringEnumSetting(
                            defaultValue = "SSL_TLS_REQUIRED",
                            values = setOf(
                                "NONE",
                                "STARTTLS_OPTIONAL",
                                "STARTTLS_REQUIRED",
                                "SSL_TLS_OPTIONAL",
                                "SSL_TLS_REQUIRED",
                            ),
                        ),
                    ),
                ),
            )
            put(
                AUTHENTICATION_TYPE,
                Settings.versions(
                    Settings.V(
                        1,
                        NoDefaultStringEnumSetting(
                            values = setOf(
                                "PLAIN",
                                "CRAM_MD5",
                                "EXTERNAL",
                                "XOAUTH2",
                                "AUTOMATIC",
                                "LOGIN",
                            ),
                        ),
                    ),
                ),
            )
            put(
                USERNAME,
                Settings.versions(
                    Settings.V(1, StringSetting("")),
                ),
            )
            put(
                PASSWORD,
                Settings.versions(
                    Settings.V(1, StringSetting(null)),
                ),
            )
            put(
                CLIENT_CERTIFICATE_ALIAS,
                Settings.versions(
                    Settings.V(1, StringSetting(null)),
                ),
            )
        }
    }

    val upgraders: Map<Int, SettingsUpgrader> by lazy {
        emptyMap()
    }

    companion object {
        const val HOST = "host"
        const val PORT = "port"
        const val CONNECTION_SECURITY = "connectionSecurity"
        const val AUTHENTICATION_TYPE = "authenticationType"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val CLIENT_CERTIFICATE_ALIAS = "clientCertificateAlias"
    }
}
