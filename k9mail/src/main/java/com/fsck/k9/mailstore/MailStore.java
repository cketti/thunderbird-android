package com.fsck.k9.mailstore;


import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mailstore.LockableDatabase.DbCallback;
import com.fsck.k9.mailstore.LockableDatabase.WrappedException;
import com.fsck.k9.mailstore.data.Folder;


public class MailStore {
    private static final String[] FOLDER_ID_COLUMN = new String[] { FolderColumns.ID };


    private final LocalStore localStore;
    private final Account account;
    private final Preferences preferences;


    MailStore(LocalStore localStore) {
        this.localStore = localStore;
        account = localStore.getAccount();
        preferences = Preferences.getPreferences(localStore.context);
    }

    public String getFoldersSyncKey() {
        return account.getFoldersSyncKey();
    }

    public void setFoldersSyncKey(String syncKey) {
        account.setFoldersSyncKey(syncKey);
        saveAccount();
    }

    public String getPolicyKey() {
        return account.getPolicyKey();
    }

    public void setPolicyKey(String policyKey) {
        account.setPolicyKey(policyKey);
        saveAccount();
    }

    private void saveAccount() {
        account.save(preferences);
    }

    public long createFolder(Folder folder) {
        final ContentValues values = new ContentValues();
        values.put(FolderColumns.NAME, folder.name);
        values.put(FolderColumns.VISIBLE_LIMIT, folder.visibleLimit);
        values.put(FolderColumns.TOP_GROUP, folder.inTopGroup);
        values.put(FolderColumns.DISPLAY_CLASS, folder.displayClass.name());
        values.put(FolderColumns.POLL_CLASS, folder.syncClass.name());
        values.put(FolderColumns.NOTIFY_CLASS, folder.notifyClass.name());
        values.put(FolderColumns.PUSH_CLASS, folder.pushClass.name());
        values.put(FolderColumns.INTEGRATE, folder.integrate);
        values.put(FolderColumns.MORE_MESSAGES, folder.moreMessages.getDatabaseName());
        values.put(FolderColumns.SERVER_ID, folder.serverId);
        if (folder.parent != -1) {
            values.put(FolderColumns.PARENT, folder.parent);
        }

        return dbOperation(new DbCallback<Long>() {
            @Override
            public Long doDbWork(SQLiteDatabase db) throws WrappedException, MessagingException {
                return db.insert(Tables.FOLDERS, null, values);
            }
        });
    }

    public int deleteFolderByServerId(final String serverId) {
        return dbOperation(new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(SQLiteDatabase db) throws WrappedException, MessagingException {
                return db.delete(Tables.FOLDERS, FolderColumns.SERVER_ID + " = ?", new String[] { serverId });
            }
        });
    }

    public int deleteAllFolders() {
        return dbOperation(new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(SQLiteDatabase db) throws WrappedException, MessagingException {
                return db.delete(Tables.FOLDERS, null, null);
            }
        });
    }

    public long getFolderIdByServerId(final String serverId) {
        return dbOperation(new DbCallback<Long>() {
            @Override
            public Long doDbWork(SQLiteDatabase db) throws WrappedException, MessagingException {
                return getFolderIdByServerId(db, serverId);
            }
        });
    }

    private long getFolderIdByServerId(SQLiteDatabase db, String serverId) {
        Cursor cursor = db.query(Tables.FOLDERS, FOLDER_ID_COLUMN, FolderColumns.SERVER_ID + " = ?",
                new String[] { serverId }, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }

        return -1;
    }

    private <T> T dbOperation(DbCallback<T> callback) {
        try {
            return localStore.getDatabase().execute(false, callback);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
