package com.fsck.k9.backend.eas.legacy;


import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.MessageServerData;


public class LegacyMessage extends LegacySimpleMessage {
    private static final Flag[] INTERESTING_FLAGS = { Flag.SEEN, Flag.FLAGGED, Flag.ANSWERED, Flag.FORWARDED };

    LegacyMessage(MessageServerData messageServerData) {
        super(messageServerData.message());

        setUid(messageServerData.serverId());
        initFlags(messageServerData);
    }

    private void initFlags(MessageServerData messageServerData) {
        initInternalFlags(messageServerData);
        initServerFlags(messageServerData);
    }

    private void initInternalFlags(MessageServerData messageServerData) {
        if (messageServerData.isMessageTruncated()) {
            setFlagOrThrow(Flag.X_DOWNLOADED_PARTIAL);
        } else {
            setFlagOrThrow(Flag.X_DOWNLOADED_FULL);
        }
    }

    private void initServerFlags(MessageServerData messageServerData) {
        for (Flag flag : INTERESTING_FLAGS) {
            if (messageServerData.isFlagSet(flag)) {
                setFlagOrThrow(flag);
            }
        }
    }

    private void setFlagOrThrow(Flag flag) {
        try {
            setFlag(flag, true);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    public static LegacyMessage createFrom(MessageServerData messageServerData) {
        return new LegacyMessage(messageServerData);
    }
}
