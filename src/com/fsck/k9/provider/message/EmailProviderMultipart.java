package com.fsck.k9.provider.message;

import com.fsck.k9.message.Multipart;
import com.fsck.k9.message.Part;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class EmailProviderMultipart implements Multipart {
    private final List<Part> mParts = new ArrayList<Part>();
    private String mBoundary;
    private byte[] mPreamble;
    private byte[] mEpilogue;


    @Override
    public List<Part> getParts() {
        return mParts;
    }

    @Override
    public void addPart(Part part) {
        mParts.add(part);
    }

    @Override
    public void addPart(Part part, int position) {
        mParts.add(position, part);
    }

    @Override
    public void removePart(Part part) {
        // TODO Auto-generated method stub

    }

    @Override
    public byte[] getPreamble() {
        return mPreamble;
    }

    @Override
    public void setPreamble(byte[] preamble) {
        mPreamble = preamble;
    }

    @Override
    public byte[] getEpilogue() {
        return mEpilogue;
    }

    @Override
    public void setEpilogue(byte[] epilogue) {
        mEpilogue = epilogue;
    }

    @Override
    public String getBoundary() {
        return mBoundary;
    }

    @Override
    public void setBoundary(String boundary) {
        mBoundary = boundary;
    }

    @Override
    public InputStream getInputStream(StreamType type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out), 1024);

        if (mPreamble != null) {
            out.write(mPreamble);
        }

        if (mParts.size() == 0) {
            writer.write("--" + mBoundary + "\r\n");
        } else {
            for (Part part : mParts) {
                writer.write("--" + mBoundary + "\r\n");
                writer.flush();
                part.writeTo(out);
                writer.write("\r\n");
            }
        }

        writer.write("--" + mBoundary + "--\r\n");
        writer.flush();

        if (mEpilogue != null) {
            out.write(mEpilogue);
        }
    }
}