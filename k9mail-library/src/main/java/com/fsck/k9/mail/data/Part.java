package com.fsck.k9.mail.data;


public interface Part {
    Header header();

    Body body();
}
