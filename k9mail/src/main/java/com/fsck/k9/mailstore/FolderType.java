package com.fsck.k9.mailstore;


public enum FolderType {
    REGULAR,
    ARCHIVE,
    DRAFTS,
    INBOX,
    OUTBOX,
    SENT,
    SPAM,
    TRASH;

    public boolean isSpecialFolder() {
        return this != REGULAR;
    }
}
