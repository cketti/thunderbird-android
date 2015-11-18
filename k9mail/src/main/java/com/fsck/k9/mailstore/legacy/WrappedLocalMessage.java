package com.fsck.k9.mailstore.legacy;


import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Body;
import com.fsck.k9.mail.data.Header;
import com.fsck.k9.mail.data.HeaderField;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.filter.EOLConvertingOutputStream;
import com.fsck.k9.mailstore.LocalMessage;


public class WrappedLocalMessage implements Message {
    private static final long UNKNOWN_LENGTH = -1L;


    private final LocalMessage message;
    private long length = UNKNOWN_LENGTH;
    private LocalMessageHeader header;


    public WrappedLocalMessage(LocalMessage message) {
        this.message = message;
    }

    @Override
    public long length() {
        if (length == UNKNOWN_LENGTH) {
            length = message.calculateSize();
        }

        return length;
    }

    @Override
    public Header header() {
        if (header == null) {
            header = new LocalMessageHeader();
        }

        return header;
    }

    @Override
    public Body body() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        try {
            message.writeTo(new EOLConvertingOutputStream(outputStream));
        } catch (MessagingException e) {
            throw new IOException(e);
        }
    }


    public class LocalMessageHeader implements Header {
        @Override
        public int size() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public List<? extends HeaderField> fields() {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public String value(String name) {
            try {
                String[] headers = message.getHeader(name);
                return headers != null && headers.length > 1 ? headers[0] : null;
            } catch (MessagingException e) {
                return null;
            }
        }

        @Override
        public List<String> values(String name) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public void writeTo(OutputStream outputStream) throws IOException {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}
