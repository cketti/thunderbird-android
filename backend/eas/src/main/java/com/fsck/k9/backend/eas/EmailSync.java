package com.fsck.k9.backend.eas;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.Context;

import com.fsck.k9.backend.api.BackendFolder;
import com.fsck.k9.backend.api.BackendFolder.MoreMessages;
import com.fsck.k9.backend.api.BackendStorage;
import com.fsck.k9.backend.eas.legacy.LegacyMessage;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mail.message.MessageBuilderFactory;
import com.fsck.k9.protocol.eas.Account;
import com.fsck.k9.protocol.eas.Eas;
import com.fsck.k9.protocol.eas.EasChangeFlag;
import com.fsck.k9.protocol.eas.EasFetchMail;
import com.fsck.k9.protocol.eas.EasOperation;
import com.fsck.k9.protocol.eas.EasSyncBase;
import com.fsck.k9.protocol.eas.EasSyncMail;
import com.fsck.k9.protocol.eas.Mailbox;
import com.fsck.k9.protocol.eas.MessageStateChange;
import com.fsck.k9.protocol.eas.PolicyManager;
import com.fsck.k9.protocol.eas.adapter.MoveItemsParser;
import com.fsck.k9.protocol.eas.callback.EmailSyncCallback;


class EmailSync {
    private static final String EXTRA_SYNC_KEY = "syncKey";


    private final Context context;
    private final Account account;
    private final BackendStorage backendStorage;
    private final ExtraBackendStorage extraBackendStorage;
    private final MessageBuilderFactory messageBuilderFactory;
    private final FolderSync folderSync;
    private final PolicyManager policyManager;


    EmailSync(Context context, Account account, BackendStorage backendStorage, ExtraBackendStorage extraBackendStorage,
            MessageBuilderFactory messageBuilderFactory, FolderSync folderSync, PolicyManager policyManager) {
        this.context = context;
        this.account = account;
        this.backendStorage = backendStorage;
        this.extraBackendStorage = extraBackendStorage;
        this.messageBuilderFactory = messageBuilderFactory;
        this.folderSync = folderSync;
        this.policyManager = policyManager;
    }

    public boolean syncFolder(String folderServerId, SyncFolderCallback callback) throws MessagingException {
        Mailbox mailbox = createMailbox(folderServerId);

        BackendEmailSyncCallback emailSyncCallback = new BackendEmailSyncCallback(mailbox, false);
        EasSyncMail syncMail = new EasSyncMail(emailSyncCallback, messageBuilderFactory);
        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);

        int result = performSyncOperation(syncBase);

        if (result == EasOperation.RESULT_AUTHENTICATION_ERROR) {
            throw new AuthenticationFailedException("Authentication failed");
        }

        if (result < EasSyncBase.RESULT_MIN_OK_RESULT) {
            return false;
        }

        List<String> messagesToDownload = extraBackendStorage.getPartiallyDownloadedMessages(folderServerId);

        for (String messageServerId : messagesToDownload) {
            boolean success = fullyDownloadMessage(folderServerId, messageServerId);
            if (!success) {
                return false;
            }

            if (callback != null) {
                callback.onMessageFullyDownloaded(folderServerId, messageServerId);
            }
        }

