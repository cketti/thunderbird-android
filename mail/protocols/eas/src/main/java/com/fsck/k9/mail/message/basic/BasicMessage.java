package com.fsck.k9.mail.message.basic;


import java.io.IOException;

import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.filter.CountingOutputStream;


class BasicMessage extends BasicPart implements Message {
    private final long length;

    private BasicMessage(Builder builder) {
        super(builder);
        length = calculateLength();
    }

    @Override
    public long length() {
        return length;
    }

    private long calculateLength() {
        CountingOutputStream countingOutputStream = new CountingOutputStream();
        try {
            writeTo(countingOutputStream);
            return countingOutputStream.getCount();
        } catch (IOException e) {
            return -1;
        }
    }


    public static class Builder extends BasicPart.Builder implements MessageBuilder {
        public BasicMessage build() {
            return new BasicMessage(this);
        }
    }
}
