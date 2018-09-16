package com.fsck.k9.backend.eas

interface ExtraBackendStorage {
    fun getPartiallyDownloadedMessages(folderServerId: String): List<String>
    fun getSyncWindowForFolder(folderServerId: String): Int
    fun setSyncWindowForFolder(folderServerId: String, syncWindow: Int)
    fun removeAllMessages(folderServerId: String)
    fun wipeAccount()
}
