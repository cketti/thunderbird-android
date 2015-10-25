package com.fsck.k9.controller.legacy;


import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.HeaderField;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.message.ContentTypeHeader;
import okio.BufferedSink;
import okio.Okio;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.util.MimeUtil;


class LegacyPart implements com.fsck.k9.mail.Part {
    private static final byte[] CRLF = { '\r', '\n' };


    private final Part part;
    private final ContentTypeHeader contentTypeHeader;
    private Body body;
    private String contentType;


    LegacyPart(Part part) {
        this.part = part;
        String contentType = part.header().value("Content-Type");
        contentTypeHeader = (contentType != null) ? ContentTypeHeader.parse(contentType) : null;
    }

    @Override
    public Body getBody() {
        if (body == null) {
            body = LegacyBody.createFrom(part);
        }

        return body;
    }

    @Override
    public String getContentType() {
        if (contentType == null) {
            String value = header("Content-Type");
            contentType = (value == null) ? "text/plain" : value;
        }
        return contentType;
    }

    @Override
    public String getDisposition() throws MessagingException {
        return header("Content-Disposition");
    }

    @Override
    public String getContentId() {
        String contentId = part.header().value(MimeHeader.HEADER_CONTENT_ID);
        if (contentId == null) {
            return null;
        }

        int first = contentId.indexOf('<');
        int last = contentId.lastIndexOf('>');

        return (first != -1 && last != -1) ?
                contentId.substring(first + 1, last) :
                contentId;
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addRawHeader(String name, String raw) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        List<String> values = part.header().values(name);
        String[] result = new String[values.size()];
        int i = 0;
        for (String value : values) {
            result[i++] = value.trim();
        }

        return result;
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getMimeType() {
        return (contentTypeHeader != null) ? contentTypeHeader.mimeType() : null;
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setBody(Body body) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException, MessagingException {
        BufferedSink bufferedSink = Okio.buffer(Okio.sink(out));
        for (HeaderField field : part.header().fields()) {
            if (field.hasRawData()) {
                bufferedSink.writeUtf8(field.raw());
            } else {
                bufferedSink.writeUtf8(field.name());
                bufferedSink.writeUtf8(": ");
                bufferedSink.writeUtf8(field.value());
            }
            bufferedSink.write(CRLF);
        }
        bufferedSink.flush();
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getServerExtra() {
        return null;
    }

    @Override
    public void setServerExtra(String serverExtra) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public com.fsck.k9.mail.Message clone() {
        throw new UnsupportedOperationException("Not implemented");
    }

    String header(String name) {
        String value = part.header().value(name);
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    static String unfoldAndDecode(String value) {
        if (value == null) {
            return null;
        }

        String unfolded = MimeUtil.unfold(value);
        return DecoderUtil.decodeEncodedWords(unfolded, DecodeMonitor.SILENT);
    }

    static String unfold(String value) {
        if (value == null) {
            return null;
        }

        return MimeUtil.unfold(value);
    }
}
