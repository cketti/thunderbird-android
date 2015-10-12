package com.fsck.k9.mail.data;


import java.util.List;


public interface Multipart {
    int size();

    List<Part> children();
}
