package com.fsck.k9.backends

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.Preferences
import com.fsck.k9.backend.BackendFactory
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.eas.EasBackend
import com.fsck.k9.backend.eas.EasMessageBuilderFactory
import com.fsck.k9.backend.eas.ExtraBackendStorage
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mailstore.K9BackendStorage
import com.fsck.k9.protocol.eas.PolicyManager

class EasBackendFactory(
        private val context: Context,
        private val preferences: Preferences,
        private val policyManager: PolicyManager,
        private val deviceIdProvider: DeviceIdProvider
) : BackendFactory {
    override val transportUriPrefix = "eas"

    override fun createBackend(account: Account): Backend {
        val emailAddress = account.email
        val serverSettings = decodeStoreUri(account.storeUri)
        val backendStorage = K9BackendStorage(preferences, account, account.localStore)
        val extraBackendStorage = K9ExtraBackendStorage()
        val messageBuilderFactory = EasMessageBuilderFactory(context)
        val deviceId = deviceIdProvider.deviceId
        return EasBackend(context, emailAddress, serverSettings, backendStorage, extraBackendStorage,
                messageBuilderFactory, deviceId, policyManager)
    }

    override fun decodeStoreUri(storeUri: String): ServerSettings {
        return EasUriDecoder.decodeUri(storeUri)
    }

    override fun createStoreUri(serverSettings: ServerSettings): String {
        return EasUriEncoder.createUri(serverSettings)
    }

    override fun decodeTransportUri(transportUri: String): ServerSettings {
        return EasUriDecoder.decodeUri(transportUri)
    }

    override fun createTransportUri(serverSettings: ServerSettings): String {
        return EasUriEncoder.createUri(serverSettings)
    }
}

//TODO: This functionality should probably be merged into BackendStorage
private class K9ExtraBackendStorage : ExtraBackendStorage {
    override fun getPartiallyDownloadedMessages(folderServerId: String): List<String> {
        TODO("implement")
    }

    override fun getSyncWindowForFolder(folderServerId: String): Int {
        TODO("implement")
    }

    override fun setSyncWindowForFolder(folderServerId: String, syncWindow: Int) {
        TODO("implement")
    }

    override fun removeAllMessages(folderServerId: String) {
        TODO("implement")
    }

    override fun wipeAccount() {
        TODO("implement")
    }
}
