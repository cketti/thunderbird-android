package com.fsck.k9.mail.filter;


import java.io.IOException;
import java.io.OutputStream;


public class FixedLengthOutputStream extends OutputStream {
    private final OutputStream outputStream;
    private final int length;
    private int numberOfBytesWritten;

    public FixedLengthOutputStream(OutputStream outputStream, int length) {
        this.outputStream = outputStream;
        this.length = length;
    }

    @Override
    public void write(int oneByte) throws IOException {
        if (numberOfBytesWritten < length) {
            outputStream.write(oneByte);
            numberOfBytesWritten++;
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        int numberOfBytesToWrite = (numberOfBytesWritten + count > length) ? length - numberOfBytesWritten : count;
        outputStream.write(buffer, offset, numberOfBytesToWrite);
        numberOfBytesWritten += numberOfBytesToWrite;
    }

    public boolean isWriteComplete() {
        return numberOfBytesWritten == length;
    }

    public int getNumberOfBytesWritten() {
        return numberOfBytesWritten;
    }
}
