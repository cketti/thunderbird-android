package com.fsck.k9.backend.eas.legacy;


import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.data.HeaderField;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.message.ContentTypeHeader;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.mime4j.util.MimeUtil;
import org.jetbrains.annotations.NotNull;


class LegacyPart implements com.fsck.k9.mail.Part {
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
    public String getDisposition() {
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
    public void addHeader(String name, String value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addRawHeader(String name, String raw) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setHeader(String name, String value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NotNull
    @Override
    public String[] getHeader(String name) {
        List<String> values = part.header().values(name);
        if (values.isEmpty()) {
            return new String[0];
        }

        String[] result = new String[values.size()];
        int i = 0;
        for (String value : values) {
            result[i++] = value.trim();
        }

        return result;
    }

    @Override
    public boolean isMimeType(String mimeType) {
        return mimeType.equalsIgnoreCase(getContentType());
    }

    @Override
    public String getMimeType() {
        return (contentTypeHeader != null) ? contentTypeHeader.mimeType() : null;
    }

    @Override
    public void removeHeader(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setBody(Body body) {
        this.body = body;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        part.writeTo(out);
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException {
        part.header().writeTo(out);
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

    public Set<String> getHeaderNames() {
        List<? extends HeaderField> fields = part.header().fields();
        Set<String> names = new HashSet<>(fields.size());
        for (HeaderField field : fields) {
            names.add(field.name());
        }

        return names;
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
}
