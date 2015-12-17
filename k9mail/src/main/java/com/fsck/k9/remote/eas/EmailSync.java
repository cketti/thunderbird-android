package com.fsck.k9.remote.eas;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.Context;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mail.store.eas.Account;
import com.fsck.k9.mail.store.eas.Eas;
import com.fsck.k9.mail.store.eas.EasFetchMail;
import com.fsck.k9.mail.store.eas.EasOperation;
import com.fsck.k9.mail.store.eas.EasChangeFlag;
import com.fsck.k9.mail.store.eas.EasSyncBase;
import com.fsck.k9.mail.store.eas.EasSyncMail;
import com.fsck.k9.mail.store.eas.Mailbox;
import com.fsck.k9.mail.store.eas.MessageStateChange;
import com.fsck.k9.mail.store.eas.adapter.MoveItemsParser;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import com.fsck.k9.remote.BackendStorage;
import com.fsck.k9.remote.DeleteStatus;


class EmailSync {
    private final Context context;
    private final Account account;
    private final BackendStorage backendStorage;
    private final FolderSync folderSync;


    EmailSync(Context context, Account account, BackendStorage backendStorage, FolderSync folderSync) {
        this.context = context;
        this.account = account;
        this.backendStorage = backendStorage;
        this.folderSync = folderSync;
    }

    public boolean syncFolder(String serverId) {
        Mailbox mailbox = createMailbox(serverId);

        EasSyncMail syncMail = new EasSyncMail(new BackendEmailSyncCallback(mailbox));
        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);

        int result = performSyncOperation(syncBase);

