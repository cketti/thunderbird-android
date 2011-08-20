package com.fsck.k9.provider.message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Header;
import com.fsck.k9.message.Part;

public class SimpleBody implements Body {
    private final Part mPart;
    private final String mText;

    public SimpleBody(Part part, String text) {
        mPart = part;
        mText = text;
    }

    @Override
    public InputStream getInputStream(StreamType type) {
        InputStream in = new ByteArrayInputStream(mText.getBytes());

        if (type == StreamType.UNMODIFIED) {
            return in;
        }

        String encoding = MimeUtility.getTransferEncoding(mPart);
        if (encoding == null) {
            encoding = MimeUtility.ENCODING_QUOTED_PRINTABLE;
            mPart.getHeader().add(Header.CONTENT_TRANSFER_ENCODING, encoding);
        }

        return MimeUtility.encodeBody(in, encoding);
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        InputStream in = getInputStream(StreamType.UNMODIFIED);
        IOUtils.copy(in, out);
    }
}
