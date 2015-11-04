package com.fsck.k9.controller.legacy;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Body;
import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.internet.SizeAware;
import com.fsck.k9.mail.message.TransferEncoding;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;


class LegacyBody implements com.fsck.k9.mail.Body, SizeAware {
    Part part;
    ContentBody contentBody;


    LegacyBody(Part part, ContentBody contentBody) {
        this.part = part;
        this.contentBody = contentBody;
    }

    public static com.fsck.k9.mail.Body createFrom(Part part) {
        Body body = part.body();
        if (body instanceof Message) {
            Message message = (Message) body;
            return new LegacySimpleMessage(message);
        } else if (body instanceof Multipart) {
            return new LegacyMultipart(part);
        } else if (body instanceof ContentBody) {
            ContentBody contentBody = (ContentBody) body;
            return new LegacyBody(part, contentBody);
        }

        throw new IllegalArgumentException("Unknown body: " + body.getClass().getSimpleName());
    }

    @Override
    public InputStream getInputStream() throws MessagingException {
        return TransferEncoding.decode(part);
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        Source source = Okio.source(contentBody.raw());
        BufferedSink bufferedSink = Okio.buffer(Okio.sink(out));
        bufferedSink.writeAll(source);
        bufferedSink.flush();
    }

    @Override
    public long getSize() {
        return contentBody.size();
    }
}
