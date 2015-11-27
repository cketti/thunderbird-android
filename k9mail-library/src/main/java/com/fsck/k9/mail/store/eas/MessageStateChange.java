package com.fsck.k9.mail.store.eas;


import java.util.HashMap;
import java.util.Map;

import com.fsck.k9.mail.Flag;

import static com.fsck.k9.mail.util.Preconditions.checkNotEmpty;
import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


public class MessageStateChange {
    private final String serverId;
    private final Map<Flag, Boolean> changes;


    private MessageStateChange(Builder builder) {
        this.serverId = checkNotNull(builder.serverId, "serverId must not be null");
        this.changes = checkNotEmpty(builder.changes, "Must contain flag changes");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getServerId() {
        return serverId;
    }

    public boolean hasFlagChanged(Flag flag) {
        return changes.containsKey(flag);
    }

    public boolean getFlagState(Flag flag) {
        Boolean newState = changes.get(flag);
        if (newState == null) {
            throw new IllegalArgumentException("Flag has not been changed: " + flag);
        }

        return newState;
    }

    public boolean isSingleChange(Flag flag) {
        return hasFlagChanged(flag) && changes.size() == 1;
    }


    public static class Builder {
        private String serverId;
        private final Map<Flag, Boolean> changes = new HashMap<Flag, Boolean>();

        private Builder() {}

        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public Builder setFlag(Flag flag, boolean newState) {
            checkNotNull(flag, "Argument flag must not be null");
            changes.put(flag, newState);
            return this;
        }

        public MessageStateChange build() {
            return new MessageStateChange(this);
        }
    }
}
