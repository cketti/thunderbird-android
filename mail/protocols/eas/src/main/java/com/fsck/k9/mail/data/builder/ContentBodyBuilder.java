package com.fsck.k9.mail.data.builder;


import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.mail.data.ContentBody;


public interface ContentBodyBuilder extends BodyBuilder {
    void raw(InputStream raw) throws IOException;

    ContentBody build();
}
