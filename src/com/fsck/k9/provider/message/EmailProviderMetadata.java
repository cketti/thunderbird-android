package com.fsck.k9.provider.message;

import com.fsck.k9.message.Metadata;
import com.fsck.k9.message.Part;

import java.util.HashMap;
import java.util.Map;

public class EmailProviderMetadata extends Metadata {
    private static final Long[] EMPTY_LONG_ARRAY = new Long[0];

    private String mAccountUuid;
    private long mFolderId;
    private long mId;
    private boolean mLocalOnly;
    private Map<Part, Long> mPartIdMapping = new HashMap<Part, Long>();
    private Map<Long, Part> mIdPartMapping = new HashMap<Long, Part>();

    public EmailProviderMetadata(String accountUuid) {
        mAccountUuid = accountUuid;
    }

    public String getAccountUuid() {
        return mAccountUuid;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public void setFolderId(long folderId) {
        mFolderId = folderId;
    }

    public long getId() {
        return mId;
    }

    public void setId(long id) {
        mId = id;
    }

    public boolean isLocalOnly() {
        return mLocalOnly;
    }

    public void setLocalOnly(boolean localOnly) {
        mLocalOnly = localOnly;
    }

    public Part getPart(long partId) {
        return mIdPartMapping.get(partId);
    }

    public Long getPartId(Part part) {
        return mPartIdMapping.get(part);
    }

    public void updateMapping(Part part, long partId) {
        mPartIdMapping.put(part, partId);
        mIdPartMapping.put(partId, part);
    }

    public Long[] getPartIds() {
        return mPartIdMapping.values().toArray(EMPTY_LONG_ARRAY);
    }

    public void setAttribute(long partId, String key, String value) {
        Part part = getPart(partId);
        setAttribute(part, key, value);
    }
}
