package com.fsck.k9.mail.store.eas.callback;


import com.fsck.k9.mail.store.eas.adapter.MessageData;


public interface EmailSyncCallback extends BaseSyncCallback {
    void addMessage(MessageData messageData);

    void removeMessage(String serverId);

    void readStateChanged(String serverId, boolean read);

    void flagStateChanged(String serverId, boolean flag);

    void messageWasRepliedTo(String serverId);

    void messageWasForwarded(String serverId);

    void commitMessageChanges();
}
