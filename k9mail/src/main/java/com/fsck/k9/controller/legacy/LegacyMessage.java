package com.fsck.k9.controller.legacy;


import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.MessageServerData;


public class LegacyMessage extends LegacySimpleMessage {
    private static final Flag[] INTERESTING_FLAGS = { Flag.SEEN, Flag.FLAGGED, Flag.ANSWERED, Flag.FORWARDED };

    LegacyMessage(MessageServerData messageServerData) {
        super(messageServerData.message());

        setUid(messageServerData.serverId());

        for (Flag flag : INTERESTING_FLAGS) {
            if (messageServerData.isFlagSet(flag)) {
                try {
                    setFlag(flag, true);
                } catch (MessagingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static LegacyMessage createFrom(MessageServerData messageServerData) {
        return new LegacyMessage(messageServerData);
    }
}
