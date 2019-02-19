package com.fsck.k9.backend.eas.legacy;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.internet.SizeAware;
import com.fsck.k9.mail.message.FileBackedBody;
import com.fsck.k9.mail.message.TransferEncoding;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;


class LegacyFileBody implements com.fsck.k9.mail.Body, SizeAware {
    Part part;
    FileBackedBody contentBody;


    LegacyFileBody(Part part, FileBackedBody contentBody) {
        this.part = part;
        this.contentBody = contentBody;
    }

    @Override
    public InputStream getInputStream() {
        InputStream decodingInputStream =  TransferEncoding.decode(part);
        return new BinaryTempFileBodyInputStream(decodingInputStream, contentBody.getFile());
    }

    @Override
    public void setEncoding(String encoding) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        Source source = Okio.source(contentBody.raw());
        BufferedSink bufferedSink = Okio.buffer(Okio.sink(out));
        bufferedSink.writeAll(source);
        bufferedSink.flush();
    }

    @Override
    public long getSize() {
        return contentBody.length();
    }
}
