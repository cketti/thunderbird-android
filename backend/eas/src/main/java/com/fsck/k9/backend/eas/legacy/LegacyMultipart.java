package com.fsck.k9.backend.eas.legacy;


import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.message.ContentTypeHeader;


class LegacyMultipart extends com.fsck.k9.mail.Multipart {
    private final ContentTypeHeader contentTypeHeader;


    public LegacyMultipart(Part part) {
        super();

        contentTypeHeader = ContentTypeHeader.parse(part.header().value("Content-Type"));

        Multipart multipart = (Multipart) part.body();
        for (Part childPart : multipart.children()) {
            LegacyBodyPart legacyBodyPart = new LegacyBodyPart(childPart);
            addBodyPart(legacyBodyPart);
        }
    }

    @Override
    public InputStream getInputStream() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeTo(OutputStream out) {
        throw new UnsupportedOperationException("Not implemented");
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
        return new byte[0];
    }

    @Override
    public byte[] getEpilogue() {
        return new byte[0];
    }
}
