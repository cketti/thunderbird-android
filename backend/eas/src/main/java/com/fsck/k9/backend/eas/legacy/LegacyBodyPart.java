package com.fsck.k9.backend.eas.legacy;


import java.io.IOException;
import java.io.OutputStream;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.data.Part;
import org.jetbrains.annotations.NotNull;


class LegacyBodyPart extends BodyPart {
    private final LegacyPart legacyPart;


    LegacyBodyPart(Part part) {
        legacyPart = new LegacyPart(part);
    }

    @Override
    public void addHeader(String name, String value) {
        legacyPart.addHeader(name, value);
    }

    @Override
    public void addRawHeader(String name, String raw) {
        legacyPart.addRawHeader(name, raw);
    }

    @Override
    public void removeHeader(String name) {
        legacyPart.removeHeader(name);
    }

    @Override
    public void setHeader(String name, String value) {
        legacyPart.setHeader(name, value);
    }

    @Override
    public Body getBody() {
        return legacyPart.getBody();
    }

    @Override
    public String getContentType() {
        return legacyPart.getContentType();
    }

    @Override
    public String getDisposition() {
        return legacyPart.getDisposition();
    }

    @Override
    public String getContentId() {
        return legacyPart.getContentId();
    }

    @NotNull
    @Override
    public String[] getHeader(String name) {
        return legacyPart.getHeader(name);
    }

    @Override
    public boolean isMimeType(String mimeType) {
        return legacyPart.isMimeType(mimeType);
    }

    @Override
    public String getMimeType() {
        return legacyPart.getMimeType();
    }

    @Override
    public void setBody(Body body) {
        legacyPart.setBody(body);
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        legacyPart.writeTo(out);
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException {
        legacyPart.writeHeaderTo(out);
    }

    @Override
    public void setEncoding(String encoding) {
        throw new UnsupportedOperationException("not implemented");
    }
}
