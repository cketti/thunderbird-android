package com.fsck.k9.mail.data;


import java.io.InputStream;


public interface Body {
    int length();

    InputStream content();
}
