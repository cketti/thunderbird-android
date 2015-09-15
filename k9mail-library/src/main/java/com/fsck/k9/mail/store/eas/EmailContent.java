package com.fsck.k9.mail.store.eas;


import android.net.Uri;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
class EmailContent {
    public static final Uri CONTENT_URI = null;  //FIXME
    public static final String DEVICE_FRIENDLY_NAME = "deviceFriendlyName";
    public static String AUTHORITY = null;  //FIXME

    public static Uri uriWithLimit(Uri original, int i) {
        throw new RuntimeException("Not implemented");
    }

    public class AccountColumns {
        public static final String MAX_ATTACHMENT_SIZE = "maxAttachmentSize";
        public static final String SECURITY_SYNC_KEY = "securitySyncKey";
    }

    public class HostAuthColumns {
        public static final String ADDRESS = "address";
        public static final String SERVER_CERT = "serverCert";
        public static final String _ID = "_id";
    }
}
