package com.fsck.k9.mail.data.builder;


import com.fsck.k9.mail.data.Part;


public interface PartBuilder {
    void header(HeaderBuilder builder);

    void body(BodyBuilder body);

    Part build();
}
