package com.fsck.k9.mail.data;


import java.io.IOException;
import java.io.OutputStream;
import java.util.List;


public interface Header {
    int size();

    List<? extends HeaderField> fields();

    String value(String name);

    List<String> values(String name);

    void writeTo(OutputStream outputStream) throws IOException;
}
