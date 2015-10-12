package com.fsck.k9.mail.data;


import com.fsck.k9.mail.Flag;


public interface MessageServerData {
    String serverId();

    long timeStamp();

    boolean isFlagSet(Flag flag);

    Message message();

    String getServerIdForPart(Part part);
}
