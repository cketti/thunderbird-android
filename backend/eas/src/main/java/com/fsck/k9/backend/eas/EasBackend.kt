package com.fsck.k9.backend.eas


import android.content.Context
import com.fsck.k9.backend.api.Backend
import com.fsck.k9.backend.api.BackendFolder.MoreMessages
import com.fsck.k9.backend.api.BackendStorage
import com.fsck.k9.backend.api.SyncConfig
import com.fsck.k9.backend.api.SyncListener
import com.fsck.k9.mail.BodyFactory
import com.fsck.k9.mail.FetchProfile
import com.fsck.k9.mail.Flag
import com.fsck.k9.mail.Folder
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.PushReceiver
import com.fsck.k9.mail.Pusher
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.message.MessageBuilderFactory
import com.fsck.k9.protocol.eas.Account
import com.fsck.k9.protocol.eas.HostAuth
import com.fsck.k9.protocol.eas.PolicyManager


class EasBackend(
        private val context: Context,
        private val emailAddress: String,
        private val serverSettings: ServerSettings,
        private val backendStorage: BackendStorage,
        private val extraBackendStorage: ExtraBackendStorage,
        messageBuilderFactory: MessageBuilderFactory,
        private val deviceId: String,
        policyManager: PolicyManager
) : Backend {
    private val easAccount: EasAccount = createEasAccount(emailAddress, serverSettings, backendStorage, policyManager,
            extraBackendStorage)
    private val folderSync = FolderSync(context, easAccount, backendStorage)
    private val emailSync = EmailSync(context, easAccount, backendStorage, extraBackendStorage,
            messageBuilderFactory, folderSync, policyManager)
    private val emailSend = EmailSend(context, easAccount)
    private val emailMove = EmailMove(context, easAccount)

    override val supportsSeenFlag = true
    override val supportsExpunge = false
    override val supportsMove = true
    override val supportsCopy = false
    override val supportsUpload = false
    override val supportsTrashFolder = true
    override val supportsSearchByDate = false
    override val isPushCapable = false
    override val isDeleteMoveToTrash = true


    override fun refreshFolderList() {
        folderSync.syncFolders()
    }

    override fun sync(
            folder: String,
            syncConfig: SyncConfig,
            listener: SyncListener,
            providedRemoteFolder: Folder<*>?
    ) {
        //TODO: more SyncListener callbacks

        emailSync.syncFolder(folder, object : SyncFolderCallback {
            override fun onMessageFullyDownloaded(folderServerId: String, messageServerId: String) {
                listener.syncNewMessage(folderServerId, messageServerId, false)
            }
        })
    }

    override fun downloadMessage(syncConfig: SyncConfig, folderServerId: String, messageServerId: String) {
        emailSync.fullyDownloadMessage(folderServerId, messageServerId)
    }

    override fun setFlag(folderServerId: String, messageServerIds: List<String>, flag: Flag, newState: Boolean) {
        if (flag == Flag.DELETED) {
            require(newState) { "Undeleting messages is not supported" }
            emailSync.deleteMessages(folderServerId, messageServerIds)
        } else {
            emailSync.setFlag(folderServerId, messageServerIds, flag, newState)
        }
    }

    override fun markAllAsRead(folderServerId: String) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun expunge(folderServerId: String) {
        throw UnsupportedOperationException("not supported")
    }

    override fun expungeMessages(folderServerId: String, messageServerIds: List<String>) {
        throw UnsupportedOperationException("not supported")
    }

    override fun deleteAllMessages(folderServerId: String) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun moveMessages(
            sourceFolderServerId: String,
            targetFolderServerId: String,
            messageServerIds: List<String>
    ): Map<String, String>? {
        val moveStatus = emailMove.moveMessages(sourceFolderServerId, targetFolderServerId, messageServerIds)
        return moveStatus.serverIdMappingForSuccessfulMoves
    }

    override fun copyMessages(
            sourceFolderServerId: String,
            targetFolderServerId: String,
            messageServerIds: List<String>
    ): Map<String, String>? {
        throw UnsupportedOperationException("not implemented")
    }

    override fun search(
            folderServerId: String,
            query: String?,
            requiredFlags: Set<Flag>?,
            forbiddenFlags: Set<Flag>?
    ): List<String> {
        throw UnsupportedOperationException("not implemented")
    }

    override fun fetchMessage(folderServerId: String, messageServerId: String, fetchProfile: FetchProfile): Message {
        throw UnsupportedOperationException("not implemented")
    }

    override fun fetchPart(folderServerId: String, messageServerId: String, part: Part, bodyFactory: BodyFactory) {
        throw UnsupportedOperationException("not implemented")
    }

    override fun findByMessageId(folderServerId: String, messageId: String): String? {
        throw UnsupportedOperationException("not supported")
    }

    override fun uploadMessage(folderServerId: String, message: Message): String? {
        throw UnsupportedOperationException("not supported")
    }

    override fun createPusher(receiver: PushReceiver): Pusher {
        throw UnsupportedOperationException("not supported")
    }

    override fun checkIncomingServerSettings() {
        checkServerSettings()
    }

    override fun sendMessage(message: Message) {
        val easMessage = WrappedMailMessage(message)
        emailSend.sendMessage(easMessage)
    }

    override fun checkOutgoingServerSettings() {
        checkServerSettings()
    }

    private fun checkServerSettings() {
        val settingsChecker = EasSettingsChecker(context, emailAddress, serverSettings.username,
                serverSettings.password, serverSettings.host, deviceId, false)

        settingsChecker.checkServerSettings()
    }

    private fun createEasAccount(
            emailAddress: String,
            serverSettings: ServerSettings,
            backendStorage: BackendStorage,
            policyManager: PolicyManager,
            extraBackendStorage: ExtraBackendStorage
    ): EasAccount {
        val hostAuth = HostAuth().apply {
            mLogin = serverSettings.username
            mPassword = serverSettings.password
            mAddress = serverSettings.host
            mFlags = HostAuth.FLAG_SSL
        }

        return EasAccount(backendStorage, deviceId, policyManager, extraBackendStorage).apply {
            mHostAuthRecv = hostAuth
            mEmailAddress = emailAddress
        }
    }


    fun getSyncWindowForFolder(folderServerId: String): Int {
        return extraBackendStorage.getSyncWindowForFolder(folderServerId)
    }

    fun setSyncWindowForFolder(folderServerId: String, syncWindow: Int) {
        extraBackendStorage.setSyncWindowForFolder(folderServerId, syncWindow)
    }

    fun setMoreMessagesForFolder(folderServerId: String, isMoreMessages: Boolean){
        val backendFolder = backendStorage.getFolder(folderServerId)
        val moreMessages = if (isMoreMessages) MoreMessages.TRUE else MoreMessages.FALSE
        backendFolder.setMoreMessages(moreMessages)
    }

    internal class EasAccount(
            private val backendStorage: BackendStorage,
            private val deviceId: String,
            private val policyManager: PolicyManager,
            private val extraBackendStorage: ExtraBackendStorage
    ) : Account() {

        init {
            mSyncKey = backendStorage.getFoldersSyncKey()

            val policyKey = backendStorage.getPolicyKey()
            setPolicyKeyInternal(policyKey)
        }

        override fun setPolicyKey(policyKey: String) {
            setPolicyKeyInternal(policyKey)
            backendStorage.setPolicyKey(policyKey)
        }

        private fun setPolicyKeyInternal(policyKey: String?) {
            super.setPolicyKey(policyKey)
        }

        override fun getDeviceId(): String {
            return deviceId
        }

        override fun shouldHandleFullProvisioning(): Boolean {
            return true
        }

        override fun remoteWipe() {
            extraBackendStorage.wipeAccount()
        }

        override fun getPolicyManager(): PolicyManager {
            return policyManager
        }
    }
}
