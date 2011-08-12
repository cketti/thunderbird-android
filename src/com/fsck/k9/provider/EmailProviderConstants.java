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
    public static final ContentUri Message = new ContentUri("message");
    public static final ContentUri MessagePart = new ContentUri("message_part");
    public static final ContentUri MessageParts = new ContentUri("message_parts");
    public static final ContentUri MessagePartAttibute = new ContentUri("message_part_attribute");
    public static final ContentUri Address = new ContentUri("address");
    public static final ContentUri AccountStats = new ContentUri("stats");

    public static final String[] FOLDER_PROJECTION = new String[] {
        FolderColumns.ID,
        FolderColumns.NAME,
        FolderColumns.LOCAL_ONLY,
        FolderColumns.UNREAD_COUNT,
        FolderColumns.FLAGGED_COUNT,
        FolderColumns.INTEGRATE,
        FolderColumns.TOP_GROUP,
        FolderColumns.DISPLAY_CLASS,
        FolderColumns.VISIBLE_LIMIT
    };

    public static final String[] ACCOUNT_STATS_PROJECTION = new String[] {
        AccountStatsColumns.SIZE
    };


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

        public static final String INTEGRATE = "integrate";
        public static final String TOP_GROUP = "top_group";
        public static final String DISPLAY_CLASS = "display_class";
        public static final String VISIBLE_LIMIT = "visible_limit";
        
        public static final String LAST_CHECKED = "last_checked";
        public static final String STATUS = "status";
    }

    public interface MessageColumns {
        public static final String ID = "id";
        public static final String FOLDER_ID = "folder_id";
        public static final String UID = "uid";
        public static final String ROOT = "root";
        public static final String PARENT = "parent";
        public static final String SEQ = "seq";
        public static final String LOCAL_ONLY = "local_only";
        public static final String DELETED = "deleted";
        public static final String NOTIFIED = "notified";
        public static final String DATE = "date";
        public static final String INTERNAL_DATE = "internal_date";
        public static final String SEEN = "seen";
        public static final String FLAGGED = "flagged";
        public static final String ANSWERED = "answered";
        public static final String FORWARDED = "forwarded";
        public static final String DESTROYED = "destroyed";
        public static final String SEND_FAILED = "send_failed";
        public static final String SEND_IN_PROGRESS = "send_in_progress";
        public static final String DOWNLOADED_FULL = "downloaded_full";
        public static final String DOWNLOADED_PARTIAL = "downloaded_partial";
        public static final String REMOTE_COPY_STARTED = "remote_copy_started";
        public static final String GOT_ALL_HEADERS = "got_all_headers";
    }

    public interface MessagePartColumns {
        public static final String ID = "id";
        public static final String MESSAGE_ID = "message_id";
        public static final String HEADER = "header";
        public static final String MIME_TYPE = "mime_type";
        public static final String PARENT = "parent";
        public static final String SEQ = "seq";
        public static final String SIZE = "size";
        public static final String PREAMBLE = "preamble";
        public static final String EPILOGUE = "epilogue";
        public static final String COMPLETE = "complete";

        public static final String DATA_TYPE = "data_type";
        public static final String DATA = "data";
    }

    public interface MessagePartAttributeColumns {
        public static final String ID = "id";
        public static final String MESSAGE_PART_ID = "message_part_id";
        public static final String KEY = "key";
        public static final String VALUE = "value";
    }

    public interface AddressColumns {
        public static final String ID = "id";
        public static final String MESSAGE_ID = "message_id";
        public static final String TYPE = "type";
        public static final String NAME = "name";
        public static final String EMAIL = "email";
    }

    public interface AccountStatsColumns {
        public static final String SIZE = "size";
    }
}
