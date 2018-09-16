package com.fsck.k9.backend.eas


import android.content.Context
import com.fsck.k9.backend.api.BackendStorage
import com.fsck.k9.backend.api.FolderInfo
import com.fsck.k9.protocol.eas.Account
import com.fsck.k9.protocol.eas.Eas
import com.fsck.k9.protocol.eas.EasFolderSync
import com.fsck.k9.protocol.eas.EasOperation
import com.fsck.k9.protocol.eas.callback.FolderSyncCallback


//TODO: wrap all database changes in a transaction and roll back in case of error
internal class FolderSync(
        private val context: Context,
        private val account: Account,
        private val backendStorage: BackendStorage
) : FolderSyncCallback {

    fun syncFolders(): Boolean {
        val folderSync = EasFolderSync(context, account, this)
        val result = folderSync.performOperation()
        //TODO: retry if status == Eas.FOLDER_STATUS_INVALID_KEY
        if (result == EasOperation.RESULT_FORBIDDEN) {
            throw AccessDeniedException()
        }

        return result >= EasOperation.RESULT_MIN_OK_RESULT
    }

    override fun addFolder(serverId: String, name: String, type: Int, parentServerId: String) {
        //TODO: use parentServerId and folderType
        val folderType = convertFolderType(type)
        if (folderType != null) {
            backendStorage.createFolders(listOf(FolderInfo(name, serverId)))
        }
    }

    override fun removeFolder(serverId: String) {
        backendStorage.deleteFolders(listOf(serverId))
    }

    override fun changeFolder(serverId: String, name: String, parentServerId: String) {
        //TODO: use parentServerId
        backendStorage.changeFolder(serverId, name)
    }

    override fun clearFolders() {
        val folderServerIds = backendStorage.getFolderServerIds()
        backendStorage.deleteFolders(folderServerIds)
    }

    override fun commitFolderChanges() {
        saveSyncKeyIfChanged(account.mSyncKey)
    }

    private fun saveSyncKeyIfChanged(syncKey: String) {
        val syncKeyHasChanged = syncKey != backendStorage.getFoldersSyncKey()
        if (syncKeyHasChanged) {
            backendStorage.setFoldersSyncKey(syncKey)
        }
    }

    private fun convertFolderType(type: Int): BackendFolderType? = when (type) {
        Eas.MAILBOX_TYPE_USER_MAIL -> BackendFolderType.REGULAR
        Eas.MAILBOX_TYPE_DRAFTS -> BackendFolderType.DRAFTS
        Eas.MAILBOX_TYPE_INBOX -> BackendFolderType.INBOX
        Eas.MAILBOX_TYPE_OUTBOX -> BackendFolderType.OUTBOX
        Eas.MAILBOX_TYPE_SENT -> BackendFolderType.SENT
        Eas.MAILBOX_TYPE_DELETED -> BackendFolderType.TRASH
        else -> null
    }
}
