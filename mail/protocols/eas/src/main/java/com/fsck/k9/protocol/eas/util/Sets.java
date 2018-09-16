package com.fsck.k9.protocol.eas.util;


import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


public class Sets {
    public static <E> Set<E> newUnmodifiableSet(E... elements) {
        HashSet<E> hashSet = new HashSet<E>(elements.length);
        Collections.addAll(hashSet, elements);
        return Collections.unmodifiableSet(hashSet);
    }
}
