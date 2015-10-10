package com.fsck.k9.mail.util;


public final class Preconditions {
    private Preconditions() {}


    public static <T> T checkNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}
