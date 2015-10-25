package com.fsck.k9.mail.message.basic;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.util.StreamHelper;


class BasicContentBody implements ContentBody {
    private byte[] raw;


    private BasicContentBody(Builder builder) {
        raw = builder.raw;
    }

    @Override
    public long size() {
        return raw.length;
    }

    @Override
    public InputStream raw() {
        return new ByteArrayInputStream(raw);
    }


    public static class Builder implements ContentBodyBuilder {
        private byte[] raw;


        @Override
        public void raw(InputStream inputStream) throws IOException {
            raw = StreamHelper.readIntoByteArray(inputStream);
        }

        public BasicContentBody build() {
            return new BasicContentBody(this);
        }
    }
}
