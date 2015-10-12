package com.fsck.k9.mail.data;


import java.util.List;


public interface Header {
    int size();

    List<String> names();

    String value(String name);

    List<String> values(String name);
}
