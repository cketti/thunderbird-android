package com.fsck.k9.mail.data;


import java.io.InputStream;


public interface ContentBody extends Body {
    long size();
    InputStream raw();
}
