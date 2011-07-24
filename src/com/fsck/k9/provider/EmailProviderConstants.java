package com.fsck.k9.provider;

import android.net.Uri;

/**
 * Constants that are needed to communicate with {@link EmailProvider}.
 *
 * <p><strong>Note:</strong>
 * This file should contain all constants necessary for a third-party application to use the
 * content provider {@code EmailProvider}.
 */
public class EmailProviderConstants {
    public static final String AUTHORITY = "com.fsck.k9.emailprovider";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY + "/");

    public static final String ACCOUNT_URI_PATH_SEGMENT = "account";

    public static final ContentUri Folder = new ContentUri("folder");

    public static class ContentUri {
        public final String URI_PATH;

        private ContentUri(String uriPath) {
            URI_PATH = uriPath;
        }

        public Uri getContentUri(String accountUuid) {
            return BASE_URI.buildUpon()
                    .appendEncodedPath(ACCOUNT_URI_PATH_SEGMENT)
                    .appendPath(accountUuid)
                    .appendEncodedPath(URI_PATH)
                    .build();
        }
    }

    public interface FolderColumns {
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String LOCAL_ONLY = "local_only";

        public static final String UNREAD_COUNT = "unread_count";
        public static final String FLAGGED_COUNT = "flagged_count";
    }
}
