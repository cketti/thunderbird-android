package com.fsck.k9.mail.message.basic;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fsck.k9.mail.data.Header;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.util.LinkedHashMultimap;
import com.fsck.k9.mail.util.Multimap;


class BasicHeader implements Header {
    private final List<BasicHeaderField> fields;
    private final Multimap<String, BasicHeaderField> fieldMap;


    private BasicHeader(Builder builder) {
        fields = builder.fields;
        fieldMap = LinkedHashMultimap.create(builder.fields.size());
        for (BasicHeaderField field : builder.fields) {
            String lowerCaseName = lowerCase(field.name());
            fieldMap.put(lowerCaseName, field);
        }
    }

    @Override
    public int size() {
        return fieldMap.size();
    }

    @Override
    public List<BasicHeaderField> fields() {
        return Collections.unmodifiableList(fields);
    }

    @Override
    public String value(String name) {
        String lowerCaseName = lowerCase(name);
        List<BasicHeaderField> values = fieldMap.get(lowerCaseName);
        return (values.isEmpty()) ? null : values.get(0).value();
    }

    @Override
    public List<String> values(String name) {
        String lowerCaseName = lowerCase(name);
        if (!fieldMap.containsKey(lowerCaseName)) {
            return Collections.emptyList();
        }

        List<BasicHeaderField> values = fieldMap.get(lowerCaseName);
        List<String> result = new ArrayList<String>(values.size());
        for (BasicHeaderField field : values) {
            result.add(field.value());
        }

        return result;
    }

    private String lowerCase(String input) {
        return input.toLowerCase(Locale.ENGLISH);
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
