package com.fsck.k9.protocol.eas;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

import android.content.Context;

import com.fsck.k9.mail.data.Message;
import com.fsck.k9.protocol.eas.CommandStatusException.CommandStatus;
import com.fsck.k9.protocol.eas.adapter.Parser.EmptyStreamException;
import com.fsck.k9.protocol.eas.adapter.SendMailParser;
import com.fsck.k9.protocol.eas.adapter.Serializer;
import com.fsck.k9.protocol.eas.adapter.Serializer.OpaqueWriter;
import com.fsck.k9.protocol.eas.adapter.Tags;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;


public class EasOutboxSync extends EasOperation {
    public static final int RESULT_OK = 1;
    public static final int RESULT_IO_ERROR = -100;
    public static final int RESULT_SEND_FAILED = -102;


    private final Message message;
    private final String messageId;
    private final boolean isEas14OrLater;


    public EasOutboxSync(Context context, Account account, Message message, String messageId) {
        super(context, account);

        this.message = message;
        this.messageId = messageId;

        isEas14OrLater = Eas.isProtocolEas14(mAccount.getProtocolVersion());
    }

    @Override
    protected String getCommand() {
        String cmd = "SendMail";

        if (!isEas14OrLater) {
            cmd += "&SaveInSent=T";
        }

        return cmd;
    }

    @Override
    protected RequestBody getRequestBody() throws IOException, MessageInvalidException {
        if (isEas14OrLater) {
            return new SendMailRequestBody(getRequestContentType(), message);
        } else {
            return new MessageRequestBody(getRequestContentType(), message);
        }
    }

    @Override
    protected int handleResponse(EasResponse response) throws IOException, CommandStatusException {
        if (!isEas14OrLater) {
            return RESULT_OK;
        }

        try {
            SendMailParser parser = new SendMailParser(response.getInputStream(), Tags.COMPOSE_SEND_MAIL);
            parser.parse();

            int status = parser.getStatus();
            if (CommandStatus.isNeedsProvisioning(status)) {
                LogUtils.w(LOG_TAG, "Needs provisioning before sending message: %s", messageId);
                return RESULT_PROVISIONING_ERROR;
            }

            LogUtils.w(LOG_TAG, "General failure sending message: %s", messageId);
            return RESULT_SEND_FAILED;
        } catch (EmptyStreamException e) {
            // This is actually fine; an empty stream means SendMail succeeded
            LogUtils.d(LOG_TAG, "Empty response sending message: %s", messageId);
            return RESULT_OK;
        } catch (IOException e) {
            LogUtils.e(LOG_TAG, e, "IOException sending message: %s", messageId);
            return RESULT_IO_ERROR;
        }
    }

    @Override
    public String getRequestContentType() {
        if (isEas14OrLater) {
            return super.getRequestContentType();
        } else {
            return "message/rfc822";
        }
    }


    private static class MessageRequestBody extends RequestBody {
        private final MediaType contentType;
        protected final Message message;


        public MessageRequestBody(String contentType, Message message) {
            this.contentType = MediaType.parse(contentType);
            this.message = message;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() throws IOException {
            return message.length();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            message.writeTo(sink.outputStream());
        }
    }

    private static class SendMailRequestBody extends RequestBody {
        private final MediaType contentType;
        private final Message message;
        private final String clientId;


        public SendMailRequestBody(String contentType, Message message) {
            this.contentType = MediaType.parse(contentType);
            this.message = message;

            clientId = createClientId();
        }

        private String createClientId() {
            // EAS 14 limits the length to 40 chars
            return "Send" + UUID.randomUUID();
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            try {
                return calculateWbxmlPrefixLength() + message.length();
            } catch (final IOException e) {
                return -1;
            }
        }

        private int calculateWbxmlPrefixLength() throws IOException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            writeTo(byteArrayOutputStream, false);
            return byteArrayOutputStream.size();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            writeTo(sink.outputStream(), true);
        }

        public void writeTo(OutputStream outputStream, boolean withData) throws IOException {
            Serializer serializer = new Serializer(outputStream);
            serializer.start(Tags.COMPOSE_SEND_MAIL);
            serializer.data(Tags.COMPOSE_CLIENT_ID, clientId);
            serializer.tag(Tags.COMPOSE_SAVE_IN_SENT_ITEMS);

            serializer.start(Tags.COMPOSE_MIME);
            if (withData) {
                serializer.opaque(new OpaqueMessageWriter(message), (int) message.length());
            } else {
                serializer.writeOpaqueHeader((int) message.length());
            }

            serializer.end().end().done();
        }
    }

    private static class OpaqueMessageWriter implements OpaqueWriter {
        private final Message message;


        public OpaqueMessageWriter(Message message) {
            this.message = message;
        }

        @Override
        public void writeTo(OutputStream outputStream) throws IOException {
            message.writeTo(outputStream);
        }
    }
}
