package com.fsck.k9.provider.message;

import com.fsck.k9.mail.internet.EncoderUtil;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.message.Header;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class EmailProviderHeader implements Header {

    private boolean mComplete = false;
    //TODO: support multiple header values
    //TODO: keep order!
    private Map<String, String> mHeaders = new LinkedHashMap<String, String>();

    @Override
    public void writeTo(OutputStream out) throws IOException {
        for (Entry<String, String> header : mHeaders.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();

            //TODO: specify charset
            out.write(name.getBytes());
            out.write(": ".getBytes());
            out.write(value.getBytes());
            out.write("\r\n".getBytes());
        }
    }

    @Override
    public void addEncoded(String name, String value) {
        mHeaders.put(name, value);
    }

    @Override
    public void add(String name, String value) {
        final String encodedValue;
        if (MimeUtility.hasToBeEncoded(value)) {
            encodedValue = EncoderUtil.encodeEncodedWord(value, Charset.forName("UTF-8"));
        } else {
            encodedValue = value;
        }
        mHeaders.put(name, encodedValue);
    }

    public String[] get(String name) {
        return new String[] { MimeUtility.unfoldAndDecode(mHeaders.get(name)) };
    }

    @Override
    public boolean isComplete() {
        return mComplete;
    }

    @Override
    public void setComplete(boolean complete) {
        mComplete = complete;
    }
}