package com.fsck.k9.mail.message.basic;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.util.StreamHelper;


class InMemoryContentBody implements ContentBody {
    private byte[] raw;


    InMemoryContentBody(byte[] raw) {
        this.raw = raw;
    }

    private InMemoryContentBody(Builder builder) {
        raw = builder.raw;
    }

    @Override
    public long length() {
        return raw.length;
    }

    @Override
    public InputStream raw() {
        return new ByteArrayInputStream(raw);
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(raw);
    }


    public static class Builder implements ContentBodyBuilder {
        private byte[] raw;


        @Override
        public void raw(InputStream inputStream) throws IOException {
            raw = StreamHelper.readIntoByteArray(inputStream);
        }

        public InMemoryContentBody build() {
            return new InMemoryContentBody(this);
        }
    }
}