        return true;
    }

    public boolean setFlag(String folderServerId, List<String> messageServerIds, Flag flag, boolean newState)
            throws MessagingException {
        Mailbox mailbox = createMailbox(folderServerId);
        List<MessageStateChange> changes = createMessageStateChangeList(messageServerIds, flag, newState);
        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox, false);

        EasChangeFlag easChangeFlag = new EasChangeFlag(context, account, mailbox, changes, callback,
                messageBuilderFactory);
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

    public DeleteStatus deleteMessages(String folderServerId, List<String> messageServerIds) throws MessagingException {
        Mailbox mailbox = createMailbox(folderServerId);
        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox, false);

        EasSyncMail syncMail = new EasSyncMail(callback, messageBuilderFactory, messageServerIds);
        EasSyncBase syncBase = new EasSyncBase(context, account, mailbox, syncMail);
        performSyncOperation(syncBase);

        return new EasDeleteStatus(syncMail.getMessageDeleteStatus());
    }

    private Mailbox createMailbox(String serverId) {
        BackendFolder backendFolder = backendStorage.getFolder(serverId);

        Mailbox mailbox = new Mailbox();
        mailbox.mServerId = serverId;
        mailbox.mSyncKey = backendFolder.getFolderExtraString(EXTRA_SYNC_KEY);
        int syncWindow = getAndPossiblyUpdateSyncWindowForFolder(serverId);
        mailbox.syncWindow = Integer.toString(syncWindow);
        return mailbox;
    }

    private int performSyncOperation(EasOperation operation) throws MessagingException {
        int result = operation.performOperation();
        if (result != EasSyncBase.RESULT_FOLDER_SYNC_REQUIRED) {
            return result;
        }

        if (!folderSync.syncFolders()) {
            return EasOperation.RESULT_OTHER_FAILURE;
        }

        return operation.performOperation();
    }

    public boolean fullyDownloadMessage(String folderServerId, String messageServerId) throws MessagingException {
        Mailbox mailbox = createMailbox(folderServerId);
        List<String> messageServerIds = Collections.singletonList(messageServerId);

        BackendEmailSyncCallback callback = new BackendEmailSyncCallback(mailbox, true);
        EasFetchMail fetchMail = new EasFetchMail(context, account, mailbox, messageServerIds, callback,
                messageBuilderFactory);
        int result = performSyncOperation(fetchMail);

        return result >= EasSyncBase.RESULT_MIN_OK_RESULT;
    }

    public boolean increaseSyncWindow(String serverId) {
        int currentSyncWindow = getAndPossiblyUpdateSyncWindowForFolder(serverId);
        int maximumSyncWindow = getMaximumSyncWindow();
        if (currentSyncWindow == maximumSyncWindow) {
            return false;
        }

        BackendFolder backendFolder = backendStorage.getFolder(serverId);

        int newSyncWindow;
        if (currentSyncWindow < Eas.FILTER_1_MONTH) {
            newSyncWindow = currentSyncWindow + 1;
        } else {
            newSyncWindow = Eas.FILTER_ALL;
            backendFolder.setMoreMessages(MoreMessages.FALSE);
        }

        extraBackendStorage.setSyncWindowForFolder(serverId, newSyncWindow);

        return true;
    }

    private int getAndPossiblyUpdateSyncWindowForFolder(String serverId) {
        int syncWindow = extraBackendStorage.getSyncWindowForFolder(serverId);
        int maximumSyncWindow = getMaximumSyncWindow();
        if (maximumSyncWindow != Eas.FILTER_ALL && syncWindow > maximumSyncWindow) {
            syncWindow = maximumSyncWindow;
            extraBackendStorage.setSyncWindowForFolder(serverId, syncWindow);
        }

        return syncWindow;
    }

    private int getMaximumSyncWindow() {
        return policyManager.getMaxSyncWindow();
    }

    public SyncWindow getSyncWindow(String serverId) {
        int syncWindow = extraBackendStorage.getSyncWindowForFolder(serverId);
        return EasSyncWindow.newInstance(syncWindow);
    }


    class BackendEmailSyncCallback implements EmailSyncCallback {
        private final Mailbox mailbox;
        private final boolean fullyDownload;
        private final BackendFolder backendFolder;
        private boolean syncKeyChanged = false;


        BackendEmailSyncCallback(Mailbox mailbox, boolean fullyDownload) {
            this.mailbox = mailbox;
            this.fullyDownload = fullyDownload;
            this.backendFolder = backendStorage.getFolder(mailbox.mServerId);
        }

        @Override
        public void addMessage(MessageServerData messageServerData) {
            if (fullyDownload && messageServerData.isMessageTruncated()) {
                // We tried to fully download a message but received a truncated message. To avoid trying to download
                // it over and over again we remove it from the local database.
                removeMessage(messageServerData.serverId());
            } else {
                Message legacyMessage = LegacyMessage.createFrom(messageServerData);

                if (messageServerData.isMessageTruncated()) {
                    backendFolder.savePartialMessage(legacyMessage);
                } else {
                    backendFolder.saveCompleteMessage(legacyMessage);
                }
            }
        }

        @Override
        public void removeMessage(String serverId) {
            backendFolder.destroyMessages(Collections.singletonList(serverId));
        }

        @Override
        public void readStateChanged(String serverId, boolean read) {
            backendFolder.setMessageFlag(serverId, Flag.SEEN, read);
        }

        @Override
        public void flagStateChanged(String serverId, boolean flag) {
            backendFolder.setMessageFlag(serverId, Flag.FLAGGED, flag);
        }

        @Override
        public void messageWasRepliedTo(String serverId) {
            backendFolder.setMessageFlag(serverId, Flag.ANSWERED, true);
        }

        @Override
        public void messageWasForwarded(String serverId) {
            backendFolder.setMessageFlag(serverId, Flag.FORWARDED, true);
        }

        @Override
        public void commitMessageChanges() {
            saveSyncKeyIfChanged();
        }

        private void saveSyncKeyIfChanged() {
            if (syncKeyChanged) {
                backendFolder.setFolderExtraString(EXTRA_SYNC_KEY, mailbox.mSyncKey);
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
            extraBackendStorage.removeAllMessages(folderServerId);
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
