package com.fsck.k9.provider.message;

import com.fsck.k9.message.Body;
import com.fsck.k9.message.Header;
import com.fsck.k9.message.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EmailProviderMessage implements Message {
    private Header mHeader;
    private Body mBody;

    EmailProviderMessage() {
        mHeader = new EmailProviderHeader();
    }

    @Override
    public boolean isComplete() {
        return (mHeader.isComplete() && mBody != null);
    }

    @Override
    public Body getBody() {
        return mBody;
    }

    @Override
    public Header getHeader() {
        return mHeader;
    }

    @Override
    public void setBody(Body body) {
        mBody = body;
    }

    @Override
    public void setHeader(Header header) {
        mHeader = header;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        mHeader.writeTo(out);
        out.write("\r\n".getBytes());
        if (mBody != null) {
            mBody.writeTo(out);
        }
    }

    @Override
    public InputStream getInputStream(StreamType type) {
        // TODO Auto-generated method stub
        return null;
    }
}