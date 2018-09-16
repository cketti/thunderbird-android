package com.fsck.k9.mail.message.basic;


import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fsck.k9.mail.data.Multipart;
import com.fsck.k9.mail.data.Part;
import com.fsck.k9.mail.data.builder.MultipartBuilder;
import com.fsck.k9.mail.data.builder.PartBuilder;


class BasicMultipart implements Multipart {
    private static final byte[] DASH_DASH = { '-', '-' };
    private static final byte[] CRLF = { '\r', '\n' };


    private final byte[] preamble;
    private final byte[] epilogue;
    private final byte[] boundary;
    private final List<Part> children;


    private BasicMultipart(Builder builder) {
        preamble = builder.preamble;
        epilogue = builder.epilogue;
        boundary = builder.boundary;
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

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        writePreamble(outputStream);
        writeChildren(outputStream);
        writeEpilogue(outputStream);
    }

    private void writePreamble(OutputStream outputStream) throws IOException {
        if (preamble != null) {
            outputStream.write(preamble);
        }
    }

    private void writeChildren(OutputStream outputStream) throws IOException {
        for (Part bodyPart : children) {
            writeBoundary(outputStream, false);
            bodyPart.writeTo(outputStream);
            outputStream.write(CRLF);
        }

        writeBoundary(outputStream, !children.isEmpty());
    }

    private void writeBoundary(OutputStream outputStream, boolean endBoundary) throws IOException {
        outputStream.write(DASH_DASH);
        outputStream.write(boundary);
        if (endBoundary) {
            outputStream.write(DASH_DASH);
        }
        outputStream.write(CRLF);
    }

    private void writeEpilogue(OutputStream outputStream) throws IOException {
        if (epilogue != null) {
            outputStream.write(epilogue);
        }
    }


    public static class Builder implements MultipartBuilder {
        private final List<PartBuilder> children = new ArrayList<PartBuilder>();
        private byte[] preamble;
        private byte[] epilogue;
        private byte[] boundary;


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
        public void boundary(String boundary) {
            this.boundary = boundary.getBytes();
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
