package com.fsck.k9.mailstore;


import com.fsck.k9.Account;


public enum FolderType {
    REGULAR {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            // Do nothing
        }
    },
    ARCHIVE {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setArchiveFolderName(serverId);
        }
    },
    DRAFTS {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setDraftsFolderName(serverId);
        }
    },
    INBOX {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setInboxFolderName(serverId);
        }
    },
    OUTBOX {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            // Outbox is always local
        }
    },
    SENT {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setSentFolderName(serverId);
        }
    },
    SPAM {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setSpamFolderName(serverId);
        }
    },
    TRASH {
        @Override
        public void setSpecialFolder(Account account, String serverId) {
            account.setTrashFolderName(serverId);
        }
    };

    public boolean isSpecialFolder() {
        return this != REGULAR;
    }

    public abstract void setSpecialFolder(Account account, String serverId);
}
