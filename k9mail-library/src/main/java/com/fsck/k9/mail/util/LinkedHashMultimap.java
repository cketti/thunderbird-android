package com.fsck.k9.mail.util;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


public class LinkedHashMultimap<K, V> implements Multimap<K, V>  {
    private static final int DEFAULT_VALUES_PER_KEY = 1;


    private final LinkedHashMap<K, ArrayList<V>> map;
    private final int expectedValuesPerKey;
    private int size = 0;


    private LinkedHashMultimap(int expectedKeys, int expectedValuesPerKey) {
        map = new LinkedHashMap<K, ArrayList<V>>(expectedKeys);
        this.expectedValuesPerKey = expectedValuesPerKey;
    }

    public static <K, V> LinkedHashMultimap<K, V> create(int expectedKeys) {
        return new LinkedHashMultimap<K, V>(expectedKeys, DEFAULT_VALUES_PER_KEY);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(K key) {
        checkNotNull(key, "Argument 'key' must not be null");

        return map.containsKey(key);
    }

    @Override
    public void put(K key, V value) {
        checkNotNull(key, "Argument 'key' must not be null");

        ArrayList<V> values = map.get(key);
        if (values == null) {
            values = new ArrayList<V>(expectedValuesPerKey);
            map.put(key, values);
        }

        values.add(value);
        size++;
    }

    @Override
    public List<V> get(K key) {
        checkNotNull(key, "Argument 'key' must not be null");

        ArrayList<V> values = map.get(key);
        return (values != null) ? Collections.unmodifiableList(values) : Collections.<V>emptyList();
    }

    @Override
    public Set<K> keys() {
        return map.keySet();
    }
}
