package com.fsck.k9.protocol.eas.adapter;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.mail.message.MessageBuilderFactory;
import com.fsck.k9.mail.message.basic.InMemoryMessageBuilderFactory;


class MessageDataToMessage {
    private final MessageBuilderFactory factory;
    private final MessageData messageData;


    static Message buildEmptyMessage(MessageData messageData) {
        MessageBuilderFactory factory = new InMemoryMessageBuilderFactory();
        return new MessageDataToMessage(factory, messageData).buildEmptyMessage();
    }


    private MessageDataToMessage(MessageBuilderFactory factory, MessageData messageData) {
        this.factory = factory;
        this.messageData = messageData;
    }

    private Message buildEmptyMessage() {
        MessageBuilder messageBuilder = factory.createMessageBuilder();
        messageBuilder.header(buildHeader());
        messageBuilder.body(buildEmptyBody());

        return messageBuilder.build();
    }

    private HeaderBuilder buildHeader() {
        HeaderBuilder headerBuilder = factory.createHeaderBuilder();

        if (messageData.getTo() != null) {
            headerBuilder.add("To", Address.toString(messageData.getTo()));
        }

        if (messageData.getFrom() != null) {
            headerBuilder.add("From", Address.toString(messageData.getFrom()));
        }

        if (messageData.getCc() != null) {
            headerBuilder.add("CC",  Address.toString(messageData.getCc()));
        }

        if (messageData.getReplyTo() != null) {
            headerBuilder.add("ReplyTo", Address.toString(messageData.getReplyTo()));
        }

        Date sentDate = new Date(messageData.getTimeStamp());
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        headerBuilder.add("Date", dateFormat.format(sentDate));

        if (messageData.getSubject() != null) {
            headerBuilder.add("Subject", MimeUtility.foldAndEncode(messageData.getSubject()));
        }

        headerBuilder.add("MIME-Version", "1.0");
        headerBuilder.add("Content-Type", "text/html; charset=utf-8");
        return headerBuilder;
    }

    private ContentBodyBuilder buildEmptyBody() {
        ContentBodyBuilder bodyBuilder = factory.createContentBodyBuilder();
        try {
            bodyBuilder.raw(new ByteArrayInputStream(new byte[0]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bodyBuilder;
    }
}
