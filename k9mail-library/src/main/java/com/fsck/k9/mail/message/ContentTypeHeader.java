package com.fsck.k9.mail.message;


import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.ContentTypeFieldImpl;
import org.apache.james.mime4j.stream.RawField;

import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


public final class ContentTypeHeader {
    private static final String DEFAULT_CHARSET = "us-ascii";


    private final ContentTypeField field;


    private ContentTypeHeader(ContentTypeField field) {
        this.field = field;
    }

    public static ContentTypeHeader parse(String value) {
        checkNotNull(value, "Argument 'value' must not be null");

        RawField field = new RawField("Content-Type", value);
        ContentTypeField contentTypeField = ContentTypeFieldImpl.PARSER.parse(field, DecodeMonitor.SILENT);

        return new ContentTypeHeader(contentTypeField);
    }

    public String mimeType() {
        return field.getMimeType();
    }

    public String boundary() {
        return field.getBoundary();
    }

    public String charset() {
        String charset = field.getCharset();
        return (charset == null) ? DEFAULT_CHARSET : charset;
    }
}
