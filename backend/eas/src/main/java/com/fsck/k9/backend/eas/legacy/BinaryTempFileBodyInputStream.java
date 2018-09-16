package com.fsck.k9.backend.eas.legacy;


import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;


public class BinaryTempFileBodyInputStream extends FilterInputStream {
    private final File file;


    public BinaryTempFileBodyInputStream(InputStream inputStream, File file) {
        super(inputStream);
        this.file = file;
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            file.delete();
        }
    }
}
