package com.fsck.k9.mail.message.basic;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.mail.data.ContentBody;
import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.helper.FileFactory;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;


class FlexibleContentBodyBuilder implements ContentBodyBuilder {
    private static final int IN_MEMORY_THRESHOLD = 100 * 1024;  // 100 KiB


    private final FileFactory fileFactory;

    private byte[] raw;
    private File file;
    private long fileSize;


    public FlexibleContentBodyBuilder(FileFactory fileFactory) {
        this.fileFactory = fileFactory;
    }

    @Override
    public void raw(InputStream inputStream) throws IOException {
        if (file != null || raw != null) {
            throw new IllegalStateException("raw() called more than once");
        }

        Buffer buffer = new Buffer();
        Source source = Okio.source(inputStream);
        long remaining = IN_MEMORY_THRESHOLD;
        boolean writeToFile = true;
        while (remaining > 0) {
            long read = source.read(buffer, remaining);
            if (read == -1) {
                writeToFile = false;
                break;
            }
            remaining -= read;
        }

        if (writeToFile) {
            file = fileFactory.createFile();
            BufferedSink fileSink = Okio.buffer(Okio.sink(file));
            try {
                fileSize = fileSink.writeAll(buffer);
                fileSize += fileSink.writeAll(source);
            } finally {
                fileSink.close();
            }
        } else {
            raw = buffer.readByteArray();
        }
    }

    @Override
    public ContentBody build() {
        if (raw != null) {
            return new InMemoryContentBody(raw);
        } else if (file != null) {
            return new FileBackedContentBody(file, fileSize);
        } else {
            throw new IllegalStateException("Need to call raw() before build()");
        }
    }
}
