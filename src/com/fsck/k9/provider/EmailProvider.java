package com.fsck.k9.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.store.LockableDatabase;
import com.fsck.k9.mail.store.StorageManager;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.mail.store.LockableDatabase.DbCallback;
import com.fsck.k9.mail.store.LockableDatabase.WrappedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ContentProvider} to access the contents of the message database.
 *
 * <p><strong>Note:</strong>
 * This class is going to supersede {@link MessageProvider}.
 */
public class EmailProvider extends ContentProvider {
    private static final int DB_VERSION = 43;

    private static final int FOLDER_BASE = 0x1000;
    private static final int FOLDERS = FOLDER_BASE;
    private static final int FOLDER_ID = FOLDER_BASE + 1;

    private static final int MESSAGE_BASE = 0x2000;
    private static final int MESSAGES = MESSAGE_BASE;
    private static final int MESSAGE_ID = MESSAGE_BASE + 1;

    private static final int MESSAGE_PART_BASE = 0x3000;
    private static final int MESSAGE_PARTS = MESSAGE_PART_BASE;
    private static final int MESSAGE_PART_ID = MESSAGE_PART_BASE + 1;

    private static final int MESSAGE_PART_ATTRIBUTES_BASE = 0x4000;
    private static final int MESSAGE_PART_ATTRIBUTES = MESSAGE_PART_ATTRIBUTES_BASE;
    private static final int MESSAGE_PART_ATTRIBUTE_ID = MESSAGE_PART_ATTRIBUTES_BASE + 1;
    private static final int MESSAGE_PARTS_BY_MESSAGE_ID = MESSAGE_PART_BASE + 2;

    private static final int ADDRESS_BASE = 0x5000;
    private static final int ADDRESSES = ADDRESS_BASE;
    private static final int ADDRESS_ID = ADDRESS_BASE + 1;

    private static final int EXTRA_BASE = 0x10000;
    private static final int ACCOUNT_STATS = EXTRA_BASE;

    private static final String FOLDERS_TABLE = "folders";
    private static final String MESSAGES_TABLE = "messages";
    private static final String MESSAGE_PARTS_TABLE = "message_parts";
    private static final String MESSAGE_PART_ATTRIBUTES_TABLE = "message_part_attributes";
    private static final String ADDRESSES_TABLE = "addresses";

    private static final String[] TABLE_NAMES = new String[] {
        "",                 // placeholder for accounts
        FOLDERS_TABLE,
        MESSAGES_TABLE,
        MESSAGE_PARTS_TABLE,
        MESSAGE_PART_ATTRIBUTES_TABLE,
        ADDRESSES_TABLE
    };

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // Email URI matching table
        UriMatcher matcher = sURIMatcher;

        // Folders of a specific account
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/folder", FOLDERS);

        // A specific folder
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/folder/#", FOLDER_ID);

        // Messages of a specific account
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message", MESSAGES);

        // A specific message
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message/#", MESSAGE_ID);

        // EmailProviderMessage parts
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message_part", MESSAGE_PARTS);

        // A specific message part
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message_part/#", MESSAGE_PART_ID);

        // All message parts of a specific message
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message_parts/#", MESSAGE_PARTS_BY_MESSAGE_ID);

        // EmailProviderMessage part attributes
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/message_part_attribute", MESSAGE_PART_ATTRIBUTES);

