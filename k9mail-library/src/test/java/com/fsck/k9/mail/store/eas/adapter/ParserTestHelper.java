package com.fsck.k9.mail.store.eas.adapter;


import java.io.ByteArrayInputStream;


class ParserTestHelper {
    public static ByteArrayInputStream inputStreamFromSerializer(Serializer serializer) {
        return new ByteArrayInputStream(serializer.toByteArray());
    }
}
