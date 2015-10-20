package com.fsck.k9.mail.data.builder;


import com.fsck.k9.mail.data.Header;


public interface HeaderBuilder {
    void add(String name, String value);

    void addRaw(String name, String raw);

    Header build();
}
