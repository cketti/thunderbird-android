package com.fsck.k9.mail.store.eas;


import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class Account {
    public static final Uri CONTENT_URI = Uri.parse("FIXME");
    public static final long NO_ACCOUNT = -1;


    public String mEmailAddress;
    public HostAuth mHostAuthRecv;
    public long mId = NO_ACCOUNT;
    private String protocolVersion;
    public String mSyncKey;
    private String policyKey;


    public HostAuth getOrCreateHostAuthRecv(Context context) {
        if (mHostAuthRecv == null) {
            mHostAuthRecv = new HostAuth();
        }

        return mHostAuthRecv;
    }

    public long getId() {
        return mId;
    }

    public static void update(Context context, Uri contentUri, long accountId, ContentValues values) {
        throw new RuntimeException("Not implemented");
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getPolicyKey() {
        return policyKey;
    }

    public void setPolicyKey(String policyKey) {
        this.policyKey = policyKey;
    }
}
