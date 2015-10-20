package com.fsck.k9.mail.data;


import java.util.List;
import java.util.Set;


public interface Header {
    int size();

    Set<String> names();

    String value(String name);

    List<String> values(String name);
}
