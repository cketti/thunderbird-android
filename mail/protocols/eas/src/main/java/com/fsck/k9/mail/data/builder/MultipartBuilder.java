package com.fsck.k9.mail.data.builder;


import java.util.List;

import com.fsck.k9.mail.data.Multipart;


public interface MultipartBuilder extends BodyBuilder {
    void add(PartBuilder builder);

    void add(List<PartBuilder> builders);

    void preamble(byte[] preamble);

    void epilogue(byte[] epilogue);

    void boundary(String boundary);

    Multipart build();
}