        return result >= EasSyncBase.RESULT_MIN_OK_RESULT;
    }

    public boolean setFlag(String folderServerId, List<String> messageServerIds, Flag flag, boolean newState) {
        Mailbox mailbox = createMailbox(folderServerId);
        List<MessageStateChange> changes = createMessageStateChangeList(messageServerIds, flag, newState);
        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox);

        EasChangeFlag easChangeFlag = new EasChangeFlag(context, account, mailbox, changes, callback);
        int result = performSyncOperation(easChangeFlag);

        return result == EasChangeFlag.RESULT_OK;
    }

    private List<MessageStateChange> createMessageStateChangeList(List<String> messageServerIds, Flag flag,
            boolean newState) {
        List<MessageStateChange> changes = new ArrayList<MessageStateChange>(messageServerIds.size());
        for (String messageServerId : messageServerIds) {
            MessageStateChange change = MessageStateChange.builder()
                    .serverId(messageServerId)
                    .setFlag(flag, newState)
                    .build();

            changes.add(change);
        }

        return changes;
    }

    public DeleteStatus deleteMessages(String folderServerId, List<String> messageServerIds) {
        Mailbox mailbox = createMailbox(folderServerId);
        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox);

        EasSyncMail syncMail = new EasSyncMail(callback, messageServerIds);
        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);
        performSyncOperation(syncBase);

        return new EasDeleteStatus(syncMail.getMessageDeleteStatus());
    }

    private Mailbox createMailbox(String serverId) {
        Mailbox mailbox = new Mailbox();
        mailbox.mServerId = serverId;
        mailbox.mSyncKey = backendStorage.getSyncKeyForFolder(serverId);
        int syncWindow = backendStorage.getSyncWindowForFolder(serverId);
        mailbox.syncWindow = Integer.toString(syncWindow);
        return mailbox;
    }

    private int performSyncOperation(EasOperation operation) {
        int result = operation.performOperation();
        if (result != EasSyncBase.RESULT_FOLDER_SYNC_REQUIRED) {
            return result;
        }

        if (!folderSync.syncFolders()) {
            return EasOperation.RESULT_OTHER_FAILURE;
        }

        return operation.performOperation();
    }

    public boolean fullyDownloadMessage(String folderServerId, String messageServerId) {
        Mailbox mailbox = createMailbox(folderServerId);
        List<String> messageServerIds = Collections.singletonList(messageServerId);

        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox);
        EasFetchMail fetchMail = new EasFetchMail(context, account, mailbox, messageServerIds, callback);
        int result = performSyncOperation(fetchMail);

        return result >= EasSyncBase.RESULT_MIN_OK_RESULT;
    }

    public boolean increaseSyncWindow(String serverId) {
        int currentSyncWindow = backendStorage.getSyncWindowForFolder(serverId);
        if (currentSyncWindow == Eas.FILTER_ALL) {
            return false;
        }

        int newSyncWindow;
        if (currentSyncWindow < Eas.FILTER_6_MONTHS) {
            newSyncWindow = currentSyncWindow + 1;
        } else {
            newSyncWindow = Eas.FILTER_ALL;
            backendStorage.setMoreMessagesForFolder(serverId, false);
        }

        backendStorage.setSyncWindowForFolder(serverId, newSyncWindow);

        return true;
    }


    class BackendEmailSyncCallback implements EmailSyncCallback {
        private final Mailbox mailbox;
        private boolean syncKeyChanged = false;


        BackendEmailSyncCallback(Mailbox mailbox) {
            this.mailbox = mailbox;
        }

        @Override
        public void addMessage(MessageServerData messageServerData) {
            backendStorage.createMessage(messageServerData);
        }

        @Override
        public void removeMessage(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.removeMessage(folderServerId, serverId);
        }

        @Override
        public void readStateChanged(String serverId, boolean read) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.SEEN, read);
        }

        @Override
        public void flagStateChanged(String serverId, boolean flag) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.FLAGGED, flag);
        }

        @Override
        public void messageWasRepliedTo(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.ANSWERED, true);
        }

        @Override
        public void messageWasForwarded(String serverId) {
            String folderServerId = mailbox.mServerId;
            backendStorage.setMessageFlag(folderServerId, serverId, Flag.FORWARDED, true);
        }

        @Override
        public void commitMessageChanges() {
            saveSyncKeyIfChanged();
        }

        private void saveSyncKeyIfChanged() {
            if (syncKeyChanged) {
                backendStorage.setSyncKeyForFolder(mailbox.mServerId, mailbox.mSyncKey);
            }
        }

        @Override
        public boolean isFirstSync() {
            return "0".equals(mailbox.mSyncKey);
        }

        @Override
        public boolean setSyncKey(String syncKey) {
            String oldSyncKey = mailbox.mSyncKey;
            boolean changed = oldSyncKey == null || !oldSyncKey.equals(syncKey);
            if (!syncKeyChanged && changed) {
                syncKeyChanged = true;
            }

            mailbox.mSyncKey = syncKey;

            return changed;
        }

        @Override
        public void prepareSyncRestart() {
            String folderServerId = mailbox.mServerId;
            backendStorage.removeAllMessages(folderServerId);
        }
    }

    private static class EasDeleteStatus implements DeleteStatus {
        private List<String> retries = new ArrayList<String>();
        private List<String> reverts = new ArrayList<String>();


        public EasDeleteStatus(Map<String, Integer> deleteStatus) {
            init(deleteStatus);
        }

        private void init(Map<String, Integer> moveStatus) {
            for (Entry<String, Integer> entry : moveStatus.entrySet()) {
                String serverId = entry.getKey();
                int status = entry.getValue();

                switch (status) {
                    case MoveItemsParser.STATUS_CODE_SUCCESS: {
                        break;
                    }
                    case MoveItemsParser.STATUS_CODE_RETRY: {
                        retries.add(serverId);
                        break;
                    }
                    case MoveItemsParser.STATUS_CODE_REVERT: {
                        reverts.add(serverId);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown EAS delete status: " + status);
                    }
                }
            }
        }

        @Override
        public List<String> getServerIdsForRetries() {
            return Collections.unmodifiableList(retries);
        }

        @Override
        public List<String> getServerIdsForReverts() {
            return Collections.unmodifiableList(reverts);
        }
    }
}
