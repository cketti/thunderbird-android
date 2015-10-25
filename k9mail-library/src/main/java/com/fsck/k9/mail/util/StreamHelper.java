package com.fsck.k9.mail.util;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import okio.BufferedSource;
import okio.Okio;


public class StreamHelper {

    public static byte[] readIntoByteArray(InputStream inputStream) throws IOException {
        BufferedSource source = Okio.buffer(Okio.source(inputStream));
        return source.readByteArray();
    }

    public static InputStream inputStreamFromString(String data) {
        return new ByteArrayInputStream(data.getBytes());
    }
}
