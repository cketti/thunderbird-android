package com.fsck.k9.mail.util;


import java.io.IOException;
import java.io.InputStream;

import okio.BufferedSource;
import okio.Okio;


public class StreamHelper {

    public static byte[] readIntoByteArray(InputStream inputStream) throws IOException {
        BufferedSource source = Okio.buffer(Okio.source(inputStream));
        return source.readByteArray();
    }
}
