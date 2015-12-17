package com.fsck.k9.controller;


import com.fsck.k9.Account;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder.FolderClass;
import com.fsck.k9.mail.data.MessageServerData;
import com.fsck.k9.mailstore.FolderType;
import com.fsck.k9.mailstore.MailStore;
import com.fsck.k9.mailstore.data.Folder;
import com.fsck.k9.remote.BackendFolderType;


class BackendStorage implements com.fsck.k9.remote.BackendStorage {
    private final Account account;
    private final MailStore mailStore;
    private final MessagingController controller;


    BackendStorage(Account account, MailStore mailStore, MessagingController controller) {
        this.account = account;
        this.mailStore = mailStore;
        this.controller = controller;
    }

    public MailStore getMailStore() {
        return mailStore;
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
    public String getDeviceId() {
        return mailStore.getDeviceId();
    }

    @Override
    public void setDeviceId(String deviceId) {
        mailStore.setDeviceId(deviceId);
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
        mailStore.createMessage(messageServerData);

        String folderServerId = messageServerData.folderServerId();
        String messageServerId = messageServerData.serverId();
        controller.notifyForMessageIfNecessary(account, folderServerId, messageServerId);
    }

    @Override
    public void removeMessage(String folderServerId, String messageServerId) {
        mailStore.removeMessage(folderServerId, messageServerId);
    }

    @Override
    public void setMessageFlag(String folderServerId, String messageServerId, Flag flag, boolean state) {
        mailStore.setMessageFlag(folderServerId, messageServerId, flag, state);
    }

    @Override
    public void removeAllMessages(String folderServerId) {
        mailStore.removeAllMessages(folderServerId);
    }

    @Override
    public int getSyncWindowForFolder(String serverId) {
        return mailStore.getSyncWindowForFolder(serverId);
    }

    @Override
    public void setSyncWindowForFolder(String serverId, int syncWindow) {
        mailStore.setSyncWindowForFolder(serverId, syncWindow);
    }

    @Override
    public void setMoreMessagesForFolder(String serverId, boolean moreMessages) {
        mailStore.setMoreMessagesForFolder(serverId, moreMessages);
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
