package com.fsck.k9.mail.message.basic;


import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;
import com.fsck.k9.mail.helper.FileFactory;
import com.fsck.k9.mail.message.MessageBuilderFactory;


public class FileBackedMessageBuilderFactory implements MessageBuilderFactory {
    private final FileFactory fileFactory;


    public FileBackedMessageBuilderFactory(FileFactory fileFactory) {
        this.fileFactory = fileFactory;
    }

    @Override
    public ContentBodyBuilder createContentBodyBuilder() {
        return new FlexibleContentBodyBuilder(fileFactory);
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
