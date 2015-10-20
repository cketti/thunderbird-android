package com.fsck.k9.mail.message.basic;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fsck.k9.mail.data.Header;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.util.LinkedHashMultimap;
import com.fsck.k9.mail.util.Multimap;


class BasicHeader implements Header {
    private final Multimap<String, BasicHeaderField> fieldMap;


    private BasicHeader(Builder builder) {
        fieldMap = LinkedHashMultimap.create(builder.fields.size());
        for (BasicHeaderField field : builder.fields) {
            String lowerCaseName = field.name().toLowerCase(Locale.ENGLISH);
            fieldMap.put(lowerCaseName, field);
        }
    }

    @Override
    public int size() {
        return fieldMap.size();
    }

    @Override
    public Set<String> names() {
        return fieldMap.keys();
    }

    @Override
    public String value(String name) {
        String lowerCaseName = name.toLowerCase(Locale.ENGLISH);
        List<BasicHeaderField> values = fieldMap.get(lowerCaseName);
        return (values.isEmpty()) ? null : values.get(0).value();
    }

    @Override
    public List<String> values(String name) {
        if (!fieldMap.containsKey(name)) {
            return Collections.emptyList();
        }

        List<BasicHeaderField> values = fieldMap.get(name);
        List<String> result = new ArrayList<String>(values.size());
        for (BasicHeaderField field : values) {
            result.add(field.value());
        }

        return result;
    }

    public static class Builder implements HeaderBuilder {
        private List<BasicHeaderField> fields = new ArrayList<BasicHeaderField>();


        public void add(String name, String value) {
            BasicHeaderField field = BasicHeaderField.newField(name, value);
            fields.add(field);
        }

        @Override
        public void addRaw(String name, String raw) {
            BasicHeaderField field = BasicHeaderField.newRawField(name, raw);
            fields.add(field);
        }

        public BasicHeader build() {
            return new BasicHeader(this);
        }
    }
}
