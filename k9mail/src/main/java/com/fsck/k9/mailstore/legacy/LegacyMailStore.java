package com.fsck.k9.mailstore.legacy;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fsck.k9.activity.MessageReference;
import com.fsck.k9.controller.legacy.LegacyMessage;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;


//TODO: Re-implement this functionality in MailStore without using LocalStore/LocalFolder
public class LegacyMailStore {
    private final LocalStore localStore;


    public LegacyMailStore(LocalStore localStore) {
        this.localStore = localStore;
    }

    public List<Long> getMessageStorageIds(long folderStorageId) {
        try {
            LocalFolder localFolder = localStore.getFolderById(folderStorageId);
            localFolder.open(LocalFolder.OPEN_MODE_RO);
            List<LocalMessage> messages = localFolder.getMessages(null);

            List<Long> messageStorageIds = new ArrayList<Long>(messages.size());
            for (LocalMessage message : messages) {
                messageStorageIds.add(message.getId());
            }

            return messageStorageIds;
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public Message getMessage(long messageStorageId) {
        try {
            MessageReference messageReference = localStore.getMessageReference(messageStorageId);
            if (messageReference == null) {
                return null;
            }

            String folderName = messageReference.getFolderName();
            String uid = messageReference.getUid();

            LocalFolder localFolder = localStore.getFolder(folderName);
            LocalMessage message = localFolder.getMessage(uid);

            FetchProfile fetchProfile = new FetchProfile();
            fetchProfile.add(FetchProfile.Item.BODY);
            localFolder.fetch(Collections.singletonList(message), fetchProfile, null);

            return new WrappedLocalMessage(message);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeMessage(long messageStorageId) {
        try {
            MessageReference messageReference = localStore.getMessageReference(messageStorageId);
            if (messageReference == null) {
                return;
            }

            String folderName = messageReference.getFolderName();
            String uid = messageReference.getUid();

            LocalFolder localFolder = localStore.getFolder(folderName);
            LocalMessage message = localFolder.getMessage(uid);

            message.destroy();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void moveMessage(long messageStorageId, String folderServerId) {
        try {
            MessageReference messageReference = localStore.getMessageReference(messageStorageId);
            if (messageReference == null) {
                return;
            }

            String sourceFolderName = messageReference.getFolderName();
            String uid = messageReference.getUid();

            LocalFolder sourceFolder = localStore.getFolder(sourceFolderName);
            LocalMessage message = sourceFolder.getMessage(uid);

            LocalFolder destinationFolder = localStore.getFolderByServerId(folderServerId);

            sourceFolder.moveMessages(Collections.singletonList(message), destinationFolder);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void createMessage(MessageServerData messageServerData) {
        try {
            com.fsck.k9.mail.Message messageToSave = LegacyMessage.createFrom(messageServerData);

            LocalFolder localFolder = localStore.getFolderByServerId(messageServerData.folderServerId());
            localFolder.open(LocalFolder.OPEN_MODE_RW);
            localFolder.appendMessages(Collections.singletonList(messageToSave));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeMessage(String folderServerId, String messageServerId) {
        try {
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            LocalMessage message = localFolder.getMessage(messageServerId);
            message.destroy();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void setMessageFlag(String folderServerId, String messageServerId, Flag flag, boolean state) {
        try {
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            LocalMessage message = localFolder.getMessage(messageServerId);
            message.setFlag(flag, state);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeAllMessages(String folderServerId) {
        try {
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            localFolder.clearAllMessages();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
