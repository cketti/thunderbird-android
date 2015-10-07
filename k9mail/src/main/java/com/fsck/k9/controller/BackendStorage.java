package com.fsck.k9.controller;


import com.fsck.k9.Account;
import com.fsck.k9.mail.Folder.FolderClass;
import com.fsck.k9.mailstore.FolderType;
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
    public void createFolder(String name, BackendFolderType backendFolderType, String serverId, String parentServerId) {
        Folder folder = new Folder();
        folder.name = name;
        folder.serverId = serverId;
        folder.parent = mailStore.getFolderIdByServerId(parentServerId);
        folder.visibleLimit = account.getDisplayCount();

        FolderType folderType = convertFromBackendFolderType(backendFolderType);
        folder.type = folderType;

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
    public void deleteFolderByServerId(String serverId) {
        mailStore.deleteFolderByServerId(serverId);
    }

    @Override
    public void deleteAllFolders() {
        mailStore.deleteAllFolders();
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
