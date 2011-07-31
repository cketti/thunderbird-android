package com.fsck.k9.provider.message;

import com.fsck.k9.message.Folder;

public class EmailProviderFolder implements Folder {
    private final long mId;
    private final String mName;
    private int mVisibleLimit;
    private boolean mInTopGroup;
    private boolean mIntegrate;

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

    public boolean isIntegrated() {
        return mIntegrate;
    }

    public void setIntegrate(boolean integrate) {
        mIntegrate = integrate;
    }

    public boolean isInTopGroup() {
        return mInTopGroup;
    }

    public void setInTopGroup(boolean inTopGroup) {
        mInTopGroup = inTopGroup;
    }

    public int getVisibleLimit() {
        return mVisibleLimit;
    }

    public void setVisibleLimit(int visibleLimit) {
        mVisibleLimit = visibleLimit;
    }
}
