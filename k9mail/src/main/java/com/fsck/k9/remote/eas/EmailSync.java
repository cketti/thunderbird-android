package com.fsck.k9.remote.eas;


import android.content.Context;

import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mail.store.eas.Account;
import com.fsck.k9.mail.store.eas.EasSyncBase;
import com.fsck.k9.mail.store.eas.EasSyncMail;
import com.fsck.k9.mail.store.eas.Mailbox;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import com.fsck.k9.remote.BackendStorage;


class EmailSync implements EmailSyncCallback {
    private final Context context;
    private final Account account;
    private final BackendStorage backendStorage;
    private Mailbox mailbox;


    EmailSync(Context context, Account account, BackendStorage backendStorage) {
        this.context = context;
        this.account = account;
        this.backendStorage = backendStorage;
    }

    public boolean syncFolder(String serverId) {
        EasSyncMail syncMail = new EasSyncMail(this);

        mailbox = new Mailbox();
        mailbox.mServerId = serverId;
        //TODO: fetch syncKey from backendStorage and use it

        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);
        int result = syncBase.performOperation();

        return result >= EasSyncBase.RESULT_MIN_OK_RESULT;
    }

    @Override
    public void addMessage(MessageServerData messageServerData) {
        backendStorage.createMessage(messageServerData);
    }

    @Override
    public void removeMessage(String serverId) {
        //TODO: implement
    }

    @Override
    public void readStateChanged(String serverId, boolean read) {
        //TODO: implement
    }

    @Override
    public void flagStateChanged(String serverId, boolean flag) {
        //TODO: implement
    }

    @Override
    public void messageWasRepliedTo(String serverId) {
        //TODO: implement
    }

    @Override
    public void messageWasForwarded(String serverId) {
        //TODO: implement
    }

    @Override
    public void commitMessageChanges() {
        //TODO: implement
    }

    @Override
    public boolean isFirstSync() {
        return "0".equals(mailbox.mSyncKey);
    }

    @Override
    public boolean setSyncKey(String syncKey) {
        mailbox.mSyncKey = syncKey;
        return false;
    }

    @Override
    public void restartSync() {
        //TODO: implement
    }

    @Override
    public void wipe() {
        //TODO: implement
    }
}
