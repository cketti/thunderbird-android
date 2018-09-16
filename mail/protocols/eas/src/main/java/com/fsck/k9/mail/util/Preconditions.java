package com.fsck.k9.mail.util;


import java.util.Map;


public final class Preconditions {
    private Preconditions() {}


    public static <T> T checkNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }

    public static <T extends Map> T checkNotEmpty(T map, String message) {
        if (map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }
}
