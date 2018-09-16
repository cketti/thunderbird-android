package com.fsck.k9.mail.data;


import java.io.IOException;
import java.io.OutputStream;


public interface Body {
    void writeTo(OutputStream outputStream) throws IOException;
}
