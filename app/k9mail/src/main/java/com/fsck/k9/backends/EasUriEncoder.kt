package com.fsck.k9.backends

import android.net.Uri
import com.fsck.k9.mail.ServerSettings

object EasUriEncoder {
    fun createUri(serverSettings: ServerSettings): String {
        return createUri(serverSettings.host, serverSettings.username, serverSettings.password)
    }

    @JvmStatic
    fun createUri(host: String, username: String, password: String): String {
        return Uri.Builder()
                .scheme("eas")
                .authority(host)
                .appendQueryParameter("username", username)
                .appendQueryParameter("password", password)
                .build()
                .toString()
    }
}
