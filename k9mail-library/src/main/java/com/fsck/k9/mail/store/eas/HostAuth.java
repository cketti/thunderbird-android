package com.fsck.k9.mail.store.eas;


import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class HostAuth {
    public static final int FLAG_NONE         = 0x00;    // No flags
    public static final int FLAG_SSL          = 0x01;    // Use SSL
    public static final int FLAG_TLS          = 0x02;    // Use TLS
    public static final int FLAG_AUTHENTICATE = 0x04;    // Use name/password for authentication
    public static final int FLAG_TRUST_ALL    = 0x08;    // Trust all certificates
    public static final int FLAG_OAUTH        = 0x10;    // Use OAuth for authentication
    // Mask of settings directly configurable by the user
    public static final int USER_CONFIG_MASK  = 0x1b;
    public static final int FLAG_TRANSPORTSECURITY_MASK = FLAG_SSL | FLAG_TLS | FLAG_TRUST_ALL;
    public static final Uri CONTENT_URI = null; //FIXME

    public String mLogin;
    public String mPassword;
    public int mPort;
    public String mProtocol;
    public int mFlags;
    public String mAddress;
    public String mClientCertAlias;
    public Long mId;
    public byte[] mServerCert;


    public HostAuth() {
        mId = -1L;
    }

    public boolean isSaved() {
        return false;
    }

    public void update(Context context, ContentValues contentValues) {
        throw new RuntimeException("Not implemented");
    }

    public boolean shouldUseSsl() {
        return (mFlags & FLAG_SSL) != 0;
    }

    public boolean shouldTrustAllServerCerts() {
        throw new RuntimeException("Not implemented");
    }
}
