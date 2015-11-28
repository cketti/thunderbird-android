package com.fsck.k9.mail.store.eas;


import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


public class MessageMove {
    private final String serverId;
    private final String sourceFolderId;
    private final String destinationFolderId;


    private MessageMove(Builder builder) {
        serverId = checkNotNull(builder.serverId, "serverId must not be null");
        sourceFolderId = checkNotNull(builder.sourceFolderId, "sourceFolderId must not be null");
        destinationFolderId = checkNotNull(builder.destinationFolderId, "destinationFolderId must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String serverId() {
        return serverId;
    }

    public String sourceFolderId() {
        return sourceFolderId;
    }

    public String destinationFolderId() {
        return destinationFolderId;
    }


    public static class Builder {
        private String serverId;
        private String sourceFolderId;
        private String destinationFolderId;

        private Builder() {}

        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public Builder sourceFolderId(String sourceFolderId) {
            this.sourceFolderId = sourceFolderId;
            return this;
        }

        public Builder destinationFolderId(String destinationFolderId) {
            this.destinationFolderId = destinationFolderId;
            return this;
        }

        public MessageMove build() {
            return new MessageMove(this);
        }
    }
}
