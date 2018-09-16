package com.fsck.k9.mail.message;


import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.MessageBuilder;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;
import com.fsck.k9.mail.util.StreamHelper;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;


public class MessageParser {
    private MessageParser() {
    }

    public static Message parse(InputStream inputStream, MessageBuilderFactory factory) throws IOException {
        MessageParser parser = new MessageParser();
        return parser.parseInternal(inputStream, factory);
    }

    private Message parseInternal(InputStream inputStream, MessageBuilderFactory factory) throws IOException {
        MimeStreamParser mimeStreamParser = getMimeStreamParser();
        MessageContentHandler messageContentHandler = new MessageContentHandler(factory);
        mimeStreamParser.setContentHandler(messageContentHandler);

        try {
            mimeStreamParser.parse(inputStream);
        } catch (MimeException e) {
            throw new MessageParserException(e);
        }

        return messageContentHandler.buildMessage();
    }

    private MimeStreamParser getMimeStreamParser() {
        MimeConfig parserConfig = new MimeConfig.Builder()
                .setMaxHeaderLen(-1)
                .setMaxLineLen(-1)
                .setMaxHeaderCount(-1)
                .build();

        return new MimeStreamParser(parserConfig);
    }


    private class MessageContentHandler implements ContentHandler {
        private final LinkedList<Object> stack = new LinkedList<>();
        private final MessageBuilderFactory factory;
        private MessageBuilder messageBuilder;

        public MessageContentHandler(MessageBuilderFactory factory) {
            this.factory = factory;
            messageBuilder = factory.createMessageBuilder();
        }

        @Override
        public void startMessage() {
            if (stack.isEmpty()) {
                stack.push(messageBuilder);
            } else {
                PartBuilder partBuilder = (PartBuilder) stack.peek();

                MessageBuilder innerMessageBuilder = factory.createMessageBuilder();
                partBuilder.body(innerMessageBuilder);

                stack.push(innerMessageBuilder);
            }
        }

        @Override
        public void endMessage() {
            stack.pop();
        }

        @Override
        public void startHeader() {
            PartBuilder partBuilder = (PartBuilder) stack.peek();

            HeaderBuilder headerBuilder = factory.createHeaderBuilder();
            partBuilder.header(headerBuilder);
            stack.push(headerBuilder);
        }

        @Override
        public void field(Field parsedField) throws MimeException {
            HeaderBuilder headerBuilder = (HeaderBuilder) stack.peek();

            String name = parsedField.getName();
            String raw = parsedField.getRaw().toString();
            headerBuilder.addRaw(name, raw);
        }

        @Override
        public void endHeader() {
            stack.pop();
        }

        @Override
        public void startMultipart(BodyDescriptor bodyDescriptor) {
            PartBuilder partBuilder = (PartBuilder) stack.peek();

            MultipartBuilder multipartBuilder = factory.createMultipartBuilder();
            multipartBuilder.boundary(bodyDescriptor.getBoundary());

            partBuilder.body(multipartBuilder);
            stack.addFirst(multipartBuilder);
        }

        @Override
        public void preamble(InputStream inputStream) throws IOException {
            MultipartBuilder multipartBuilder = (MultipartBuilder) stack.peek();

            byte[] preamble = StreamHelper.readIntoByteArray(inputStream);
            multipartBuilder.preamble(preamble);
        }

        @Override
        public void startBodyPart() {
            MultipartBuilder multipartBuilder = (MultipartBuilder) stack.peek();

            PartBuilder partBuilder = factory.createPartBuilder();
            multipartBuilder.add(partBuilder);
            stack.addFirst(partBuilder);
        }

        @Override
        public void endBodyPart() {
            stack.pop();
        }

        @Override
        public void epilogue(InputStream is) throws IOException {
            MultipartBuilder multipartBuilder = (MultipartBuilder) stack.peek();

            byte[] epilogue = StreamHelper.readIntoByteArray(is);
            multipartBuilder.epilogue(epilogue);
        }

        @Override
        public void endMultipart() {
            stack.pop();
        }

        @Override
        public void body(BodyDescriptor bodyDescriptor, InputStream inputStream) throws IOException {
            PartBuilder partBuilder = (PartBuilder) stack.peek();

            ContentBodyBuilder bodyBuilder = factory.createContentBodyBuilder();
            bodyBuilder.raw(inputStream);
            partBuilder.body(bodyBuilder);
        }

        @Override
        public void raw(InputStream is) throws IOException {
            throw new UnsupportedOperationException();
        }

        public Message buildMessage() {
            return messageBuilder.build();
        }
    }
}
