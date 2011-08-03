package com.fsck.k9.provider.message;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.store.StorageManager;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Part;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class TempFileBody implements Body {

    private Part mPart;
    private File mFile;

    TempFileBody(Part part, File file) {
        mPart = part;
        mFile = file;
    }

    TempFileBody(Context context, String accountUuid, Part part, InputStream in) {
        mPart = part;

        Account account = Preferences.getPreferences(context).getAccount(accountUuid);
        File storageDirectory = StorageManager.getInstance(context).getAttachmentDirectory(accountUuid, account.getLocalStorageProviderId());

        try {
            mFile = File.createTempFile("body", null, storageDirectory);
            //mFile.deleteOnExit(); will this be canceled if the file is renamed?
            FileOutputStream out = new FileOutputStream(mFile);
            IOUtils.copy(in, out);
            in.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public InputStream getInputStream(StreamType type) {
        InputStream in = null;
        if (type == StreamType.DECODED) {
            //in = new DecodeMessageBodyStream(mPart, mFile);
            if (true) throw new RuntimeException("Not implemented yet");
        } else {
            try {
                in = new FileInputStream(mFile);
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