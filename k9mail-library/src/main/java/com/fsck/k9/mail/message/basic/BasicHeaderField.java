package com.fsck.k9.mail.message.basic;


import com.fsck.k9.mail.data.HeaderField;


class BasicHeaderField implements HeaderField {
    private final String name;
    private final String value;
    private final String raw;


    private BasicHeaderField(String name, String value, String raw) {
        if (name == null) {
            throw new NullPointerException("Argument 'name' cannot be null");
        }

        this.name = name;
        this.value = value;
        this.raw = raw;
    }

    public static BasicHeaderField newField(String name, String value) {
        if (value == null) {
            throw new NullPointerException("Argument 'value' cannot be null");
        }

        return new BasicHeaderField(name, value, null);
    }

    public static BasicHeaderField newRawField(String name, String raw) {
        if (raw == null) {
            throw new NullPointerException("Argument 'raw' cannot be null");
        }
        if (name != null && !raw.startsWith(name + ":")) {
            throw new IllegalArgumentException("The value of 'raw' needs to start with the supplied field name " +
                    "followed by a colon");
        }

        return new BasicHeaderField(name, null, raw);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String value() {
        if (value != null) {
            return value;
        }

        int delimiterIndex = raw.indexOf(':');
        if (delimiterIndex == raw.length() - 1) {
            return "";
        }

        return raw.substring(delimiterIndex + 1).trim();
    }

    @Override
    public String raw() {
        return raw;
    }

    @Override
    public boolean hasRawData() {
        return raw != null;
    }

    @Override
    public String toString() {
        return (hasRawData()) ? raw() : name() + ": " + value();
    }
}
