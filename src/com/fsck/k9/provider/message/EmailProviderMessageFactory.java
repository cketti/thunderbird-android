package com.fsck.k9.provider.message;

import android.content.Context;

import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Message;
import com.fsck.k9.message.MessageFactory;
import com.fsck.k9.message.Multipart;
import com.fsck.k9.message.Part;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.field.Field;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeEntityConfig;
import org.apache.james.mime4j.stream.RawField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;

public class EmailProviderMessageFactory implements MessageFactory {
    private final Context mContext;
    private final String mAccountUuid;

    public EmailProviderMessageFactory(Context context, String accountUuid) {
        mContext = context;
        mAccountUuid = accountUuid;
    }

    @Override
    public Body createBody(Part part, InputStream in) {
        return new TempFileBody(mContext, mAccountUuid, part, in);
    }

    @Override
    public Body createBody(Part part, String text) {
        return new SimpleBody(part, text);
    }

    @Override
    public EmailProviderMetadata createMetadata() {
        return new EmailProviderMetadata(mAccountUuid);
    }

    @Override
    public Message createMessage() {
        return new EmailProviderMessage();
    }

    @Override
    public Message createMessage(InputStream in) {
        Message message = createMessage();

        MimeEntityConfig parserConfig  = new MimeEntityConfig();
        parserConfig.setMaxHeaderLen(-1); // The default is a mere 10k
        parserConfig.setMaxLineLen(-1); // The default is 1000 characters. Some MUAs generate
        // REALLY long References: headers
        MimeStreamParser parser = new MimeStreamParser(parserConfig);
        ContentHandler handler = new EmailProviderContentHandler(this, message);
        parser.setContentHandler(handler);
        try {
            //parser.parse(new EOLConvertingInputStream(in));
            parser.parse(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return message;
    }

    @Override
    public Multipart createMultipart() {
        return new EmailProviderMultipart();
    }

    @Override
    public Part createPart() {
        return new EmailProviderBodyPart();
    }


    static class EmailProviderContentHandler implements ContentHandler {

        private final MessageFactory mFactory;
        private final Message mMessage;
        private final Stack<Object> stack = new Stack<Object>();

        EmailProviderContentHandler(MessageFactory factory, Message message) {
            mFactory = factory;
            mMessage = message;
        }

        private void expect(Class<?> c) {
            if (!c.isInstance(stack.peek())) {
                throw new IllegalStateException("Internal stack error: " + "Expected '"
                                                + c.getName() + "' found '" + stack.peek().getClass().getName() + "'");
            }
        }

        @Override
        public void startMessage() throws MimeException {
            if (stack.isEmpty()) {
                stack.push(mMessage);
            } else {
                expect(Part.class);
                Part part = (Part) stack.peek();
                Message message = mFactory.createMessage();
                part.setBody(message);
                stack.push(message);
            }
        }

        @Override
        public void endMessage() throws MimeException {
            expect(Message.class);
            stack.pop();
        }

        @Override
        public void startHeader() throws MimeException {
            expect(Part.class);
        }

        @Override
        public void field(RawField field) throws MimeException {
            expect(Part.class);
            try {
                Part part = (Part) stack.peek();
                Field parsedField = DefaultFieldParser.parse(field.getRaw(), null);
                part.getHeader().addEncoded(parsedField.getName(), parsedField.getBody());
            } catch (MimeException me) {
                throw new Error(me);
            }
        }

        @Override
        public void endHeader() throws MimeException {
            expect(Part.class);
        }

        @Override
        public void startMultipart(BodyDescriptor bd) throws MimeException {
            expect(Part.class);
            Part part = (Part) stack.peek();
            Multipart multipart = mFactory.createMultipart();
            part.setBody(multipart);
            stack.push(multipart);
        }

        @Override
        public void preamble(InputStream in) throws MimeException, IOException {
            expect(Multipart.class);
            Multipart multipart = (Multipart) stack.peek();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copy(in, out);
            out.close();
            multipart.setPreamble(out.toByteArray());
        }

        @Override
        public void startBodyPart() throws MimeException {
            expect(Multipart.class);
            Multipart multipart = (Multipart) stack.peek();
            Part bodyPart = mFactory.createPart();
            multipart.addPart(bodyPart);
            stack.push(bodyPart);
        }

        @Override
        public void endBodyPart() throws MimeException {
            expect(Part.class);
            stack.pop();
        }

        @Override
        public void epilogue(InputStream in) throws MimeException, IOException {
            expect(Multipart.class);
            Multipart multipart = (Multipart) stack.peek();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copy(in, out);
            out.close();
            multipart.setEpilogue(out.toByteArray());
        }

        @Override
        public void endMultipart() throws MimeException {
            expect(Multipart.class);
            Multipart multipart = (Multipart) stack.pop();
            Part part = (Part) stack.peek();
            String boundary = MimeUtility.getBoundary(part);
            multipart.setBoundary(boundary);
        }

        @Override
        public void body(BodyDescriptor bd, InputStream in) throws MimeException, IOException {
            expect(Part.class);
            Part part = (Part) stack.peek();
            Body body = mFactory.createBody(part, in);
            part.setBody(body);
        }

        @Override
        public void raw(InputStream arg0) throws MimeException, IOException {
            throw new UnsupportedOperationException("Not supported");
        }
    }
}