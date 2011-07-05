package com.fsck.k9.provider;

public class EmailProviderConstants {
    public static final String AUTHORITY = "com.fsck.k9.emailprovider";

    public interface FolderColumns {
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String LOCAL_ONLY = "local_only";

        public static final String UNREAD_COUNT = "unread_count";
        public static final String FLAGGED_COUNT = "flagged_count";
    }
}