        // Account statistics (e.g. occupied storage space of database + message bodies/attachments)
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/stats", ACCOUNT_STATS);
    }



    private Context mContext;
    private final Map<String, LockableDatabase> mDatabases = new HashMap<String, LockableDatabase>();

    @Override
    public boolean onCreate() {
        mContext = getContext();
        return true;
    }

    private LockableDatabase getDatabase(String accountUuid) throws UnavailableStorageException {
        checkAccountUuidValidity(accountUuid);

        LockableDatabase database = mDatabases.get(accountUuid);
        if (database == null) {
            Account account = Preferences.getPreferences(mContext).getAccount(accountUuid);
            database = new LockableDatabase(mContext, accountUuid, new StoreSchemaDefinition());
            database.setStorageProviderId(account.getLocalStorageProviderId());
            database.open();
            mDatabases.put(accountUuid, database);
        }

        return database;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Cursor query(Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        Cursor cursor = null;

        int match = sURIMatcher.match(uri);
        List<String> segments = uri.getPathSegments();
        switch (match) {
            case FOLDERS:
            case MESSAGES:
            case MESSAGE_PARTS:
            case MESSAGE_PART_ATTRIBUTES:
            case ADDRESSES:
            {
                String accountUuid = segments.get(1);
                final String tableName = TABLE_NAMES[match >> 12];
                try {
                    cursor = getDatabase(accountUuid).execute(false, new DbCallback<Cursor>() {
                        @Override
                        public Cursor doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.query(tableName, projection, selection, selectionArgs, null,
                                    null, sortOrder);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case FOLDER_ID:
            case MESSAGE_ID:
            case MESSAGE_PART_ID:
            case MESSAGE_PART_ATTRIBUTE_ID:
            case ADDRESS_ID:
            {
                String accountUuid = segments.get(1);
                String id = uri.getLastPathSegment();
                final String tableName = TABLE_NAMES[match >> 12];
                try {
                    final String selectionWithId = whereWithId(id, selection);

                    cursor = getDatabase(accountUuid).execute(false, new DbCallback<Cursor>() {
                        @Override
                        public Cursor doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.query(tableName, projection, selectionWithId,
                                    selectionArgs, null, null, sortOrder);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case MESSAGE_PARTS_BY_MESSAGE_ID:
            {
                String accountUuid = segments.get(1);
                String id = uri.getLastPathSegment();
                cursor = getMessageParts(accountUuid, id, projection, selection, selectionArgs);
                break;
            }
            case ACCOUNT_STATS:
            {
                final String accountUuid = segments.get(1);
                try {
                    Context context = getContext();
                    final StorageManager storageManager = StorageManager.getInstance(context);
                    Account account = Preferences.getPreferences(context).getAccount(accountUuid);
                    final String storageProviderId = account.getLocalStorageProviderId();
                    final File attachmentDirectory = storageManager.getAttachmentDirectory(
                            accountUuid, storageProviderId);

                    long size = getDatabase(accountUuid).execute(false, new DbCallback<Long>() {
                        @Override
                        public Long doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            final File[] files = attachmentDirectory.listFiles();
                            long attachmentLength = 0;
                            for (File file : files) {
                                if (file.exists()) {
                                    attachmentLength += file.length();
                                }
                            }

                            final File dbFile = storageManager.getDatabase(accountUuid, storageProviderId);
                            return dbFile.length() + attachmentLength;
                        }
                    });

                    MatrixCursor matrixCursor = new MatrixCursor(EmailProviderConstants.ACCOUNT_STATS_PROJECTION);
                    matrixCursor.addRow(new Object[] { size });
                    cursor = matrixCursor;
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }

        /*
        if (cursor != null && !isTemporary()) {
            cursor.setNotificationUri(getContext().getContentResolver(), notificationUri);
        }
        */
        return cursor;
    }

    private Cursor getMessageParts(String accountUuid, String messageId, final String[] projection,
                                   final String selection, final String[] selectionArgs) {
        Cursor cursor = null;
        try {
            StringBuffer sb = new StringBuffer();
            sb.append("SELECT ");
            boolean first = true;
            for (String field : projection) {
                if (!first) {
                    sb.append(',');
                } else {
                    first = false;
                }
                sb.append("p.");
                sb.append(field);
                sb.append(" AS ");
                sb.append(field);
            }
            sb.append(" FROM ");
            sb.append(MESSAGES_TABLE);
            sb.append(" AS m JOIN ");
            sb.append(MESSAGE_PARTS_TABLE);
            //TODO: make this work when the message id of a sub-message is used
            sb.append(" AS p ON (m.id = p.message_id) WHERE (m.id = ? OR m.root = ?)");
            if (selection != null) {
                sb.append(" AND (");
                //sb.append(modifiedSelection);
                sb.append(selection);
                sb.append(')');
            }
            //TODO: respect sortOrder argument
            sb.append(" ORDER BY p.parent, p.seq");

            final String query = sb.toString();

            int len = (selectionArgs == null) ? 0 : selectionArgs.length;
            final String[] modifiedSelectionArgs = new String[2 + len];
            modifiedSelectionArgs[0] = messageId;
            modifiedSelectionArgs[1] = messageId;
            if (len > 0) {
                System.arraycopy(selectionArgs, 0, modifiedSelectionArgs, 2, len);
            }

            cursor = getDatabase(accountUuid).execute(false, new DbCallback<Cursor>() {
                @Override
                public Cursor doDbWork(SQLiteDatabase db) throws WrappedException,
                        UnavailableStorageException {

                    return db.rawQuery(query, modifiedSelectionArgs);
                }
            });
        } catch (UnavailableStorageException e) {
            throw new RuntimeException(e);
        }
        return cursor;
    }

    @Override
    public int delete(Uri uri, final String selection, final String[] selectionArgs) {
        int result = 0;
        int match = sURIMatcher.match(uri);
        List<String> segments = uri.getPathSegments();
        switch (match) {
            case FOLDERS:
            case MESSAGE_PARTS:
            case MESSAGE_PART_ATTRIBUTES:
            case ADDRESSES:
            {
                String accountUuid = segments.get(1);
                final String tableName = TABLE_NAMES[match >> 12];
                try {
                    result = getDatabase(accountUuid).execute(false, new DbCallback<Integer>() {
                        @Override
                        public Integer doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.delete(tableName, selection, selectionArgs);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case MESSAGES:
            {
                String accountUuid = segments.get(1);
                try {
                    result = getDatabase(accountUuid).execute(false, new DbCallback<Integer>() {
                        @Override
                        public Integer doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {

                            //TODO: delete all message parts etc.

                            return db.delete(MESSAGES_TABLE, selection, selectionArgs);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }

        return result;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        Uri resultUri = null;
        int match = sURIMatcher.match(uri);
        List<String> segments = uri.getPathSegments();
        switch (match) {
            case FOLDERS:
            case MESSAGES:
            case MESSAGE_PARTS:
            case ADDRESSES:
            {
                String accountUuid = segments.get(1);
                final String tableName = TABLE_NAMES[match >> 12];
                long id = 0;
                try {
                    id = getDatabase(accountUuid).execute(false, new DbCallback<Long>() {
                        @Override
                        public Long doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.insert(tableName, null, values);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }

                resultUri = ContentUris.withAppendedId(uri, id);
                break;
            }
            default:
            {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }

        return resultUri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case MESSAGE_PART_ID:
            {
                List<String> segments = uri.getPathSegments();
                String accountUuid = segments.get(1);
                long partId = ContentUris.parseId(uri);
                Context context = getContext();
                Account account = Preferences.getPreferences(context).getAccount(accountUuid);
                File dir = StorageManager.getInstance(context).getAttachmentDirectory(accountUuid, account.getLocalStorageProviderId());
                File file = new File(dir, Long.toString(partId));
                //TODO: only create if there's an entry in message_parts
                if (!file.exists()) {
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                //TODO: respect "mode" argument
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE);
            }
            default:
            {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }
    }

    private String whereWithId(String id, String selection) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("id=");
        sb.append(id);
        if (selection != null) {
            sb.append(" AND (");
            sb.append(selection);
            sb.append(')');
        }
        return sb.toString();
    }

    private void checkAccountUuidValidity(String uuid) {
        Account account = Preferences.getPreferences(mContext).getAccount(uuid);
        if (account == null) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" is not a valid account UUID", uuid));
        }
    }

    public void deleteDatabase(String accountUuid) throws UnavailableStorageException {
        getDatabase(accountUuid).delete();
    }

    private static class StoreSchemaDefinition implements LockableDatabase.SchemaDefinition {
        @Override
        public int getVersion() {
            return DB_VERSION;
        }

        @Override
        public void doDbUpgrade(final SQLiteDatabase db) {
            if (db.getVersion() < 43) {
                db.execSQL("DROP TABLE IF EXISTS folders");
                db.execSQL("CREATE TABLE folders (" +
                        "id INTEGER PRIMARY KEY," +
                        "name TEXT," +
                        "local_only BOOLEAN," +
                        "unread_count INTEGER," +
                        "flagged_count INTEGER," +
                        "integrate BOOLEAN," +
                        "top_group BOOLEAN," +
                        "display_class TEXT," +
                        "visible_limit TEXT)");

                db.execSQL("DROP TABLE IF EXISTS folder_attributes");
                db.execSQL("CREATE TABLE folder_attributes (" +
                        "id INTEGER PRIMARY KEY," +
                        "folder_id INTEGER," +
                        "key TEXT," +
                        "value TEXT)");

                db.execSQL("DROP TABLE IF EXISTS messages");
                db.execSQL("CREATE TABLE messages (" +
                        "id INTEGER PRIMARY KEY," +
                        "folder_id INTEGER," +
                        "uid TEXT," +
                        "root INTEGER," +
                        "parent INTEGER," +
                        "seq INTEGER," +
                        "local_only BOOLEAN," +
                        "deleted BOOLEAN," +
                        "notified BOOLEAN," +
                        "date INTEGER," +
                        "internal_date INTEGER," +
                        "seen BOOLEAN," +
                        "flagged BOOLEAN," +
                        "answered BOOLEAN," +
                        "forwarded BOOLEAN," +
                        "destroyed BOOLEAN," +
                        "send_failed BOOLEAN," +
                        "send_in_progress BOOLEAN," +
                        "downloaded_full BOOLEAN," +
                        "downloaded_partial BOOLEAN," +
                        "remote_copy_started BOOLEAN," +
                        "got_all_headers BOOLEAN)");

                db.execSQL("DROP TABLE IF EXISTS addresses");
                db.execSQL("CREATE TABLE addresses (" +
                        "id INTEGER PRIMARY KEY," +
                        "message_id INTEGER," +
                        "type INTEGER," +
                        "name TEXT," +
                        "email TEXT)");

                db.execSQL("DROP TABLE IF EXISTS message_parts");
                db.execSQL("CREATE TABLE message_parts (" +
                        "id INTEGER PRIMARY KEY," +
                        "message_id INTEGER," +
                        "type INTEGER," +
                        "mime_type TEXT," +
                        "parent INTEGER," +
                        "seq INTEGER," +
                        "size INTEGER," +
                        "data_type INTEGER," +
                        "data TEXT," +
                        "header TEXT," +
                        "preamble TEXT," +
                        "epilogue TEXT," +
                        "complete BOOLEAN)");

                db.execSQL("DROP TABLE IF EXISTS message_part_attributes");
                db.execSQL("CREATE TABLE message_part_attributes (" +
                        "id INTEGER PRIMARY KEY," +
                        "message_part_id INTEGER," +
                        "key TEXT," +
                        "value TEXT)");

                db.execSQL("DROP TABLE IF EXISTS message_cache");
                db.execSQL("CREATE TABLE message_cache (" +
                        "id INTEGER PRIMARY KEY," +
                        "message_id INTEGER," +
                        "subject TEXT," +
                        "preview TEXT)");

                db.execSQL("DROP TABLE IF EXISTS message_part_cache");
                db.execSQL("CREATE TABLE message_part_cache (" +
                        "id INTEGER PRIMARY KEY," +
                        "message_part_id INTEGER," +
                        "type INTEGER," +
                        "name TEXT," +
                        "size INTEGER," +
                        "data_type INTEGER," +
                        "data TEXT," +
                        "seq INTEGER," +
                        "content_id TEXT)");

                db.execSQL("DROP TABLE IF EXISTS pending_commands");
                db.execSQL("CREATE TABLE pending_commands (" +
                        "id INTEGER PRIMARY KEY," +
                        "command TEXT," +
                        "arguments TEXT)");

                db.execSQL("CREATE INDEX IF NOT EXISTS folder_name ON folders (name)");
            }
        }
    }
}
