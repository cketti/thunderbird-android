package com.fsck.k9.remote.eas;


import android.content.Context;

import com.fsck.k9.mail.store.eas.Account;
import com.fsck.k9.mail.store.eas.Eas;
import com.fsck.k9.mail.store.eas.EasFolderSync;
import com.fsck.k9.mail.store.eas.EasOperation;
import com.fsck.k9.mail.store.eas.callback.FolderSyncCallback;
import com.fsck.k9.remote.BackendFolderType;
import com.fsck.k9.remote.BackendStorage;


//TODO: wrap all database changes in a transaction and roll back in case of error
class FolderSync implements FolderSyncCallback {
    private final Context context;
    private final Account account;
    private final BackendStorage backendStorage;


    FolderSync(Context context, Account account, BackendStorage backendStorage) {
        this.context = context;
        this.account = account;
        this.backendStorage = backendStorage;
    }

    public boolean syncFolders() {
        EasFolderSync folderSync = new EasFolderSync(context, account, this);
        int result = folderSync.performOperation();
        //TODO: retry if status == Eas.FOLDER_STATUS_INVALID_KEY

        return result >= EasOperation.RESULT_MIN_OK_RESULT;
    }

    @Override
    public void addFolder(String serverId, String name, int type, String parentServerId) {
        BackendFolderType folderType = convertFolderType(type);
        if (folderType != null) {
            backendStorage.createFolder(name, folderType, serverId, parentServerId);
        }
    }

    @Override
    public void removeFolder(String serverId) {
        backendStorage.deleteFolderByServerId(serverId);
    }

    @Override
    public void changeFolder(String serverId, String name, String parentServerId) {
        //TODO: implement
    }

    @Override
    public void clearFolders() {
        backendStorage.deleteAllFolders();
    }

    @Override
    public void commitFolderChanges() {
        saveSyncKeyIfChanged(account.mSyncKey);
    }

    private void saveSyncKeyIfChanged(String syncKey) {
        boolean syncKeyHasChanged = !syncKey.equals(backendStorage.getFoldersSyncKey());
        if (syncKeyHasChanged) {
            backendStorage.setFoldersSyncKey(syncKey);
        }
    }

    private BackendFolderType convertFolderType(int type) {
        switch (type) {
            case Eas.MAILBOX_TYPE_USER_MAIL: {
                return BackendFolderType.REGULAR;
            }
            case Eas.MAILBOX_TYPE_DRAFTS: {
                return BackendFolderType.DRAFTS;
            }
            case Eas.MAILBOX_TYPE_INBOX: {
                return BackendFolderType.INBOX;
            }
            case Eas.MAILBOX_TYPE_OUTBOX: {
                return BackendFolderType.OUTBOX;
            }
            case Eas.MAILBOX_TYPE_SENT: {
                return BackendFolderType.SENT;
            }
            case Eas.MAILBOX_TYPE_DELETED: {
                return BackendFolderType.TRASH;
            }
        }

        return null;
    }
}
