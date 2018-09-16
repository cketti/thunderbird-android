package com.fsck.k9.backend.eas

import com.fsck.k9.backend.api.BackendStorage

private const val EXTRA_POLICY_KEY = "policyKey"
private const val EXTRA_FOLDERS_SYNC_KEY = "foldersSyncKey"

fun BackendStorage.getPolicyKey(): String? = getExtraString(EXTRA_POLICY_KEY)

fun BackendStorage.setPolicyKey(policyKey: String) {
    setExtraString(EXTRA_POLICY_KEY, policyKey)
}

fun BackendStorage.getFoldersSyncKey(): String? = getExtraString(EXTRA_FOLDERS_SYNC_KEY)

fun BackendStorage.setFoldersSyncKey(syncKey: String) {
    setExtraString(EXTRA_FOLDERS_SYNC_KEY, syncKey)
}
