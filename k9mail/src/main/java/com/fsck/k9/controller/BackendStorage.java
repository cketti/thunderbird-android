package com.fsck.k9.controller;


import java.util.Collections;

import com.fsck.k9.Account;
import com.fsck.k9.controller.legacy.LegacyMessage;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder.FolderClass;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mailstore.FolderType;
import com.fsck.k9.mailstore.LocalFolder;
import com.fsck.k9.mailstore.LocalMessage;
import com.fsck.k9.mailstore.LocalStore;
import com.fsck.k9.mailstore.MailStore;
import com.fsck.k9.mailstore.data.Folder;
import com.fsck.k9.remote.BackendFolderType;


class BackendStorage implements com.fsck.k9.remote.BackendStorage {
    private final Account account;
    private final MailStore mailStore;

    BackendStorage(Account account, MailStore mailStore) {
        this.account = account;
        this.mailStore = mailStore;
    }

    @Override
    public String getFoldersSyncKey() {
        return mailStore.getFoldersSyncKey();
    }

    @Override
    public void setFoldersSyncKey(String syncKey) {
        mailStore.setFoldersSyncKey(syncKey);
    }

    @Override
    public String getPolicyKey() {
        return mailStore.getPolicyKey();
    }

    @Override
    public void setPolicyKey(String policyKey) {
        mailStore.setPolicyKey(policyKey);
    }

    @Override
    public String getSyncKeyForFolder(String serverId) {
        return mailStore.getSyncKeyForFolder(serverId);
    }

    @Override
    public void setSyncKeyForFolder(String serverId, String syncKey) {
        mailStore.setSyncKeyForFolder(serverId, syncKey);
    }

    @Override
    public void createFolder(String name, BackendFolderType backendFolderType, String serverId, String parentServerId) {
        Folder folder = new Folder();
        folder.name = name;
        folder.serverId = serverId;
        folder.parent = mailStore.getFolderIdByServerId(parentServerId);
        folder.visibleLimit = account.getDisplayCount();

        FolderType folderType = convertFromBackendFolderType(backendFolderType);
        folder.type = folderType;
        folderType.setSpecialFolder(account, serverId);

        boolean integrate = false;
        boolean inTopGroup = false;
        FolderClass displayClass = FolderClass.NO_CLASS;
        FolderClass syncClass = FolderClass.NO_CLASS;
        FolderClass notifyClass = FolderClass.INHERITED;
        FolderClass pushClass = FolderClass.INHERITED;
        if (folderType.isSpecialFolder()) {
            inTopGroup = true;
            displayClass = FolderClass.FIRST_CLASS;

            if (folderType == FolderType.INBOX) {
                integrate = true;
                notifyClass = FolderClass.FIRST_CLASS;
                pushClass = FolderClass.FIRST_CLASS;
                syncClass = FolderClass.FIRST_CLASS;
            } else if (folderType == FolderType.DRAFTS) {
                syncClass = FolderClass.FIRST_CLASS;
            }
        }

        folder.integrate = integrate;
        folder.inTopGroup = inTopGroup;
        folder.displayClass = displayClass;
        folder.syncClass = syncClass;
        folder.notifyClass = notifyClass;
        folder.pushClass = pushClass;

        mailStore.createFolder(folder);
    }

    @Override
    public void changeFolder(String serverId, String name, String parentServerId) {
        mailStore.changeFolder(serverId, name, parentServerId);
    }

    @Override
    public void deleteFolderByServerId(String serverId) {
        mailStore.deleteFolderByServerId(serverId);
    }

    @Override
    public void deleteAllFolders() {
        mailStore.deleteAllFolders();
    }

    @Override
    public void createMessage(MessageServerData messageServerData) {
        //TODO: Move code to store messages from LocalStore/LocalFolder to MailStore
        try {
            Message messageToSave = LegacyMessage.createFrom(messageServerData);

            LocalStore localStore = account.getLocalStore();
            LocalFolder localFolder = localStore.getFolderByServerId(messageServerData.folderServerId());
            localFolder.open(LocalFolder.OPEN_MODE_RW);
            localFolder.appendMessages(Collections.singletonList(messageToSave));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeMessage(String folderServerId, String messageServerId) {
        try {
            LocalStore localStore = account.getLocalStore();
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            LocalMessage message = localFolder.getMessage(messageServerId);
            message.destroy();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMessageFlag(String folderServerId, String messageServerId, Flag flag, boolean state) {
        try {
            LocalStore localStore = account.getLocalStore();
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            LocalMessage message = localFolder.getMessage(messageServerId);
            message.setFlag(flag, state);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAllMessages(String folderServerId) {
        try {
            LocalStore localStore = account.getLocalStore();
            LocalFolder localFolder = localStore.getFolderByServerId(folderServerId);

            localFolder.open(LocalFolder.OPEN_MODE_RW);
            localFolder.clearAllMessages();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    private FolderType convertFromBackendFolderType(BackendFolderType backendFolderType) {
        switch (backendFolderType) {
            case REGULAR:
                return FolderType.REGULAR;
            case ARCHIVE:
                return FolderType.ARCHIVE;
            case DRAFTS:
                return FolderType.DRAFTS;
            case INBOX:
                return FolderType.INBOX;
            case OUTBOX:
                return FolderType.OUTBOX;
            case SENT:
                return FolderType.SENT;
            case SPAM:
                return FolderType.SPAM;
            case TRASH:
                return FolderType.TRASH;
        }

        throw new AssertionError("Unknown folder type: " + backendFolderType.name());
    }
}
