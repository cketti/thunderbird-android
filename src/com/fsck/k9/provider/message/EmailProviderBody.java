package com.fsck.k9.provider.message;

import android.content.Context;
import android.net.Uri;

import com.fsck.k9.message.Body;

import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EmailProviderBody implements Body {
    private Context mContext;
    private Uri mUri;

    EmailProviderBody(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
    }

    @Override
    public InputStream getInputStream(StreamType type) {
        InputStream in = null;
        if (type == StreamType.DECODED) {
            if (true) throw new RuntimeException("Not implemented yet");
        } else {
            try {
                in = mContext.getContentResolver().openInputStream(mUri);
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return in;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        InputStream in = getInputStream(StreamType.UNMODIFIED);
        IOUtils.copy(in, out);
    }
}
