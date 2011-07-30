package com.fsck.k9.provider.message;

import com.fsck.k9.message.Folder;

public class EmailProviderFolder implements Folder {
    private final long mId;
    private final String mName;

    public EmailProviderFolder(String name) {
        this(name, -1);
    }

    public EmailProviderFolder(String name, long id) {
        mName = name;
        mId = id;
    }

    public long getId() {
        return mId;
    }

    @Override
    public String getInternalName() {
        return mName;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getAttribute(String key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setAttribute(String key, String value) {
        // TODO Auto-generated method stub

    }

}
