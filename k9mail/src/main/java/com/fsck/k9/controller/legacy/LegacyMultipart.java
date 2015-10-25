package com.fsck.k9.controller.legacy;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.message.ContentTypeHeader;


class LegacyMultipart extends com.fsck.k9.mail.Multipart {
    private final Multipart multipart;
    private final ContentTypeHeader contentTypeHeader;


    public LegacyMultipart(Part part) {
        super();
        this.multipart = (Multipart) part.body();
        contentTypeHeader = ContentTypeHeader.parse(part.header().value("Content-Type"));

        for (Part childPart : multipart.children()) {
            LegacyBodyPart legacyBodyPart = new LegacyBodyPart(childPart);
            addBodyPart(legacyBodyPart);
        }
    }

    @Override
    public String getMimeType() {
        return contentTypeHeader.mimeType();
    }

    @Override
    public String getBoundary() {
        return contentTypeHeader.boundary();
    }

    @Override
    public byte[] getPreamble() {
        return multipart.preamble();
    }

    @Override
    public byte[] getEpilogue() {
        return multipart.epilogue();
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public InputStream getInputStream() throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
