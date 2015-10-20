package com.fsck.k9.mail.message.basic;


import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;
import com.fsck.k9.mail.message.MessageBuilderFactory;


public class BasicMessageBuilderFactory implements MessageBuilderFactory {
    @Override
    public ContentBodyBuilder createContentBodyBuilder() {
        return new BasicContentBody.Builder();
    }

    @Override
    public HeaderBuilder createHeaderBuilder() {
        return new BasicHeader.Builder();
    }

    @Override
    public MessageBuilder createMessageBuilder() {
        return new BasicMessage.Builder();
    }

    @Override
    public MultipartBuilder createMultipartBuilder() {
        return new BasicMultipart.Builder();
    }

    @Override
    public PartBuilder createPartBuilder() {
        return new BasicPart.Builder();
    }
}
