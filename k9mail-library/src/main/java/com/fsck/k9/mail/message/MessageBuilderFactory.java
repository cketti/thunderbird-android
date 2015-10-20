package com.fsck.k9.mail.message;


import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;


public interface MessageBuilderFactory {
    MessageBuilder createMessageBuilder();

    HeaderBuilder createHeaderBuilder();

    ContentBodyBuilder createContentBodyBuilder();

    MultipartBuilder createMultipartBuilder();

    PartBuilder createPartBuilder();
}
