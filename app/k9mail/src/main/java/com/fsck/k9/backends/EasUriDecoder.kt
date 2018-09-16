package com.fsck.k9.backends

import android.net.Uri
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings

object EasUriDecoder {
    @JvmStatic
    fun decodeUri(uri: String): ServerSettings {
        val storeUri = Uri.parse(uri)

        val username = storeUri.getQueryParameter("username")
        val password = storeUri.getQueryParameter("password")
        val hostname = storeUri.host

        return ServerSettings("eas", hostname, 443, ConnectionSecurity.SSL_TLS_REQUIRED,
                AuthType.AUTOMATIC, username, password, null)
    }
}
