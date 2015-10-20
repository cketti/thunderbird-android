package com.fsck.k9.mail.message.basic;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;


class BasicMultipart implements Multipart {
    private final byte[] preamble;
    private final byte[] epilogue;
    private final List<Part> children;


    private BasicMultipart(Builder builder) {
        preamble = builder.preamble;
        epilogue = builder.epilogue;
        children = builder.children();
    }

    @Override
    public byte[] preamble() {
        return preamble;
    }

    @Override
    public byte[] epilogue() {
        return epilogue;
    }

    @Override
    public int size() {
        return children.size();
    }

    @Override
    public List<Part> children() {
        return Collections.unmodifiableList(children);
    }


    public static class Builder implements MultipartBuilder {
        private final List<PartBuilder> children = new ArrayList<PartBuilder>();
        private byte[] preamble;
        private byte[] epilogue;


        @Override
        public void add(PartBuilder child) {
            children.add(child);
        }

        @Override
        public void add(List<PartBuilder> children) {
            this.children.addAll(children);
        }

        @Override
        public void preamble(byte[] preamble) {
            this.preamble = preamble;
        }

        @Override
        public void epilogue(byte[] epilogue) {
            this.epilogue = epilogue;
        }

        @Override
        public Multipart build() {
            return new BasicMultipart(this);
        }

        List<Part> children() {
            List<Part> result = new ArrayList<Part>(children.size());
            for (PartBuilder builder : children) {
                result.add(builder.build());
            }

            return result;
        }
    }
}
