package com.fsck.k9.mail.data;


import java.util.List;


public interface Multipart extends Body {
    byte[] preamble();

    byte[] epilogue();

    int size();

    List<Part> children();
}
