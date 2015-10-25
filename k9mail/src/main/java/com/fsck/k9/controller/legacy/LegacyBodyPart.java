package com.fsck.k9.controller.legacy;


import java.io.IOException;
import java.io.OutputStream;

import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Part;


class LegacyBodyPart extends BodyPart {
    private final LegacyPart legacyPart;


    LegacyBodyPart(Part part) {
        legacyPart = new LegacyPart(part);
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        legacyPart.addHeader(name, value);
    }

    @Override
    public void addRawHeader(String name, String raw) throws MessagingException {
        legacyPart.addRawHeader(name, raw);
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        legacyPart.removeHeader(name);
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
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
    public String getDisposition() throws MessagingException {
        return legacyPart.getDisposition();
    }

    @Override
    public String getContentId() {
        return legacyPart.getContentId();
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        return legacyPart.getHeader(name);
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
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
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        legacyPart.writeTo(out);
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException, MessagingException {
        legacyPart.writeHeaderTo(out);
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        legacyPart.setUsing7bitTransport();
    }
}
