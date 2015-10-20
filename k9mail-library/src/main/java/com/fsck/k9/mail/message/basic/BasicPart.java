package com.fsck.k9.mail.message.basic;


import com.fsck.k9.mail.data.Body;
import com.fsck.k9.mail.data.Header;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.data.builder.BodyBuilder;
import com.fsck.k9.mail.data.builder.HeaderBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;


class BasicPart implements Part {
    private final Header header;
    private final Body body;


    BasicPart(Builder builder) {
        header = builder.header();
        body = builder.body();
    }

    @Override
    public Header header() {
        return header;
    }

    @Override
    public Body body() {
        return body;
    }


    public static class Builder implements PartBuilder {
        private HeaderBuilder headerBuilder;
        private BodyBuilder bodyBuilder;


        public void header(HeaderBuilder builder) {
            headerBuilder = builder;
        }

        public void body(BodyBuilder builder) {
            bodyBuilder = builder;
        }

        public BasicPart build() {
            return new BasicPart(this);
        }

        Header header() {
            return (headerBuilder != null) ? headerBuilder.build() : null;
        }

        Body body() {
            return (bodyBuilder != null) ? bodyBuilder.build() : null;
        }
    }
}
