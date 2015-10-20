package com.fsck.k9.mail.message.basic;


import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.builder.MessageBuilder;


class BasicMessage extends BasicPart implements Message {
    private BasicMessage(Builder builder) {
        super(builder);
    }

    public static class Builder extends BasicPart.Builder implements MessageBuilder {
        public BasicMessage build() {
            return new BasicMessage(this);
        }
    }
}
