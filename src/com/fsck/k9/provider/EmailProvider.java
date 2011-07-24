package com.fsck.k9.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;
import com.fsck.k9.mail.store.LockableDatabase;
import com.fsck.k9.mail.store.UnavailableStorageException;
import com.fsck.k9.mail.store.LockableDatabase.DbCallback;
import com.fsck.k9.mail.store.LockableDatabase.WrappedException;

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


    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // Email URI matching table
        UriMatcher matcher = sURIMatcher;

        // Folders of a specific account
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/folder", FOLDERS);

        // A specific folder
        matcher.addURI(EmailProviderConstants.AUTHORITY, "account/*/folder/#", FOLDER_ID);
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
            {
                String accountUuid = segments.get(1);
                try {
                    cursor = getDatabase(accountUuid).execute(false, new DbCallback<Cursor>() {
                        @Override
                        public Cursor doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.query("folders", projection, selection, selectionArgs, null,
                                    null, sortOrder);
                        }
                    });
                } catch (UnavailableStorageException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            case FOLDER_ID:
            {
                String accountUuid = segments.get(1);
                String id = uri.getLastPathSegment();
                try {
                    final String selectionWithId = whereWithId(
                            EmailProviderConstants.FolderColumns.ID, id, selection);

                    cursor = getDatabase(accountUuid).execute(false, new DbCallback<Cursor>() {
                        @Override
                        public Cursor doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.query("folders", projection, selectionWithId,
                                    selectionArgs, null, null, sortOrder);
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

        /*
        if (cursor != null && !isTemporary()) {
            cursor.setNotificationUri(getContext().getContentResolver(), notificationUri);
        }
        */
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        Uri resultUri = null;
        int match = sURIMatcher.match(uri);
        List<String> segments = uri.getPathSegments();
        switch (match) {
            case FOLDERS:
            {
                String accountUuid = segments.get(1);
                long id = 0;
                try {
                    id = getDatabase(accountUuid).execute(false, new DbCallback<Long>() {
                        @Override
                        public Long doDbWork(SQLiteDatabase db) throws WrappedException,
                                UnavailableStorageException {
                            return db.insert("folders", null, values);
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


    private String whereWithId(String pk, String id, String selection) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(pk);
        sb.append('=');
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

    protected void deleteDatabase(String accountUuid) throws UnavailableStorageException {
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
