package com.fsck.k9.controller;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.K9;
import com.fsck.k9.helper.MutableInt;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mailstore.MailStore;
import com.fsck.k9.notification.NotificationController;
import com.fsck.k9.remote.Backend;


class SendMailController {
    private static final boolean SUCCESS = true;
    private static final boolean FAILURE = false;


    private final MessagingController messagingController;
    private final BackendManager backendManager;
    private final NotificationController notificationController;
    private final Map<String, MutableInt> sendCount = new HashMap<String, MutableInt>();


    SendMailController(MessagingController messagingController, BackendManager backendManager,
            NotificationController notificationController) {
        this.messagingController = messagingController;
        this.backendManager = backendManager;
        this.notificationController = notificationController;
    }

    //TODO: improve error reporting
    public void sendPendingMessages(Account account) {
        Backend backend = backendManager.getBackend(account);
        MailStore mailStore = getMailStore(account);

        notifySendPendingMessagesStarted(account);

        List<Long> messageStorageIds = getMessageStorageIdsFromOutbox(account, mailStore);

        int progress = 0;
        int numberOfMessagesToSend = messageStorageIds.size();
        notifySendMailProgress(account, progress, numberOfMessagesToSend);

        boolean success = true;
        for (long messageStorageId : messageStorageIds) {
            success &= sendSingleMessage(account, backend, mailStore, messageStorageId);

            notifySendMailProgress(account, ++progress, numberOfMessagesToSend);
        }

        notifySendPendingMessagesCompleted(account);

        if (success) {
            notificationController.clearSendFailedNotification(account);
        }
    }

    private boolean sendSingleMessage(Account account, Backend backend, MailStore mailStore, long messageStorageId) {
        Message message = mailStore.getMessage(messageStorageId);
        boolean messageNoLongerAvailable = message == null;
        if (messageNoLongerAvailable) {
            return SUCCESS;
        }

        if (isMaxSendAttemptsExceeded(account, messageStorageId, message)) {
            return FAILURE;
        }

        boolean success = sendMessage(backend, message);
        if (!success) {
            return FAILURE;
        }

        if (needToUploadSentMessage(account, backend)) {
            uploadSentMessage(account, mailStore, messageStorageId);
        } else {
            mailStore.removeMessage(messageStorageId);
        }

        return SUCCESS;
    }

    private boolean needToUploadSentMessage(Account account, Backend backend) {
        return backend.supportsUpload() && account.hasSentFolder();
    }

    private void uploadSentMessage(Account account, MailStore mailStore, long messageStorageId) {
        String sentFolder = account.getSentFolderName();
        if (K9.DEBUG) {
            Log.i(K9.LOG_TAG, "Moving sent message to folder '" + sentFolder);
        }

        mailStore.moveMessage(messageStorageId, sentFolder);

        if (K9.DEBUG) {
            Log.i(K9.LOG_TAG, "Moved sent message to folder '" + sentFolder);
        }

        messagingController.uploadMessage(account, messageStorageId);
    }

    private boolean isMaxSendAttemptsExceeded(Account account, long messageStorageId, Message message) {
        String sendCountKey = account.getUuid() + ":" + messageStorageId;

        if (!sendCount.containsKey(sendCountKey)) {
            sendCount.put(sendCountKey, new MutableInt(0));
        }
        MutableInt count = sendCount.get(sendCountKey);

        if (K9.DEBUG) {
            Log.i(K9.LOG_TAG, "Send count for message " + sendCountKey + " is " + count.value);
        }

        if (count.value < K9.MAX_SEND_ATTEMPTS) {
            count.value++;
            return false;
        }

        Log.e(K9.LOG_TAG, "Message " + sendCountKey + " can't be delivered after " + K9.MAX_SEND_ATTEMPTS +
                " attempts. Giving up until the user restarts the device.");

        String subject = message.header().value("Subject");
        notificationController.showSendFailedNotification(account, new MessagingException(subject));

        return true;
    }

    private boolean sendMessage(Backend backend, Message message) {
        try {
            return backend.sendMessage(message);
        } catch (Exception e) {
            Log.e(K9.LOG_TAG, "Error sending message", e);
            return FAILURE;
        }
    }

    private void notifySendPendingMessagesStarted(Account account) {
        for (MessagingListener listener : messagingController.getListeners()) {
            listener.sendPendingMessagesStarted(account);
        }
    }

    private void notifySendMailProgress(Account account, int progress, int numberOfMessagesToSend) {
        String outbox = account.getOutboxFolderName();
        for (MessagingListener listener : messagingController.getListeners()) {
            listener.synchronizeMailboxProgress(account, outbox, progress, numberOfMessagesToSend);
        }
    }

    private void notifySendPendingMessagesCompleted(Account account) {
        for (MessagingListener listener : messagingController.getListeners()) {
            listener.sendPendingMessagesCompleted(account);
        }
    }

    private List<Long> getMessageStorageIdsFromOutbox(Account account, MailStore mailStore) {
        String outboxFolder = account.getOutboxFolderName();
        long folderStorageId = mailStore.getFolderIdByServerId(outboxFolder);
        return mailStore.getMessageStorageIds(folderStorageId);
    }

    private MailStore getMailStore(Account account) {
        BackendStorage storage = backendManager.getBackendStorage(account);
        return storage.getMailStore();
    }
}
