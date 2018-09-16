package com.fsck.k9.mail.util;


import java.util.List;
import java.util.Set;


/**
 * Minimal Multimap interface containing just the functionality we need.
 */
public interface Multimap<K, V> {
    int size();
    boolean isEmpty();
    boolean containsKey(K key);

    void put(K key, V value);

    List<V> get(K key);
    Set<K> keys();
}
