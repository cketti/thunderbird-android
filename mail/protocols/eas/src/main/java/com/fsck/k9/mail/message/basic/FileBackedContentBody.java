package com.fsck.k9.mail.message.basic;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fsck.k9.mail.data.builder.ContentBodyBuilder;
import com.fsck.k9.mail.helper.FileFactory;
import com.fsck.k9.mail.message.FileBackedBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


class FileBackedContentBody implements FileBackedBody {
    private final File file;
    private final long fileSize;


    FileBackedContentBody(File file, long fileSize) {
        this.file = file;
        this.fileSize = fileSize;
    }

    private FileBackedContentBody(Builder builder) {
        checkNotNull(builder.file, "Missing body");

        file = builder.file;
        fileSize = builder.fileSize;
    }

    @Override
    public long length() {
        return fileSize;
    }

    @Override
    public InputStream raw() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        BufferedSource source = Okio.buffer(Okio.source(file));
        try {
            source.readAll(Okio.sink(outputStream));
        } finally {
            source.close();
        }
    }

    @Override
    public File getFile() {
        return file;
    }


    public static class Builder implements ContentBodyBuilder {
        private final FileFactory fileFactory;

        private File file;
        private long fileSize;


        public Builder(FileFactory fileFactory) {
            this.fileFactory = fileFactory;
        }

        @Override
        public void raw(InputStream inputStream) throws IOException {
            if (file != null) {
                throw new IllegalStateException("raw() called more than once");
            }

            file = fileFactory.createFile();
            BufferedSink fileSink = Okio.buffer(Okio.sink(file));
            try {
                fileSize = fileSink.writeAll(Okio.source(inputStream));
            } finally {
                fileSink.close();
            }
        }

        public FileBackedContentBody build() {
            return new FileBackedContentBody(this);
        }
    }
}
