package com.fsck.k9.mail.data;


public interface HeaderField {
    String name();

    String value();

    String raw();

    boolean hasRawData();
}
