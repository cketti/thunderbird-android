package com.fsck.k9.provider.message;

import com.fsck.k9.message.Folder;

public class EmailProviderFolder implements Folder {
    private final long mId;
    private final String mName;
    private int mVisibleLimit;
    private boolean mInTopGroup;
    private boolean mIntegrate;
    private Integer mLastUid;
    private int mUnreadCount;
    private int mFlaggedCount;
    private String mStatus;
    private long mLastChecked;
    private String mPushState;
    private boolean mPushStateModified;

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

    public String getPushState() {
        return mPushState;
    }

    public void setPushState(String pushState) {
        if (pushState == null) {
            mPushStateModified = (mPushState != null);
        } else {
            mPushStateModified = !pushState.equals(mPushState);
        }
        mPushState = pushState;
    }
    
    public boolean isPushStateModified() {
        return mPushStateModified;
    }
    
    public void resetPushStateModified() {
        mPushStateModified = false;
    }

    public Integer getLastUid() {
        return mLastUid;
    }
    
    public void setLastUid(int uid) {
        mLastUid = uid;
    }

    public int getUnreadCount() {
        return mUnreadCount;
    }
    
    public void setUnreadCount(int unreadCount) {
        mUnreadCount = unreadCount;
    }

    public int getFlaggedCount() {
        return mFlaggedCount;
    }
    
    public void setFlaggedCount(int flaggedCount) {
        mFlaggedCount = flaggedCount;
    }

    public String getStatus() {
        return mStatus;
    }
    
    public void setStatus(String status) {
        mStatus = status;
    }

    public long getLastChecked() {
        return mLastChecked;
    }
    
    public void setLastChecked(long timestamp) {
        mLastChecked = timestamp;
    }
}
