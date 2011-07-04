
package com.fsck.k9.mail.store;

import java.io.*;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import com.fsck.k9.helper.HtmlConverter;
import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeEntityConfig;
import org.apache.james.mime4j.stream.RawField;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.controller.MessageRemovalListener;
import com.fsck.k9.controller.MessageRetrievalListener;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.BodyPart;
import com.fsck.k9.mail.FetchProfile;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Message.RecipientType;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Multipart;
import com.fsck.k9.mail.Part;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.filter.Base64OutputStream;
import com.fsck.k9.mail.internet.MimeBodyPart;
import com.fsck.k9.mail.internet.MimeHeader;
import com.fsck.k9.mail.internet.MimeMessage;
import com.fsck.k9.mail.internet.MimeMultipart;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.mail.internet.TextBody;
import com.fsck.k9.mail.store.LockableDatabase.DbCallback;
import com.fsck.k9.mail.store.LockableDatabase.WrappedException;
import com.fsck.k9.mail.store.StorageManager.StorageProvider;
import com.fsck.k9.provider.AttachmentProvider;

/**
 * Implements a SQLite database backed local store for Messages.
 *
 * <p>
 * <strong>Note:</strong>
 * This is the first attempt at using the new database schema. The goal is to store messages in
 * a format that allows to reconstruct a byte-identical copy of the original message.<br/>
 * Right now this goal is not reached. The transport encoding is removed from the message parts
 * before the message reaches LocalStore. So currently the messages are stored in decoded form.
 * </p><p>
 * Because the plan is to get rid of LocalStore in favor of a ContentProvider-based solution I
 * decided to stop work on modifying LocalStore to meet all the goals associated with the new
 * database format.<br/>
 * Instead I will concentrate my efforts on implementing the ContentProvider.
 * </p>
 * -- cketti
 */
public class LocalStore extends Store implements Serializable {

    private static final long serialVersionUID = -5142141896809423072L;

    private static final Message[] EMPTY_MESSAGE_ARRAY = new Message[0];
    private static final Address[] EMPTY_ADDRESS_ARRAY = new Address[0];

    /**
     * Immutable empty {@link String} array
     */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final Flag[] PERMANENT_FLAGS = { Flag.DELETED, Flag.X_DESTROYED, Flag.SEEN, Flag.FLAGGED };

    private static Set<String> HEADERS_TO_SAVE = new HashSet<String>();
    static {
        HEADERS_TO_SAVE.add(K9.IDENTITY_HEADER);
        HEADERS_TO_SAVE.add("To");
        HEADERS_TO_SAVE.add("Cc");
        HEADERS_TO_SAVE.add("From");
        HEADERS_TO_SAVE.add("In-Reply-To");
        HEADERS_TO_SAVE.add("References");
        HEADERS_TO_SAVE.add("Content-ID");
        HEADERS_TO_SAVE.add("Content-Disposition");
        HEADERS_TO_SAVE.add("User-Agent");
    }
    /*
     * a String containing the columns getMessages expects to work with
     * in the correct order.
     */
    private static final String GET_MESSAGES_COLS =
        "m.id, m.uid, m.deleted, m.date, m.seen, m.flagged, m.answered, m.forwarded, " +
        "m.destroyed, m.send_failed, m.send_in_progress,  m.downloaded_full, " +
        "m.downloaded_partial, m.remote_copy_started,  m.got_all_headers, c.subject, c.preview, " +
        "COUNT(pc.id), p2.mime_type, m.folder_id";

    private static final String GET_MESSAGES_BASE_QUERY =
        "SELECT " + GET_MESSAGES_COLS + " " +
        "FROM messages AS m " +
        "LEFT JOIN message_cache AS c ON (m.id = c.message_id) " +
        "LEFT JOIN message_parts AS p ON (m.id = p.message_id) " +
        "LEFT JOIN message_part_cache AS pc ON (p.id = pc.message_part_id AND pc.type = 3) " +
        "LEFT JOIN message_parts AS p2 ON (m.id = p2.message_id AND p2.parent IS NULL) " +
        "WHERE %s GROUP BY m.id%s";

    private static final String GET_FOLDER_COLS =
        "folders.id," +
        "folders.name," +
        "folders.local_only," +
        "folders.unread_count," +
        "folders.flagged_count," +
        "folders.integrate," +
        "folders.top_group," +
        "folders.display_class," +
        "folders.visible_limit," +
        "folder_attributes.key," +
        "folder_attributes.value";


    protected static final int DB_VERSION = 43;

    private static String flagToDatabaseField(Flag flag) {
        String field;

        switch (flag) {
            case ANSWERED: field = "answered"; break;
            case DELETED: field = "deleted"; break;
            case FLAGGED: field = "answered"; break;
            case SEEN: field = "seen"; break;
            case X_DESTROYED: field = "destroyed"; break;
            case X_DOWNLOADED_FULL: field = "downloaded_full"; break;
            case X_DOWNLOADED_PARTIAL: field = "downloaded_partial"; break;
            case X_GOT_ALL_HEADERS: field = "got_all_headers"; break;
            case X_REMOTE_COPY_STARTED: field = "remote_copy_started"; break;
            case X_SEND_FAILED: field = "send_failed"; break;
            case X_SEND_IN_PROGRESS: field = "send_in_progress"; break;
            default: field = null; break;
        }

        return field;
    }


    protected String uUid = null;

    private final Application mApplication;

    private LockableDatabase database;

    /**
     * local://localhost/path/to/database/uuid.db
     * This constructor is only used by {@link Store#getLocalInstance(Account, Application)}
     * @param account
     * @param application
     * @throws UnavailableStorageException if not {@link StorageProvider#isReady(Context)}
     */
    public LocalStore(final Account account, final Application application) throws MessagingException {
        super(account);
        database = new LockableDatabase(application, account.getUuid(), new StoreSchemaDefinition());

        mApplication = application;
        database.setStorageProviderId(account.getLocalStorageProviderId());
        uUid = account.getUuid();

        database.open();
    }

    public void switchLocalStorage(final String newStorageProviderId) throws MessagingException {
        database.switchProvider(newStorageProviderId);
    }

    protected SharedPreferences getPreferences() {
        return Preferences.getPreferences(mApplication).getPreferences();
    }

    private class StoreSchemaDefinition implements LockableDatabase.SchemaDefinition {
        @Override
        public int getVersion() {
            return DB_VERSION;
        }

        @Override
        public void doDbUpgrade(final SQLiteDatabase db) {
            Log.i(K9.LOG_TAG, String.format("Upgrading database from version %d to version %d",
                                            db.getVersion(), DB_VERSION));


            AttachmentProvider.clear(mApplication);

            try {
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

                    /*
                    db.execSQL("DROP TABLE IF EXISTS messages");
                    db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, deleted INTEGER default 0, folder_id INTEGER, uid TEXT, subject TEXT, "
                               + "date INTEGER, flags TEXT, sender_list TEXT, to_list TEXT, cc_list TEXT, bcc_list TEXT, reply_to_list TEXT, "
                               + "html_content TEXT, text_content TEXT, attachment_count INTEGER, internal_date INTEGER, message_id TEXT, preview TEXT, "
                               + "mime_type TEXT)");

                    db.execSQL("DROP TABLE IF EXISTS headers");
                    db.execSQL("CREATE TABLE headers (id INTEGER PRIMARY KEY, message_id INTEGER, name TEXT, value TEXT)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS header_folder ON headers (message_id)");

                    db.execSQL("CREATE INDEX IF NOT EXISTS msg_uid ON messages (uid, folder_id)");
                    db.execSQL("DROP INDEX IF EXISTS msg_folder_id");
                    db.execSQL("DROP INDEX IF EXISTS msg_folder_id_date");
                    db.execSQL("CREATE INDEX IF NOT EXISTS msg_folder_id_deleted_date ON messages (folder_id,deleted,internal_date)");
                    db.execSQL("DROP TABLE IF EXISTS attachments");
                    db.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY, message_id INTEGER,"
                               + "store_data TEXT, content_uri TEXT, size INTEGER, name TEXT,"
                               + "mime_type TEXT, content_id TEXT, content_disposition TEXT)");
                    db.execSQL("DROP TABLE IF EXISTS pending_commands");
                    db.execSQL("CREATE TABLE pending_commands " +
                               "(id INTEGER PRIMARY KEY, command TEXT, arguments TEXT)");

                    db.execSQL("DROP TRIGGER IF EXISTS delete_folder");
                    db.execSQL("CREATE TRIGGER delete_folder BEFORE DELETE ON folders BEGIN DELETE FROM messages WHERE old.id = folder_id; END;");

                    db.execSQL("DROP TRIGGER IF EXISTS delete_message");
                    db.execSQL("CREATE TRIGGER delete_message BEFORE DELETE ON messages BEGIN DELETE FROM attachments WHERE old.id = message_id; "
                               + "DELETE FROM headers where old.id = message_id; END;");
                    */
                }
                /*
                // schema version 29 was when we moved to incremental updates
                // in the case of a new db or a < v29 db, we blow away and start from scratch
                if (db.getVersion() < 29) {

                    db.execSQL("DROP TABLE IF EXISTS folders");
                    db.execSQL("CREATE TABLE folders (id INTEGER PRIMARY KEY, name TEXT, "
                               + "last_updated INTEGER, unread_count INTEGER, visible_limit INTEGER, status TEXT, "
                               + "push_state TEXT, last_pushed INTEGER, flagged_count INTEGER default 0, "
                               + "integrate INTEGER, top_group INTEGER, poll_class TEXT, push_class TEXT, display_class TEXT"
                               + ")");

                    db.execSQL("CREATE INDEX IF NOT EXISTS folder_name ON folders (name)");
                    db.execSQL("DROP TABLE IF EXISTS messages");
                    db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, deleted INTEGER default 0, folder_id INTEGER, uid TEXT, subject TEXT, "
                               + "date INTEGER, flags TEXT, sender_list TEXT, to_list TEXT, cc_list TEXT, bcc_list TEXT, reply_to_list TEXT, "
                               + "html_content TEXT, text_content TEXT, attachment_count INTEGER, internal_date INTEGER, message_id TEXT, preview TEXT, "
                               + "mime_type TEXT)");

                    db.execSQL("DROP TABLE IF EXISTS headers");
                    db.execSQL("CREATE TABLE headers (id INTEGER PRIMARY KEY, message_id INTEGER, name TEXT, value TEXT)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS header_folder ON headers (message_id)");

                    db.execSQL("CREATE INDEX IF NOT EXISTS msg_uid ON messages (uid, folder_id)");
                    db.execSQL("DROP INDEX IF EXISTS msg_folder_id");
                    db.execSQL("DROP INDEX IF EXISTS msg_folder_id_date");
                    db.execSQL("CREATE INDEX IF NOT EXISTS msg_folder_id_deleted_date ON messages (folder_id,deleted,internal_date)");
                    db.execSQL("DROP TABLE IF EXISTS attachments");
                    db.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY, message_id INTEGER,"
                               + "store_data TEXT, content_uri TEXT, size INTEGER, name TEXT,"
                               + "mime_type TEXT, content_id TEXT, content_disposition TEXT)");

                    db.execSQL("DROP TABLE IF EXISTS pending_commands");
                    db.execSQL("CREATE TABLE pending_commands " +
                               "(id INTEGER PRIMARY KEY, command TEXT, arguments TEXT)");

                    db.execSQL("DROP TRIGGER IF EXISTS delete_folder");
                    db.execSQL("CREATE TRIGGER delete_folder BEFORE DELETE ON folders BEGIN DELETE FROM messages WHERE old.id = folder_id; END;");

                    db.execSQL("DROP TRIGGER IF EXISTS delete_message");
                    db.execSQL("CREATE TRIGGER delete_message BEFORE DELETE ON messages BEGIN DELETE FROM attachments WHERE old.id = message_id; "
                               + "DELETE FROM headers where old.id = message_id; END;");
                } else {
                    // in the case that we're starting out at 29 or newer, run all the needed updates

                    if (db.getVersion() < 30) {
                        try {
                            db.execSQL("ALTER TABLE messages ADD deleted INTEGER default 0");
                        } catch (SQLiteException e) {
                            if (! e.toString().startsWith("duplicate column name: deleted")) {
                                throw e;
                            }
                        }
                    }
                    if (db.getVersion() < 31) {
                        db.execSQL("DROP INDEX IF EXISTS msg_folder_id_date");
                        db.execSQL("CREATE INDEX IF NOT EXISTS msg_folder_id_deleted_date ON messages (folder_id,deleted,internal_date)");
                    }
                    if (db.getVersion() < 32) {
                        db.execSQL("UPDATE messages SET deleted = 1 WHERE flags LIKE '%DELETED%'");
                    }
                    if (db.getVersion() < 33) {

                        try {
                            db.execSQL("ALTER TABLE messages ADD preview TEXT");
                        } catch (SQLiteException e) {
                            if (! e.toString().startsWith("duplicate column name: preview")) {
                                throw e;
                            }
                        }

                    }
                    if (db.getVersion() < 34) {
                        try {
                            db.execSQL("ALTER TABLE folders ADD flagged_count INTEGER default 0");
                        } catch (SQLiteException e) {
                            if (! e.getMessage().startsWith("duplicate column name: flagged_count")) {
                                throw e;
                            }
                        }
                    }
                    if (db.getVersion() < 35) {
                        try {
                            db.execSQL("update messages set flags = replace(flags, 'X_NO_SEEN_INFO', 'X_BAD_FLAG')");
                        } catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Unable to get rid of obsolete flag X_NO_SEEN_INFO", e);
                        }
                    }
                    if (db.getVersion() < 36) {
                        try {
                            db.execSQL("ALTER TABLE attachments ADD content_id TEXT");
                        } catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Unable to add content_id column to attachments");
                        }
                    }
                    if (db.getVersion() < 37) {
                        try {
                            db.execSQL("ALTER TABLE attachments ADD content_disposition TEXT");
                        } catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Unable to add content_disposition column to attachments");
                        }
                    }

                    // Database version 38 is solely to prune cached attachments now that we clear them better
                    if (db.getVersion() < 39) {
                        try {
                            db.execSQL("DELETE FROM headers WHERE id in (SELECT headers.id FROM headers LEFT JOIN messages ON headers.message_id = messages.id WHERE messages.id IS NULL)");
                        } catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Unable to remove extra header data from the database");
                        }
                    }

                    // V40: Store the MIME type for a message.
                    if (db.getVersion() < 40) {
                        try {
                            db.execSQL("ALTER TABLE messages ADD mime_type TEXT");
                        } catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Unable to add mime_type column to messages");
                        }
                    }

                    if (db.getVersion() < 41) {
                        try {
                            db.execSQL("ALTER TABLE folders ADD integrate INTEGER");
                            db.execSQL("ALTER TABLE folders ADD top_group INTEGER");
                            db.execSQL("ALTER TABLE folders ADD poll_class TEXT");
                            db.execSQL("ALTER TABLE folders ADD push_class TEXT");
                            db.execSQL("ALTER TABLE folders ADD display_class TEXT");
                        } catch (SQLiteException e) {
                            if (! e.getMessage().startsWith("duplicate column name:")) {
                                throw e;
                            }
                        }
                        Cursor cursor = null;

                        try {

                            SharedPreferences prefs = getPreferences();
                            cursor = db.rawQuery("SELECT id, name FROM folders", null);
                            while (cursor.moveToNext()) {
                                try {
                                    int id = cursor.getInt(0);
                                    String name = cursor.getString(1);
                                    update41Metadata(db, prefs, id, name);
                                } catch (Exception e) {
                                    Log.e(K9.LOG_TAG, " error trying to ugpgrade a folder class: " + e);
                                }
                            }
                        }


                        catch (SQLiteException e) {
                            Log.e(K9.LOG_TAG, "Exception while upgrading database to v41. folder classes may have vanished " + e);

                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                    if (db.getVersion() == 41) {
                        try {
                            long startTime = System.currentTimeMillis();
                            SharedPreferences.Editor editor = getPreferences().edit();

                            List <? extends Folder >  folders = getPersonalNamespaces(true);
                            for (Folder folder : folders) {
                                if (folder instanceof LocalFolder) {
                                    LocalFolder lFolder = (LocalFolder)folder;
                                    lFolder.save(editor);
                                }
                            }

                            editor.commit();
                            long endTime = System.currentTimeMillis();
                            Log.i(K9.LOG_TAG, "Putting folder preferences for " + folders.size() + " folders back into Preferences took " + (endTime - startTime) + " ms");
                        } catch (Exception e) {
                            Log.e(K9.LOG_TAG, "Could not replace Preferences in upgrade from DB_VERSION 41", e);
                        }
                    }
                }
                */
            }

            catch (SQLiteException e) {
                Log.e(K9.LOG_TAG, "Exception while upgrading database. Resetting the DB to v0");
                db.setVersion(0);
                throw new Error("Database upgrade failed! Resetting your DB version to 0 to force a full schema recreation.");
            }



            db.setVersion(DB_VERSION);

            if (db.getVersion() != DB_VERSION) {
                throw new Error("Database upgrade failed!");
            }

            // Unless we're blowing away the whole data store, there's no reason to prune attachments
            // every time the user upgrades. it'll just cost them money and pain.
            // try
            //{
            //        pruneCachedAttachments(true);
            //}
            //catch (Exception me)
            //{
            //   Log.e(K9.LOG_TAG, "Exception while force pruning attachments during DB update", me);
            //}
        }

        /*
        private void update41Metadata(final SQLiteDatabase  db, SharedPreferences prefs, int id, String name) {


            Folder.FolderClass displayClass = Folder.FolderClass.NO_CLASS;
            Folder.FolderClass syncClass = Folder.FolderClass.INHERITED;
            Folder.FolderClass pushClass = Folder.FolderClass.SECOND_CLASS;
            boolean inTopGroup = false;
            boolean integrate = false;
            if (mAccount.getInboxFolderName().equals(name)) {
                displayClass = Folder.FolderClass.FIRST_CLASS;
                syncClass =  Folder.FolderClass.FIRST_CLASS;
                pushClass =  Folder.FolderClass.FIRST_CLASS;
                inTopGroup = true;
                integrate = true;
            }

            try {
                displayClass = Folder.FolderClass.valueOf(prefs.getString(uUid + "." + name + ".displayMode", displayClass.name()));
                syncClass = Folder.FolderClass.valueOf(prefs.getString(uUid + "." + name + ".syncMode", syncClass.name()));
                pushClass = Folder.FolderClass.valueOf(prefs.getString(uUid + "." + name + ".pushMode", pushClass.name()));
                inTopGroup = prefs.getBoolean(uUid + "." + name + ".inTopGroup", inTopGroup);
                integrate = prefs.getBoolean(uUid + "." + name + ".integrate", integrate);
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, " Throwing away an error while trying to upgrade folder metadata: " + e);
            }

            if (displayClass == Folder.FolderClass.NONE) {
                displayClass = Folder.FolderClass.NO_CLASS;
            }
            if (syncClass == Folder.FolderClass.NONE) {
                syncClass = Folder.FolderClass.INHERITED;
            }
            if (pushClass == Folder.FolderClass.NONE) {
                pushClass = Folder.FolderClass.INHERITED;
            }

            db.execSQL("UPDATE folders SET integrate = ?, top_group = ?, poll_class=?, push_class =?, display_class = ? WHERE id = ?",
                       new Object[] { integrate, inTopGroup, syncClass, pushClass, displayClass, id });

        }
        */
    }


    public long getSize() throws UnavailableStorageException {

        final StorageManager storageManager = StorageManager.getInstance(mApplication);

        final File attachmentDirectory = storageManager.getAttachmentDirectory(uUid,
                                         database.getStorageProviderId());

        return database.execute(false, new DbCallback<Long>() {
            @Override
            public Long doDbWork(final SQLiteDatabase db) {
                final File[] files = attachmentDirectory.listFiles();
                long attachmentLength = 0;
                for (File file : files) {
                    if (file.exists()) {
                        attachmentLength += file.length();
                    }
                }

                final File dbFile = storageManager.getDatabase(uUid, database.getStorageProviderId());
                return dbFile.length() + attachmentLength;
            }
        });
    }

    public void compact() throws MessagingException {
        if (K9.DEBUG)
            Log.i(K9.LOG_TAG, "Before compaction size = " + getSize());

        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                db.execSQL("VACUUM");
                return null;
            }
        });
        if (K9.DEBUG)
            Log.i(K9.LOG_TAG, "After compaction size = " + getSize());
    }


    public void clear() throws MessagingException {
        if (K9.DEBUG)
            Log.i(K9.LOG_TAG, "Before prune size = " + getSize());

        pruneCachedAttachments(true);
        if (K9.DEBUG) {
            Log.i(K9.LOG_TAG, "After prune / before compaction size = " + getSize());

            Log.i(K9.LOG_TAG, "Before clear folder count = " + getFolderCount());
            Log.i(K9.LOG_TAG, "Before clear message count = " + getMessageCount());

            Log.i(K9.LOG_TAG, "After prune / before clear size = " + getSize());
        }
        // don't delete messages that are Local, since there is no copy on the server.
        // Don't delete deleted messages.  They are essentially placeholders for UIDs of messages that have
        // been deleted locally.  They take up insignificant space
        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) {
                db.delete("messages", "deleted = 0 AND local_only = 0", null);

                ContentValues cv = new ContentValues();
                cv.put("flagged_count", 0);
                cv.put("unread_count", 0);
                db.update("folders", cv, null, null);
                return null;
            }
        });

        compact();

        if (K9.DEBUG) {
            Log.i(K9.LOG_TAG, "After clear message count = " + getMessageCount());

            Log.i(K9.LOG_TAG, "After clear size = " + getSize());
        }
    }

    public int getMessageCount() throws MessagingException {
        return database.execute(false, new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(final SQLiteDatabase db) {
                Cursor cursor = null;
                try {
                    cursor = db.rawQuery("SELECT COUNT(*) FROM messages", null);
                    cursor.moveToFirst();
                    return cursor.getInt(0);   // message count
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }

    public void getMessageCounts(final AccountStats stats) throws MessagingException {
        final Account.FolderMode displayMode = mAccount.getFolderDisplayMode();

        database.execute(false, new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(final SQLiteDatabase db) {
                Cursor cursor = null;
                try {
                    // Always count messages in the INBOX but exclude special folders and possibly
                    // more (depending on the folder display mode)
                    String baseQuery = "SELECT SUM(unread_count), SUM(flagged_count) " +
                            "FROM folders " +
                            "WHERE (name = ?)" +  /* INBOX */
                            " OR (" +
                            "name NOT IN (?, ?, ?, ?, ?)" +  /* special folders */
                            "%s)";  /* placeholder for additional constraints */

                    List<String> queryParam = new ArrayList<String>();
                    queryParam.add(mAccount.getInboxFolderName());

                    queryParam.add((mAccount.getTrashFolderName() != null) ?
                            mAccount.getTrashFolderName() : "");
                    queryParam.add((mAccount.getDraftsFolderName() != null) ?
                            mAccount.getDraftsFolderName() : "");
                    queryParam.add((mAccount.getSpamFolderName() != null) ?
                            mAccount.getSpamFolderName() : "");
                    queryParam.add((mAccount.getOutboxFolderName() != null) ?
                            mAccount.getOutboxFolderName() : "");
                    queryParam.add((mAccount.getSentFolderName() != null) ?
                            mAccount.getSentFolderName() : "");

                    final String extraWhere;
                    switch (displayMode) {
                        case FIRST_CLASS:
                            // Count messages in the INBOX and non-special first class folders
                            extraWhere = " AND (display_class = ?)";
                            queryParam.add(Folder.FolderClass.FIRST_CLASS.name());
                            break;
                        case FIRST_AND_SECOND_CLASS:
                            // Count messages in the INBOX and non-special first and second class folders
                            extraWhere = " AND (display_class IN (?, ?))";
                            queryParam.add(Folder.FolderClass.FIRST_CLASS.name());
                            queryParam.add(Folder.FolderClass.SECOND_CLASS.name());
                            break;
                        case NOT_SECOND_CLASS:
                            // Count messages in the INBOX and non-special non-second-class folders
                            extraWhere = " AND (display_class != ?)";
                            queryParam.add(Folder.FolderClass.SECOND_CLASS.name());
                            break;
                        case ALL:
                            // Count messages in the INBOX and non-special folders
                            extraWhere = "";
                            break;
                        default:
                            Log.e(K9.LOG_TAG, "asked to compute account statistics for an impossible folder mode " + displayMode);
                            stats.unreadMessageCount = 0;
                            stats.flaggedMessageCount = 0;
                            return null;
                    }

                    String query = String.format(Locale.US, baseQuery, extraWhere);
                    cursor = db.rawQuery(query, queryParam.toArray(EMPTY_STRING_ARRAY));

                    cursor.moveToFirst();
                    stats.unreadMessageCount = cursor.getInt(0);
                    stats.flaggedMessageCount = cursor.getInt(1);
                    return null;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }


    public int getFolderCount() throws MessagingException {
        return database.execute(false, new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(final SQLiteDatabase db) {
                Cursor cursor = null;
                try {
                    cursor = db.rawQuery("SELECT COUNT(*) FROM folders", null);
                    cursor.moveToFirst();
                    return cursor.getInt(0);        // folder count
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }

    @Override
    public LocalFolder getFolder(String name) {
        return new LocalFolder(name);
    }

    // TODO this takes about 260-300ms, seems slow.
    @Override
    public List <? extends Folder > getPersonalNamespaces(boolean forceListAll) throws MessagingException {
        final List<LocalFolder> folders = new LinkedList<LocalFolder>();
        try {
            database.execute(false, new DbCallback < List <? extends Folder >> () {
                @Override
                public List <? extends Folder > doDbWork(final SQLiteDatabase db) throws WrappedException {
                    Cursor cursor = null;

                    try {
                        cursor = db.rawQuery("SELECT " + GET_FOLDER_COLS + " FROM folders " +
                                "LEFT JOIN folder_attributes ON (folders.id = folder_attributes.folder_id) " +
                                "ORDER BY name ASC", null);

                        if (cursor.moveToNext()) {
                            do {
                                LocalFolder folder = new LocalFolder(cursor);
                                folders.add(folder);
                            } while (!cursor.isAfterLast());
                        }
                        return folders;
                    } catch (MessagingException e) {
                        throw new WrappedException(e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            });
        } catch (WrappedException e) {
            throw(MessagingException) e.getCause();
        }
        return folders;
    }

    @Override
    public void checkSettings() throws MessagingException {
    }

    public void delete() throws UnavailableStorageException {
        database.delete();
    }

    public void recreate() throws UnavailableStorageException {
        database.recreate();
    }

    public void pruneCachedAttachments() throws MessagingException {
        pruneCachedAttachments(false);
    }

    /**
     * Deletes all cached attachments for the entire store.
     * @param force
     * @throws com.fsck.k9.mail.MessagingException
     *
     * TODO: don't delete the "external" bodies of all messages parts but only "attachments"
     * OR optimize this since it's only called from clear()
     */
    private void pruneCachedAttachments(final boolean force) throws MessagingException {
        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                if (force) {
                    ContentValues cv = new ContentValues();
                    cv.putNull("data");
                    cv.put("complete", false);
                    db.update("message_parts", cv, null, null);
                }
                final StorageManager storageManager = StorageManager.getInstance(mApplication);
                File[] files = storageManager.getAttachmentDirectory(uUid, database.getStorageProviderId()).listFiles();
                for (File file : files) {
                    if (file.exists()) {
                        if (!force) {
                            Cursor cursor = null;
                            try {
                                cursor = db.rawQuery("SELECT 1 FROM " +
                                        "message_part_attributes AS a " +
                                        "JOIN message_parts AS p ON (a.message_part_id = p.id) " +
                                        "WHERE a.key = ? AND p.data = ?",
                                        new String[] { "imap_bodystructure_id", file.getName() });

                                if (!cursor.moveToNext()) {
                                    if (K9.DEBUG)
                                        Log.d(K9.LOG_TAG, "Attachment " + file.getAbsolutePath() + " has no store data, not deleting");
                                    /*
                                     * If the attachment has no store data it is not recoverable, so
                                     * we won't delete it.
                                     */
                                    continue;
                                }
                            } finally {
                                if (cursor != null) {
                                    cursor.close();
                                }
                            }
                            try {
                                ContentValues cv = new ContentValues();
                                cv.putNull("data");
                                cv.put("complete", false);
                                db.update("message_parts", cv, "data = ?", new String[] { file.getName() });

                            } catch (Exception e) {
                                /*
                                 * If the row has gone away before we got to mark it not-downloaded that's
                                 * okay.
                                 */
                            }
                        }
                        if (!file.delete()) {
                            file.deleteOnExit();
                        }
                    }
                }
                return null;
            }
        });
    }

    public void resetVisibleLimits() throws UnavailableStorageException {
        resetVisibleLimits(mAccount.getDisplayCount());
    }

    public void resetVisibleLimits(int visibleLimit) throws UnavailableStorageException {
        final ContentValues cv = new ContentValues();
        cv.put("visible_limit", Integer.toString(visibleLimit));
        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                db.update("folders", cv, null, null);
                return null;
            }
        });
    }

    public ArrayList<PendingCommand> getPendingCommands() throws UnavailableStorageException {
        return database.execute(false, new DbCallback<ArrayList<PendingCommand>>() {
            @Override
            public ArrayList<PendingCommand> doDbWork(final SQLiteDatabase db) throws WrappedException {
                Cursor cursor = null;
                try {
                    cursor = db.query("pending_commands",
                                      new String[] { "id", "command", "arguments" },
                                      null,
                                      null,
                                      null,
                                      null,
                                      "id ASC");
                    ArrayList<PendingCommand> commands = new ArrayList<PendingCommand>();
                    while (cursor.moveToNext()) {
                        PendingCommand command = new PendingCommand();
                        command.mId = cursor.getLong(0);
                        command.command = cursor.getString(1);
                        String arguments = cursor.getString(2);
                        command.arguments = arguments.split(",");
                        for (int i = 0; i < command.arguments.length; i++) {
                            command.arguments[i] = Utility.fastUrlDecode(command.arguments[i]);
                        }
                        commands.add(command);
                    }
                    return commands;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }

    public void addPendingCommand(PendingCommand command) throws UnavailableStorageException {
        try {
            for (int i = 0; i < command.arguments.length; i++) {
                command.arguments[i] = URLEncoder.encode(command.arguments[i], "UTF-8");
            }
            final ContentValues cv = new ContentValues();
            cv.put("command", command.command);
            cv.put("arguments", Utility.combine(command.arguments, ','));
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                    db.insert("pending_commands", "command", cv);
                    return null;
                }
            });
        } catch (UnsupportedEncodingException usee) {
            throw new Error("Aparently UTF-8 has been lost to the annals of history.");
        }
    }

    public void removePendingCommand(final PendingCommand command) throws UnavailableStorageException {
        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                db.delete("pending_commands", "id = ?", new String[] { Long.toString(command.mId) });
                return null;
            }
        });
    }

    public void removePendingCommands() throws UnavailableStorageException {
        database.execute(false, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                db.delete("pending_commands", null, null);
                return null;
            }
        });
    }

    public static class PendingCommand {
        private long mId;
        public String command;
        public String[] arguments;

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(command);
            sb.append(": ");
            for (String argument : arguments) {
                sb.append(", ");
                sb.append(argument);
                //sb.append("\n");
            }
            return sb.toString();
        }
    }

    @Override
    public boolean isMoveCapable() {
        return true;
    }

    @Override
    public boolean isCopyCapable() {
        return true;
    }

    public Message[] searchForMessages(MessageRetrievalListener listener, String queryString,
                                       List<LocalFolder> folders, Message[] messages, final Flag[] requiredFlags, final Flag[] forbiddenFlags) throws MessagingException {
        List<String> args = new LinkedList<String>();

        String[] queryFields = {"pc2.data", "c.subject", "a.name", "a.email"};

        StringBuilder whereClause = new StringBuilder();
        if (queryString != null && queryString.length() > 0) {
            boolean anyAdded = false;
            String likeString = "%" + queryString + "%";
            whereClause.append(" AND (");
            for (String queryField : queryFields) {

                if (anyAdded) {
                    whereClause.append(" OR ");
                }
                whereClause.append(queryField).append(" LIKE ?");
                args.add(likeString);
                anyAdded = true;
            }


            whereClause.append(" )");
        }
        if (folders != null && folders.size() > 0) {
            whereClause.append(" AND m.folder_id IN (");
            boolean anyAdded = false;
            for (LocalFolder folder : folders) {
                if (anyAdded) {
                    whereClause.append(",");
                }
                anyAdded = true;
                whereClause.append("?");
                args.add(Long.toString(folder.getId()));
            }
            whereClause.append(" )");
        }
        if (messages != null && messages.length > 0) {
            whereClause.append(" AND (");
            boolean anyAdded = false;
            for (Message message : messages) {
                if (anyAdded) {
                    whereClause.append(" OR ");
                }
                anyAdded = true;
                whereClause.append(" (m.uid = ? AND m.folder_id = ?)");
                args.add(message.getUid());
                args.add(Long.toString(((LocalFolder)message.getFolder()).getId()));
            }
            whereClause.append(" )");
        }
        if (forbiddenFlags != null && forbiddenFlags.length > 0) {
            whereClause.append(" AND (");
            boolean anyAdded = false;
            for (Flag flag : forbiddenFlags) {
                String field = flagToDatabaseField(flag);
                if (anyAdded) {
                    whereClause.append(" AND ");
                }
                anyAdded = true;
                whereClause.append("m.").append(field).append(" = 0");
            }
            whereClause.append(")");
        }
        if (requiredFlags != null && requiredFlags.length > 0) {
            whereClause.append(" AND (");
            boolean anyAdded = false;
            for (Flag flag : requiredFlags) {
                String field = flagToDatabaseField(flag);
                if (anyAdded) {
                    whereClause.append(" AND ");
                }
                anyAdded = true;
                whereClause.append("m.").append(field).append(" = 1");
            }
            whereClause.append(")");
        }

        if (K9.DEBUG) {
            Log.v(K9.LOG_TAG, "whereClause = " + whereClause.toString());
            Log.v(K9.LOG_TAG, "args = " + args);
        }

        String baseQuery =
            "SELECT " + GET_MESSAGES_COLS + " " +
            "FROM messages AS m " +
            "LEFT JOIN message_cache AS c ON (m.id = c.message_id) " +
            "LEFT JOIN message_parts AS p ON (m.id = p.message_id) " +
            "LEFT JOIN message_part_cache AS pc ON (p.id = pc.message_part_id AND pc.type = 3) " +
            "LEFT JOIN message_parts AS p2 ON (m.id = p2.message_id AND p2.parent IS NULL) " +
            "LEFT JOIN addresses AS a ON (m.id = a.message_id AND a.type = 'FROM') " +
            "LEFT JOIN message_part_cache AS pc2 ON (p.id = pc2.message_part_id AND pc2.type = 2) " +
            "WHERE deleted = 0 %s GROUP BY m.id ORDER BY m.date DESC";

        String query = String.format(baseQuery, whereClause.toString());

        return getMessages(listener, null, query, args.toArray(EMPTY_STRING_ARRAY));
    }
    /*
     * Given a query string, actually do the query for the messages and
     * call the MessageRetrievalListener for each one
     */
    private Message[] getMessages(
        final MessageRetrievalListener listener,
        final LocalFolder folder,
        final String queryString, final String[] placeHolders
    ) throws MessagingException {
        final ArrayList<LocalMessage> messages = new ArrayList<LocalMessage>();
        final int j = database.execute(false, new DbCallback<Integer>() {
            @Override
            public Integer doDbWork(final SQLiteDatabase db) throws WrappedException {
                Cursor cursor = null;
                int i = 0;
                try {
                    cursor = db.rawQuery(queryString + " LIMIT 10", placeHolders);

                    while (cursor.moveToNext()) {
                        LocalMessage message = new LocalMessage(null, folder);
                        message.populateFromGetMessageCursor(db, cursor);

                        messages.add(message);
                        if (listener != null) {
                            listener.messageFinished(message, i, -1);
                        }
                        i++;
                    }
                    cursor.close();
                    cursor = db.rawQuery(queryString + " LIMIT -1 OFFSET 10", placeHolders);

                    while (cursor.moveToNext()) {
                        LocalMessage message = new LocalMessage(null, folder);
                        message.populateFromGetMessageCursor(db, cursor);

                        messages.add(message);
                        if (listener != null) {
                            listener.messageFinished(message, i, -1);
                        }
                        i++;
                    }
                } catch (Exception e) {
                    Log.e(K9.LOG_TAG, "Got an exception: " + e, e);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                return i;
            }
        });
        if (listener != null) {
            listener.messagesFinished(j);
        }

        return messages.toArray(EMPTY_MESSAGE_ARRAY);

    }

    public AttachmentInfo getAttachmentInfo(final String attachmentId) throws UnavailableStorageException {
        return database.execute(false, new DbCallback<AttachmentInfo>() {
            @Override
            public AttachmentInfo doDbWork(final SQLiteDatabase db) throws WrappedException {
                Cursor cursor = null;
                try {
                    String query =
                        "SELECT c.name, c.size, p.mime_type " +
                        "FROM message_part_cache AS c " +
                        "JOIN message_parts AS p ON (c.message_part_id = p.id) " +
                        "WHERE p.id = ? AND c.type = 3";

                    cursor = db.rawQuery(query, new String[] { attachmentId });
                    if (!cursor.moveToFirst()) {
                        return null;
                    }

                    String name = cursor.getString(0);
                    int size = cursor.getInt(1);
                    String mimeType = cursor.getString(2);

                    final AttachmentInfo attachmentInfo = new AttachmentInfo();
                    attachmentInfo.name = name;
                    attachmentInfo.size = size;
                    attachmentInfo.type = mimeType;
                    return attachmentInfo;
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        });
    }

    public static class AttachmentInfo {
        public String name;
        public int size;
        public String type;
    }

    public void createFolders(final List<LocalFolder> foldersToCreate, final int visibleLimit) throws UnavailableStorageException {
        database.execute(true, new DbCallback<Void>() {
            @Override
            public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                for (LocalFolder folder : foldersToCreate) {
                    String name = folder.getName();
                    final  LocalFolder.PreferencesHolder prefHolder = folder.new PreferencesHolder();

                    // When created, special folders should always be displayed
                    // inbox should be integrated
                    // and the inbox and drafts folders should be syncced by default
                    if (mAccount.isSpecialFolder(name)) {
                        prefHolder.inTopGroup = true;
                        prefHolder.displayClass = LocalFolder.FolderClass.FIRST_CLASS;
                        if (name.equalsIgnoreCase(mAccount.getInboxFolderName())) {
                            prefHolder.integrate = true;
                            prefHolder.pushClass = LocalFolder.FolderClass.FIRST_CLASS;
                        } else {
                            prefHolder.pushClass = LocalFolder.FolderClass.INHERITED;

                        }
                        if (name.equalsIgnoreCase(mAccount.getInboxFolderName()) ||
                                name.equalsIgnoreCase(mAccount.getDraftsFolderName())) {
                            prefHolder.syncClass = LocalFolder.FolderClass.FIRST_CLASS;
                        } else {
                            prefHolder.syncClass = LocalFolder.FolderClass.NO_CLASS;
                        }
                    }
                    folder.refresh(name, prefHolder);   // Recover settings from Preferences

                    ContentValues cv = new ContentValues();
                    cv.put("name", name);
                    cv.put("integrate", prefHolder.integrate ? 1 : 0);
                    cv.put("top_group", prefHolder.inTopGroup ? 1 : 0);
                    cv.put("display_class", prefHolder.displayClass.name());
                    cv.put("visible_limit", visibleLimit);
                    long folderId = db.insert("folders", null, cv);

                    cv.clear();
                    cv.put("folder_id", folderId);

                    cv.put("key", "poll_class");
                    cv.put("value", prefHolder.syncClass.name());
                    db.insert("folder_attributes", null, cv);

                    cv.put("key", "push_class");
                    cv.put("value", prefHolder.pushClass.name());
                    db.insert("folder_attributes", null, cv);
                }
                return null;
            }
        });
    }

    public class LocalFolder extends Folder implements Serializable {
        /**
         *
         */
        private static final long serialVersionUID = -1973296520918624767L;
        private String mName = null;
        private long mFolderId = -1;
        private int mUnreadMessageCount = -1;
        private int mFlaggedMessageCount = -1;
        private int mVisibleLimit = -1;
        private String prefId = null;
        private FolderClass mDisplayClass = FolderClass.NO_CLASS;
        private FolderClass mSyncClass = FolderClass.INHERITED;
        private FolderClass mPushClass = FolderClass.SECOND_CLASS;
        private boolean mInTopGroup = false;
        private String mPushState = null;
        private boolean mIntegrate = false;
        // mLastUid is used during syncs. It holds the highest UID within the local folder so we
        // know whether or not an unread message added to the local folder is actually "new" or not.
        private Integer mLastUid = null;

        public LocalFolder(Cursor cursor) throws MessagingException {
            super(LocalStore.this.mAccount);
            init(cursor);
        }

        public LocalFolder(String name) {
            super(LocalStore.this.mAccount);
            this.mName = name;

            if (LocalStore.this.mAccount.getInboxFolderName().equals(getName())) {

                mSyncClass =  FolderClass.FIRST_CLASS;
                mPushClass =  FolderClass.FIRST_CLASS;
                mInTopGroup = true;
            }


        }

        public LocalFolder(long id) {
            super(LocalStore.this.mAccount);
            this.mFolderId = id;
        }

        public long getId() {
            return mFolderId;
        }

        @Override
        public void open(final OpenMode mode) throws MessagingException {
            if (isOpen()) {
                return;
            }
            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                        Cursor cursor = null;
                        try {
                            String baseQuery = "SELECT " + GET_FOLDER_COLS + " FROM folders " +
                                    "LEFT JOIN folder_attributes ON (folders.id = folder_attributes.folder_id) ";

                            if (mName != null) {
                                cursor = db.rawQuery(baseQuery + "WHERE folders.name = ?", new String[] { mName });
                            } else {
                                cursor = db.rawQuery(baseQuery + "WHERE folders.id = ?", new String[] { Long.toString(mFolderId) });
                            }

                            if (cursor.moveToFirst()) {
                                int folderId = cursor.getInt(0);
                                if (folderId > 0) {
                                    init(cursor);
                                }
                            } else {
                                Log.w(K9.LOG_TAG, "Creating folder " + getName() + " with existing id " + getId());
                                create(FolderType.HOLDS_MESSAGES);
                                open(mode);
                            }
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        private final void init(Cursor cursor) throws MessagingException {
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            //boolean localOnly = (cursor.getInt(2) == 1);
            int unreadCount = cursor.getInt(3);
            int flaggedCount = cursor.getInt(4);
            boolean integrate = (cursor.getInt(5) == 1);
            boolean topGroup = (cursor.getInt(6) == 1);
            String displayClass = cursor.getString(7);
            int visibleLimit = cursor.getInt(8);

            Map<String, String> folderAttributes = new HashMap<String, String>();
            do {
                String key = cursor.getString(9);
                String value = cursor.getString(10);
                folderAttributes.put(key, value);
            } while (cursor.moveToNext() && cursor.getInt(0) == id);

            // TODO: remove the specialization from LocalFolder
            String pushState = folderAttributes.get("push_state");
            String status = folderAttributes.get("status");
            String lastCheckedString = folderAttributes.get("last_updated");
            long lastChecked = (lastCheckedString == null) ? 0 : Long.valueOf(lastCheckedString);
            String lastPushedString = folderAttributes.get("last_pushed");
            long lastPushed = (lastPushedString == null) ? 0 : Long.valueOf(lastPushedString);
            String pushClassString = folderAttributes.get("push_class");
            FolderClass pushClass = (pushClassString == null) ? FolderClass.NO_CLASS : FolderClass.valueOf(pushClassString);
            String syncClassString = folderAttributes.get("poll_class");
            FolderClass syncClass = (syncClassString == null) ? FolderClass.NO_CLASS : FolderClass.valueOf(syncClassString);


            mFolderId = id;
            mName = name;
            mUnreadMessageCount = unreadCount;
            mVisibleLimit = visibleLimit;
            mPushState = pushState;
            mFlaggedMessageCount = flaggedCount;
            super.setStatus(status);
            // Only want to set the local variable stored in the super class.  This class
            // does a DB update on setLastChecked
            super.setLastChecked(lastChecked);
            super.setLastPush(lastPushed);
            mInTopGroup = topGroup;
            mIntegrate = integrate;
            mDisplayClass = (displayClass == null) ? FolderClass.NO_CLASS : FolderClass.valueOf(displayClass);
            mPushClass = pushClass;
            mSyncClass = syncClass;

        }

        @Override
        public boolean isOpen() {
            return (mFolderId != -1 && mName != null);
        }

        @Override
        public OpenMode getMode() {
            return OpenMode.READ_WRITE;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public boolean exists() throws MessagingException {
            return database.execute(false, new DbCallback<Boolean>() {
                @Override
                public Boolean doDbWork(final SQLiteDatabase db) throws WrappedException {
                    Cursor cursor = db.query("folders", new String[] { "id" }, "name = ?",
                            new String[] { getName() }, null, null, null);
                    try {
                        //return cursor.moveToFirst();
                        if (cursor.moveToFirst()) {
                            int folderId = cursor.getInt(0);
                            return (folderId > 0);
                        }
                        return false;
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            });
        }

        @Override
        public boolean create(FolderType type) throws MessagingException {
            return create(type, mAccount.getDisplayCount());
        }

        @Override
        public boolean create(FolderType type, final int visibleLimit) throws MessagingException {
            if (exists()) {
                throw new MessagingException("Folder " + mName + " already exists.");
            }
            List<LocalFolder> foldersToCreate = new ArrayList<LocalFolder>(1);
            foldersToCreate.add(this);
            LocalStore.this.createFolders(foldersToCreate, visibleLimit);

            return true;
        }

        private class PreferencesHolder {
            FolderClass displayClass = mDisplayClass;
            FolderClass syncClass = mSyncClass;
            FolderClass pushClass = mPushClass;
            boolean inTopGroup = mInTopGroup;
            boolean integrate = mIntegrate;
        }

        @Override
        public void close() {
            mFolderId = -1;
        }

        @Override
        public int getMessageCount() throws MessagingException {
            try {
                return database.execute(false, new DbCallback<Integer>() {
                    @Override
                    public Integer doDbWork(final SQLiteDatabase db) throws WrappedException {
                        try {
                            open(OpenMode.READ_WRITE);
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        Cursor cursor = null;
                        try {
                            cursor = db.rawQuery("SELECT COUNT(*) FROM messages WHERE deleted = 0 and folder_id = ?",
                                                 new String[] { Long.toString(mFolderId) });
                            cursor.moveToFirst();
                            return cursor.getInt(0);   //messagecount
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        @Override
        public int getUnreadMessageCount() throws MessagingException {
            open(OpenMode.READ_WRITE);
            return mUnreadMessageCount;
        }

        @Override
        public int getFlaggedMessageCount() throws MessagingException {
            open(OpenMode.READ_WRITE);
            return mFlaggedMessageCount;
        }

        public void setUnreadMessageCount(final int unreadMessageCount) throws MessagingException {
            mUnreadMessageCount = Math.max(0, unreadMessageCount);
            updateFolderColumn("unread_count", mUnreadMessageCount);
        }

        public void setFlaggedMessageCount(final int flaggedMessageCount) throws MessagingException {
            mFlaggedMessageCount = Math.max(0, flaggedMessageCount);
            updateFolderColumn("flagged_count", mFlaggedMessageCount);
        }

        @Override
        public void setLastChecked(final long lastChecked) throws MessagingException {
            try {
                open(OpenMode.READ_WRITE);
                LocalFolder.super.setLastChecked(lastChecked);
            } catch (MessagingException e) {
                throw new WrappedException(e);
            }
            updateFolderAttribute("last_updated", lastChecked);
        }

        @Override
        public void setLastPush(final long lastChecked) throws MessagingException {
            try {
                open(OpenMode.READ_WRITE);
                LocalFolder.super.setLastPush(lastChecked);
            } catch (MessagingException e) {
                throw new WrappedException(e);
            }
            updateFolderAttribute("last_pushed", lastChecked);
        }

        public int getVisibleLimit() throws MessagingException {
            open(OpenMode.READ_WRITE);
            return mVisibleLimit;
        }

        public void purgeToVisibleLimit(MessageRemovalListener listener) throws MessagingException {
            if (mVisibleLimit == 0) {
                return ;
            }
            open(OpenMode.READ_WRITE);
            Message[] messages = getMessages(null, false);
            for (int i = mVisibleLimit; i < messages.length; i++) {
                if (listener != null) {
                    listener.messageRemoved(messages[i]);
                }
                messages[i].destroy();

            }
        }


        public void setVisibleLimit(final int visibleLimit) throws MessagingException {
            mVisibleLimit = visibleLimit;
            updateFolderColumn("visible_limit", mVisibleLimit);
        }

        @Override
        public void setStatus(final String status) throws MessagingException {
            updateFolderAttribute("status", status);
        }
        public void setPushState(final String pushState) throws MessagingException {
            mPushState = pushState;
            updateFolderAttribute("push_state", pushState);
        }

        private void updateFolderColumn(final String column, final Object value) throws MessagingException {
            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                        try {
                            open(OpenMode.READ_WRITE);
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        db.execSQL("UPDATE folders SET " + column + " = ? WHERE id = ?", new Object[] { value, mFolderId });
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        private void updateFolderAttribute(final String key, final Object value) throws MessagingException {
            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                        try {
                            open(OpenMode.READ_WRITE);
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        String folderId = Long.toString(mFolderId);

                        ContentValues cv = new ContentValues();
                        cv.put("key", key);
                        if (value != null) {
                            cv.put("value", value.toString());
                        } else {
                            cv.putNull("value");
                        }
                        int result = db.update("folder_attributes", cv, "folder_id = ? AND key = ?",
                                new String[] {folderId, key});

                        if (result < 1) {
                            cv.put("folder_id", folderId);
                            db.insert("folder_attributes", null, cv);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        public String getPushState() {
            return mPushState;
        }
        @Override
        public FolderClass getDisplayClass() {
            return mDisplayClass;
        }

        @Override
        public FolderClass getSyncClass() {
            if (FolderClass.INHERITED == mSyncClass) {
                return getDisplayClass();
            } else {
                return mSyncClass;
            }
        }

        public FolderClass getRawSyncClass() {
            return mSyncClass;

        }

        @Override
        public FolderClass getPushClass() {
            if (FolderClass.INHERITED == mPushClass) {
                return getSyncClass();
            } else {
                return mPushClass;
            }
        }

        public FolderClass getRawPushClass() {
            return mPushClass;

        }

        public void setDisplayClass(FolderClass displayClass) throws MessagingException {
            mDisplayClass = displayClass;
            updateFolderColumn("display_class", mDisplayClass.name());

        }

        public void setSyncClass(FolderClass syncClass) throws MessagingException {
            mSyncClass = syncClass;
            updateFolderAttribute("poll_class", mSyncClass.name());
        }
        public void setPushClass(FolderClass pushClass) throws MessagingException {
            mPushClass = pushClass;
            updateFolderAttribute("push_class", mPushClass.name());
        }

        public boolean isIntegrate() {
            return mIntegrate;
        }
        public void setIntegrate(boolean integrate) throws MessagingException {
            mIntegrate = integrate;
            updateFolderColumn("integrate", mIntegrate ? 1 : 0);
        }

        private String getPrefId(String name) {
            if (prefId == null) {
                prefId = uUid + "." + name;
            }

            return prefId;
        }

        private String getPrefId() throws MessagingException {
            open(OpenMode.READ_WRITE);
            return getPrefId(mName);

        }

        public void delete() throws MessagingException {
            String id = getPrefId();

            SharedPreferences.Editor editor = LocalStore.this.getPreferences().edit();

            editor.remove(id + ".displayMode");
            editor.remove(id + ".syncMode");
            editor.remove(id + ".pushMode");
            editor.remove(id + ".inTopGroup");
            editor.remove(id + ".integrate");

            editor.commit();
        }

        public void save() throws MessagingException {
            SharedPreferences.Editor editor = LocalStore.this.getPreferences().edit();
            save(editor);
            editor.commit();
        }

        public void save(SharedPreferences.Editor editor) throws MessagingException {
            String id = getPrefId();

            // there can be a lot of folders.  For the defaults, let's not save prefs, saving space, except for INBOX
            if (mDisplayClass == FolderClass.NO_CLASS && !mAccount.getInboxFolderName().equals(getName())) {
                editor.remove(id + ".displayMode");
            } else {
                editor.putString(id + ".displayMode", mDisplayClass.name());
            }

            if (mSyncClass == FolderClass.INHERITED && !mAccount.getInboxFolderName().equals(getName())) {
                editor.remove(id + ".syncMode");
            } else {
                editor.putString(id + ".syncMode", mSyncClass.name());
            }

            if (mPushClass == FolderClass.SECOND_CLASS && !mAccount.getInboxFolderName().equals(getName())) {
                editor.remove(id + ".pushMode");
            } else {
                editor.putString(id + ".pushMode", mPushClass.name());
            }
            editor.putBoolean(id + ".inTopGroup", mInTopGroup);

            editor.putBoolean(id + ".integrate", mIntegrate);

        }

        public void refresh(String name, PreferencesHolder prefHolder) {
            String id = getPrefId(name);

            SharedPreferences preferences = LocalStore.this.getPreferences();

            try {
                prefHolder.displayClass = FolderClass.valueOf(preferences.getString(id + ".displayMode",
                                          prefHolder.displayClass.name()));
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to load displayMode for " + getName(), e);
            }
            if (prefHolder.displayClass == FolderClass.NONE) {
                prefHolder.displayClass = FolderClass.NO_CLASS;
            }

            try {
                prefHolder.syncClass = FolderClass.valueOf(preferences.getString(id  + ".syncMode",
                                       prefHolder.syncClass.name()));
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to load syncMode for " + getName(), e);

            }
            if (prefHolder.syncClass == FolderClass.NONE) {
                prefHolder.syncClass = FolderClass.INHERITED;
            }

            try {
                prefHolder.pushClass = FolderClass.valueOf(preferences.getString(id  + ".pushMode",
                                       prefHolder.pushClass.name()));
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to load pushMode for " + getName(), e);
            }
            if (prefHolder.pushClass == FolderClass.NONE) {
                prefHolder.pushClass = FolderClass.INHERITED;
            }
            prefHolder.inTopGroup = preferences.getBoolean(id + ".inTopGroup", prefHolder.inTopGroup);
            prefHolder.integrate = preferences.getBoolean(id + ".integrate", prefHolder.integrate);

        }

        @Override
        public void fetch(final Message[] messages, final FetchProfile fp, final MessageRetrievalListener listener)
        throws MessagingException {
            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                        try {
                            open(OpenMode.READ_WRITE);
                            if (fp.contains(FetchProfile.Item.BODY)) {
                                for (Message message : messages) {
                                    LocalMessage localMessage = (LocalMessage)message;
                                    loadMessage(db, localMessage);


















                                }
                            }
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }





        private void loadMessage(SQLiteDatabase db, LocalMessage message) throws MessagingException {
            String messageId = Long.toString(message.getId());

            String query =
                "SELECT p.id, p.mime_type, p.parent, p.data, p.header, p.data_type " +
                "FROM messages AS m " +
                "JOIN message_parts AS p ON (m.id = p.message_id) " +
                "WHERE m.id = ? OR m.root = ? " +
                "ORDER BY p.parent, p.seq";

            Cursor cursor = db.rawQuery(query, new String[] {messageId, messageId});
            try {
                Map<Integer, Part> idPartMap = new HashMap<Integer, Part>();
                while (cursor.moveToNext())
                {
                    int partId = cursor.getInt(0);
                    String mimeType = cursor.getString(1);
                    int parentPartId = cursor.getInt(2);
                    String data = cursor.getString(3);
                    String header = cursor.getString(4);
                    int dataType = cursor.getInt(5);

                    Part parent;
                    Part current;
                    if (parentPartId == 0)
                    {
                        parent = message;
                    }
                    else
                    {
                        parent = idPartMap.get(parentPartId);
                    }

                    Body parentBody = parent.getBody();
                    if (parentBody instanceof Multipart)
                    {
                        //current = factory.createBodyPart();
                        current = new MimeBodyPart();
                        ((Multipart) parentBody).addBodyPart((BodyPart) current);
                    }
                    else if (parentBody instanceof Message)
                    {
                        current = (Message) parentBody;
                    }
                    else
                    {
                        current = parent;
                    }

                    if (mimeType.startsWith("multipart/"))
                    {
                        String boundary = MimeMultipart.generateBoundary();
                        String contentType = String.format("%s; boundary=\"%s\"", mimeType, boundary);
                        MimeMultipart multipart = new MimeMultipart(contentType);
                        current.setBody(multipart);
                    }
                    else if (mimeType.equalsIgnoreCase("message/rfc822"))
                    {
                        MimeMessage innerMessage = new MimeMessage();
                        current.setBody(innerMessage);
                    }
                    else
                    {
                        if (dataType == 1 &&  data != null) {
                            //current.setBody(factory.createBody(mimeType, -1,
                            //        new ByteArrayInputStream(data.getBytes())));
                            TextBody body = new TextBody(data);
                            current.setBody(body);
                        } else if (dataType == 2) {
                            //FIXME: This is an ugly hack so text bodies won't be base64-encoded by
                            // LocalAttachmentBody.writeTo(OutputStream).
                            if (mimeType.startsWith("text/")) {
                                String textBody = null;
                                try {
                                    File attachmentDirectory = StorageManager.getInstance(mApplication).getAttachmentDirectory(uUid, database.getStorageProviderId());
                                    String filename = Long.toString(partId);
                                    File file = new File(attachmentDirectory, filename);
                                    FileInputStream in = new FileInputStream(file);
                                    ByteArrayOutputStream out = new ByteArrayOutputStream((int)file.length());
                                    IOUtils.copy(in, out);
                                    textBody = out.toString();
                                } catch (Exception e) {
                                    Log.e(K9.LOG_TAG, "Something went wrong while reading a text body", e);
                                }
                                TextBody body = new TextBody(textBody);
                                current.setBody(body);
                            } else {
                                Uri uri = AttachmentProvider.getAttachmentUri(mAccount, partId);
                                LocalAttachmentBody body = new LocalAttachmentBody(uri, mApplication);
                                current.setBody(body);
                            }
                        }
                    }

                    idPartMap.put(partId, current);

                    parseHeader(current, header);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        @Override
        public Message[] getMessages(int start, int end, Date earliestDate, MessageRetrievalListener listener)
        throws MessagingException {
            open(OpenMode.READ_WRITE);
            throw new MessagingException(
                "LocalStore.getMessages(int, int, MessageRetrievalListener) not yet implemented");
        }

        /**
         * Populate the header fields of the given list of messages by reading
         * the saved header data from the database.
         *
         * @param messages
         *            The messages whose headers should be loaded.
         * @throws UnavailableStorageException
         */
        private void populateHeaders(final List<LocalMessage> messages) throws UnavailableStorageException {
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    Cursor cursor = null;
                    if (messages.size() == 0) {
                        return null;
                    }
                    try {
                        Map<Long, LocalMessage> popMessages = new HashMap<Long, LocalMessage>();
                        List<String> ids = new ArrayList<String>();
                        StringBuffer questions = new StringBuffer();

                        for (int i = 0; i < messages.size(); i++) {
                            if (i != 0) {
                                questions.append(", ");
                            }
                            questions.append("?");
                            LocalMessage message = messages.get(i);
                            Long id = message.getId();
                            ids.add(Long.toString(id));
                            popMessages.put(id, message);

                        }

                        cursor = db.rawQuery(
                                     "SELECT message_id, header FROM message_parts " + "WHERE message_id IN (" + questions + ")",
                                     ids.toArray(EMPTY_STRING_ARRAY));

                        while (cursor.moveToNext()) {
                            Long id = cursor.getLong(0);
                            String header = cursor.getString(1);
                            try {
                                parseHeader(popMessages.get(id), header);
                            } catch (MessagingException e) {
                                Log.e(K9.LOG_TAG, "Exception in parseHeader()", e);
                            }
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    return null;
                }
            });
        }

        @Override
        public Message getMessage(final String uid) throws MessagingException {
            String query = String.format(GET_MESSAGES_BASE_QUERY, "m.uid = ? AND m.folder_id = ?", "");
            Message[] messages = LocalStore.this.getMessages(null, this, query, new String[] {uid, Long.toString(mFolderId)});
            return (messages != null && messages.length > 0) ? messages[0] : null;
        }

        @Override
        public Message[] getMessages(MessageRetrievalListener listener) throws MessagingException {
            return getMessages(listener, true);
        }

        @Override
        public Message[] getMessages(final MessageRetrievalListener listener, final boolean includeDeleted) throws MessagingException {
            open(OpenMode.READ_WRITE);
            String query = String.format(GET_MESSAGES_BASE_QUERY,
                    (includeDeleted ? "" : "m.deleted = 0 AND ") +
                    "m.folder_id = ?", " ORDER BY m.date DESC");
            return LocalStore.this.getMessages(listener, this, query, new String[] {Long.toString(mFolderId)});
        }


        @Override
        public Message[] getMessages(String[] uids, MessageRetrievalListener listener)
        throws MessagingException {
            open(OpenMode.READ_WRITE);
            if (uids == null) {
                return getMessages(listener);
            }
            ArrayList<Message> messages = new ArrayList<Message>();
            for (String uid : uids) {
                Message message = getMessage(uid);
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages.toArray(EMPTY_MESSAGE_ARRAY);
        }

        @Override
        public void copyMessages(Message[] msgs, Folder folder) throws MessagingException {
            if (!(folder instanceof LocalFolder)) {
                throw new MessagingException("copyMessages called with incorrect Folder");
            }
            ((LocalFolder) folder).appendMessages(msgs, true);
        }

        @Override
        public void moveMessages(final Message[] msgs, final Folder destFolder) throws MessagingException {
            if (!(destFolder instanceof LocalFolder)) {
                throw new MessagingException("moveMessages called with non-LocalFolder");
            }

            final LocalFolder lDestFolder = (LocalFolder)destFolder;

            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                        try {
                            lDestFolder.open(OpenMode.READ_WRITE);
                            for (Message message : msgs) {
                                LocalMessage lMessage = (LocalMessage)message;

                                if (!message.isSet(Flag.SEEN)) {
                                    setUnreadMessageCount(getUnreadMessageCount() - 1);
                                    lDestFolder.setUnreadMessageCount(lDestFolder.getUnreadMessageCount() + 1);
                                }

                                if (message.isSet(Flag.FLAGGED)) {
                                    setFlaggedMessageCount(getFlaggedMessageCount() - 1);
                                    lDestFolder.setFlaggedMessageCount(lDestFolder.getFlaggedMessageCount() + 1);
                                }

                                String oldUID = message.getUid();

                                if (K9.DEBUG)
                                    Log.d(K9.LOG_TAG, "Updating folder_id to " + lDestFolder.getId() + " for message with UID "
                                          + message.getUid() + ", id " + lMessage.getId() + " currently in folder " + getName());

                                message.setUid(K9.LOCAL_UID_PREFIX + UUID.randomUUID().toString());

                                ContentValues cv = new ContentValues();
                                cv.put("folder_id", lDestFolder.getId());
                                cv.put("uid", message.getUid());
                                cv.put("local_only", true);
                                db.update("messages", cv, "id = ?", new String[] { Long.toString(lMessage.getId()) });

                                // Open local folder so getId() will return the id of the source folder
                                open(OpenMode.READ_WRITE);

                                // Write placeholder so we know not to display the source message anymore
                                cv.clear();
                                cv.put("folder_id", getId());
                                cv.put("uid", oldUID);
                                cv.put("local_only", false);
                                cv.put("deleted", true);
                                cv.put("seen", true);
                                db.insertOrThrow("messages", null, cv);

                                /*
                                LocalMessage placeHolder = new LocalMessage(oldUID, LocalFolder.this);
                                placeHolder.setFlagInternal(Flag.DELETED, true);
                                placeHolder.setFlagInternal(Flag.SEEN, true);
                                appendMessages(new Message[] { placeHolder });
                                */
                            }
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }

        }

        /**
         * Convenience transaction wrapper for storing a message and set it as fully downloaded. Implemented mainly to speed up DB transaction commit.
         *
         * @param message Message to store. Never <code>null</code>.
         * @param runnable What to do before setting {@link Flag#X_DOWNLOADED_FULL}. Never <code>null</code>.
         * @return The local version of the message. Never <code>null</code>.
         * @throws MessagingException
         */
        public Message storeSmallMessage(final Message message, final Runnable runnable) throws MessagingException {
            return database.execute(true, new DbCallback<Message>() {
                @Override
                public Message doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    try {
                        appendMessages(new Message[] { message });
                        final String uid = message.getUid();
                        final Message result = getMessage(uid);
                        runnable.run();
                        // Set a flag indicating this message has now be fully downloaded
                        result.setFlag(Flag.X_DOWNLOADED_FULL, true);
                        return result;
                    } catch (MessagingException e) {
                        throw new WrappedException(e);
                    }
                }
            });
        }

        /**
         * The method differs slightly from the contract; If an incoming message already has a uid
         * assigned and it matches the uid of an existing message then this message will replace the
         * old message. It is implemented as a delete/insert. This functionality is used in saving
         * of drafts and re-synchronization of updated server messages.
         *
         * NOTE that although this method is located in the LocalStore class, it is not guaranteed
         * that the messages supplied as parameters are actually {@link LocalMessage} instances (in
         * fact, in most cases, they are not). Therefore, if you want to make local changes only to a
         * message, retrieve the appropriate local message instance first (if it already exists).
         */
        @Override
        public void appendMessages(Message[] messages) throws MessagingException {
            appendMessages(messages, false);
        }

        public void destroyMessages(final Message[] messages) throws MessagingException {
            try {
                database.execute(true, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                        for (Message message : messages) {
                            try {
                                message.destroy();
                            } catch (MessagingException e) {
                                throw new WrappedException(e);
                            }
                        }
                        return null;
                    }
                });
            } catch (MessagingException e) {
                throw new WrappedException(e);
            }
        }


        /**
         * The method differs slightly from the contract; If an incoming message already has a uid
         * assigned and it matches the uid of an existing message then this message will replace the
         * old message. It is implemented as a delete/insert. This functionality is used in saving
         * of drafts and re-synchronization of updated server messages.
         *
         * NOTE that although this method is located in the LocalStore class, it is not guaranteed
         * that the messages supplied as parameters are actually {@link LocalMessage} instances (in
         * fact, in most cases, they are not). Therefore, if you want to make local changes only to a
         * message, retrieve the appropriate local message instance first (if it already exists).
         * @param messages
         * @param copy
         */
        private void appendMessages(final Message[] messages, final boolean copy) throws MessagingException {
            open(OpenMode.READ_WRITE);
            try {
                database.execute(true, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                        try {
                            for (Message message : messages) {
                                if (!(message instanceof MimeMessage)) {
                                    throw new Error("LocalStore can only store Messages that extend MimeMessage");
                                }

                                String uid = message.getUid();
                                if (uid == null || copy) {
                                    uid = K9.LOCAL_UID_PREFIX + UUID.randomUUID().toString();
                                    if (!copy) {
                                        message.setUid(uid);
                                    }
                                } else {
                                    LocalMessage oldMessage = (LocalMessage) getMessage(uid);
                                    if (oldMessage != null && !oldMessage.isSet(Flag.SEEN)) {
                                        setUnreadMessageCount(getUnreadMessageCount() - 1);
                                    }
                                    if (oldMessage != null && oldMessage.isSet(Flag.FLAGGED)) {
                                        setFlaggedMessageCount(getFlaggedMessageCount() - 1);
                                    }
                                    /*
                                     * The message may already exist in this Folder, so delete it first.
                                     */
                                    if (oldMessage != null) {
                                        oldMessage.destroy();
                                    }
                                }

                                Map<Part, Long> partIdMapping = new HashMap<Part, Long>();
                                long messageId = saveMessage(db, uid, message, partIdMapping);

                                populateMessageCache(db, uid, messageId, partIdMapping, message, copy);

                            }
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        //TODO: support message/rfc822 (sub) messages
        private long saveMessage(SQLiteDatabase db, String uid, Message message,
                Map<Part, Long> partIdMapping) throws MessagingException {

            Map<Long, int[]> messageOrder = new HashMap<Long, int[]>();

            Stack<PartDescription> stack = new Stack<PartDescription>();
            stack.push(new PartDescription(message, 0, 0, 0));

            long rootMessageId = 0;

            while (!stack.isEmpty()) {
                PartDescription p = stack.pop();
                Part part = p.part;
                long messageId = p.messageId;
                long parentPartId = p.parentId;
                int order = p.order;

                if (part instanceof Message) {
                    // Create entry in 'messages' table
                    int msgOrder;
                    if (rootMessageId == 0) {
                        msgOrder = 1;
                    } else {
                        int[] currentOrder = messageOrder.get(messageId);
                        msgOrder = currentOrder[0];
                        currentOrder[0]++;
                    }

                    Message currentMessage = (Message) part;

                    ContentValues msg = new ContentValues();
                    msg.put("folder_id", mFolderId);
                    msg.put("uid", uid);
                    msg.put("local_only", uid.startsWith(K9.LOCAL_UID_PREFIX));
                    msg.put("deleted", currentMessage.isSet(Flag.DELETED));
                    if (currentMessage.getSentDate() != null) {
                        msg.put("date", currentMessage.getSentDate().getTime());
                    }
                    if (currentMessage.getInternalDate() != null) {
                        msg.put("internal_date", currentMessage.getInternalDate().getTime());
                    }
                    if (rootMessageId != 0) {
                        msg.put("root", rootMessageId);
                    }
                    if (messageId != 0) {
                        msg.put("parent", messageId);
                    }
                    msg.put("seq", msgOrder);

                    if (rootMessageId == 0) {
                        msg.put("seen", currentMessage.isSet(Flag.SEEN));
                        msg.put("answered", currentMessage.isSet(Flag.ANSWERED));
                        msg.put("flagged", currentMessage.isSet(Flag.FLAGGED));
                        msg.put("destroyed", currentMessage.isSet(Flag.X_DESTROYED));
                        msg.put("downloaded_full", currentMessage.isSet(Flag.X_DOWNLOADED_FULL));
                        msg.put("downloaded_partial", currentMessage.isSet(Flag.X_DOWNLOADED_PARTIAL));
                        msg.put("got_all_headers", currentMessage.isSet(Flag.X_GOT_ALL_HEADERS));
                        msg.put("remote_copy_started", currentMessage.isSet(Flag.X_REMOTE_COPY_STARTED));
                        msg.put("send_failed", currentMessage.isSet(Flag.X_SEND_FAILED));
                        msg.put("send_in_progress", currentMessage.isSet(Flag.X_SEND_IN_PROGRESS));
                    }

                    messageId = db.insertOrThrow("messages", null, msg);

                    if (rootMessageId == 0) {
                        rootMessageId = messageId;
                    }
                    messageOrder.put(messageId, new int[] {1});

                    // Create entries in 'addresses' table
                    ContentValues cv = new ContentValues();
                    cv.put("message_id", messageId);
                    cv.put("type", "FROM");
                    for (Address addr : currentMessage.getFrom()) {
                        cv.put("name", addr.getPersonal());
                        cv.put("email", addr.getAddress());
                        db.insertOrThrow("addresses", null, cv);
                    }
                    messageOrder.put(messageId, new int[] {1});

                    cv.put("type", "TO");
                    for (Address addr : currentMessage.getRecipients(RecipientType.TO)) {
                        cv.put("name", addr.getPersonal());
                        cv.put("email", addr.getAddress());
                        db.insertOrThrow("addresses", null, cv);
                    }

                    cv.put("type", "CC");
                    for (Address addr : currentMessage.getRecipients(RecipientType.CC)) {
                        cv.put("name", addr.getPersonal());
                        cv.put("email", addr.getAddress());
                        db.insertOrThrow("addresses", null, cv);
                    }

                    cv.put("type", "BCC");
                    for (Address addr : currentMessage.getRecipients(RecipientType.BCC)) {
                        cv.put("name", addr.getPersonal());
                        cv.put("email", addr.getAddress());
                        db.insertOrThrow("addresses", null, cv);
                    }
                }

                // Create entry in 'message_parts' table
                ContentValues cv = new ContentValues();
                cv.put("message_id", messageId);

                // Field: header
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try {
                    part.getHeader().writeTo(buf);
                } catch (IOException e2) {
                    // TODO Auto-generated catch block
                    e2.printStackTrace();
                }
                //cv.put("header", buf.toByteArray());
                cv.put("header", buf.toString());

                // Field: mime_type
                String[] headers = part.getHeader("Content-Type");
                String contentType = (headers != null && headers.length >= 1) ? headers[0] : "";
                String mimeType = null;
                try {
                    ContentTypeField ct = (ContentTypeField) DefaultFieldParser.parse("Content-Type: " + contentType);
                    //TODO: use ContentTypeField.getMimeType(ContentTypeField,ContentTypeField)
                    mimeType = ct.getMimeType();
                } catch (Exception e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                cv.put("mime_type", mimeType);

                Body body = part.getBody();
                boolean isMultipart = (body instanceof Multipart);

                if (parentPartId != 0) {
                    cv.putNull("parent");
                }

                cv.put("seq", order);

                if (isMultipart) {
                    Multipart multipart = (Multipart) body;
                    cv.put("data_type", 0); //empty
                    cv.put("preamble", multipart.getPreamble());
                    cv.put("epilogue", multipart.getEpilogue());
                    cv.put("complete", true);
                } else {
                    cv.put("data_type", 2); //external file
                    cv.put("complete", false);
                }

                long partId = db.insertOrThrow("message_parts", null, cv);
                partIdMapping.put(part, partId);

                if (isMultipart) {
                    Multipart multipart = (Multipart) body;
                    int children = multipart.getCount();

                    List<Part> parts = new ArrayList<Part>();
                    for (int i = 0; i < children; i++) {
                        parts.add(multipart.getBodyPart(i));
                    }

                    Collections.reverse(parts);

                    int seq = 0;
                    for (Part child : parts) {
                        stack.push(new PartDescription(child, messageId, partId, seq++));
                    }
                } else if (body != null) {
                    // save message part as file if body is available
                    writeMessagePartFile(db, body, partId);
                }

                String storeData = Utility.combine(part.getHeader(
                        MimeHeader.HEADER_ANDROID_ATTACHMENT_STORE_DATA), ',');

                if (storeData != null && storeData.length() > 0) {
                    cv.clear();
                    cv.put("message_part_id", partId);
                    cv.put("key", "imap_bodystructure_id");
                    cv.put("value", storeData);
                    db.insertOrThrow("message_part_attributes", null, cv);
                }
            }

            return rootMessageId;
        }

        private long writeMessagePartFile(SQLiteDatabase db, Body body, long partId) {
            long size = -1;
            try {
                ContentValues cv = new ContentValues();

                File attachmentDirectory = StorageManager.getInstance(mApplication).getAttachmentDirectory(uUid, database.getStorageProviderId());

                InputStream in = body.getInputStream();
                String filename = Long.toString(partId);
                File file = new File(attachmentDirectory, filename);
                FileOutputStream out = new FileOutputStream(file);
                size = IOUtils.copy(in, out);
                in.close();
                out.close();

                if (size == 0) {
                    file.delete();
                } else {
                    cv.put("data", filename);
                }

                cv.put("size", size);
                cv.put("complete", true);
                db.update("message_parts", cv, "id = ?", new String[] {Long.toString(partId)});
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Ooops", e);
            }

            return size;
        }

        private void populateMessageCache(SQLiteDatabase db, String uid, long messageId,
                Map<Part, Long> partIdMapping, Message message, boolean copy)
                throws MessagingException {

            //message_cache
            ArrayList<Part> viewables = new ArrayList<Part>();
            ArrayList<Part> attachments = new ArrayList<Part>();
            MimeUtility.collectParts(message, viewables, attachments);

            long textPartId = 0;
            long htmlPartId = 0;

            StringBuffer sbHtml = new StringBuffer();
            StringBuffer sbText = new StringBuffer();
            for (Part viewable : viewables) {
                try {
                    String text = MimeUtility.getTextFromPart(viewable);

                    /*
                     * Small hack to make sure the string "null" doesn't end up
                     * in one of the StringBuffers.
                     */
                    if (text == null) {
                        text = "";
                    }

                    /*
                     * Anything with MIME type text/html will be stored as such. Anything
                     * else will be stored as text/plain.
                     */
                    if (viewable.getMimeType().equalsIgnoreCase("text/html")) {
                        sbHtml.append(text);
                        if (htmlPartId == 0) {
                            htmlPartId = partIdMapping.get(viewable);
                        }
                    } else {
                        sbText.append(text);
                        if (textPartId == 0) {
                            textPartId = partIdMapping.get(viewable);
                        }
                    }
                } catch (Exception e) {
                    throw new MessagingException("Unable to get text for message part", e);
                }
            }

            String text = sbText.toString();
            String html = markupContent(text, sbHtml.toString());
            String preview = calculateContentPreview(text);
            // If we couldn't generate a reasonable preview from the text part, try doing it with the HTML part.
            if (preview == null || preview.length() == 0) {
                preview = calculateContentPreview(HtmlConverter.htmlToText(html));
            }

            try {
                ContentValues cv = new ContentValues();
                cv.put("message_id", messageId);
                cv.put("subject", message.getSubject());
                cv.put("preview", preview.length() > 0 ? preview : null);
                db.insertOrThrow("message_cache", null, cv);
            } catch (Exception e) {
                throw new MessagingException("Error appending message", e);
            }

            int seq = 1;

            if (textPartId != 0) {
                ContentValues cv = new ContentValues();
                cv.put("message_part_id", textPartId);
                cv.put("type", 1);  //display "text"
                cv.put("size", text.length());
                //cv.put("data_type", x);
                cv.put("data", text);
                cv.put("seq", seq++);
                db.insertOrThrow("message_part_cache", null, cv);
            }

            if (html != null && html.length() > 0) {
                ContentValues cv = new ContentValues();
                cv.put("message_part_id", (htmlPartId != 0) ? htmlPartId : textPartId);
                cv.put("type", 2);  //display "html"
                cv.put("size", html.length());
                //cv.put("data_type", x);
                cv.put("data", html);
                cv.put("seq", seq++);
                db.insertOrThrow("message_part_cache", null, cv);
            }

            for (Part attachment : attachments) {
                long messagePartId = partIdMapping.get(attachment);

                String contentType = MimeUtility.unfoldAndDecode(attachment.getContentType());
                String contentDisposition = MimeUtility.unfoldAndDecode(attachment.getDisposition());

                String name = MimeUtility.getHeaderParameter(contentType, "name");
                if (name == null) {
                    name = MimeUtility.getHeaderParameter(contentDisposition, "filename");
                }

                // TODO: get size from message_parts table
                long size = -1;
                String disposition = attachment.getDisposition();
                if (disposition != null) {
                    String s = MimeUtility.getHeaderParameter(disposition, "size");
                    if (s != null) {
                        try {
                            size = Long.parseLong(s);
                        } catch (NumberFormatException e) { /* ignore */ }
                    }
                }
                if (size == -1) {
                    size = 0;
                }

                ContentValues cv = new ContentValues();
                cv.put("message_part_id", messagePartId);
                cv.put("type", 3);  //attachment
                cv.put("name", name);
                cv.put("size", size);
                //cv.put("data_type", x);
                if (attachment.getBody() != null) {
                    cv.put("data", "content://com.fsck.k9.MessagePartProvider/<account_uuid>/<message_part_id>/DECODED/");    //FIXME
                }
                cv.put("seq", seq++);
                db.insertOrThrow("message_part_cache", null, cv);
            }
        }

        /**
         * Update the given message in the LocalStore without first deleting the existing
         * message (contrast with appendMessages). This method is used to store changes
         * to the given message while updating attachments and not removing existing
         * attachment data.
         * TODO In the future this method should be combined with appendMessages since the Message
         * contains enough data to decide what to do.
         * @param message
         * @throws MessagingException
         */
        public void updateMessage(final LocalMessage message, final Part part) throws MessagingException {
            open(OpenMode.READ_WRITE);
            try {
                database.execute(false, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                        try {
                            LocalAttachmentBodyPart attachment = (LocalAttachmentBodyPart) part;

                            if (part.getBody() == null) {
                                // This shouldn't happen. Right?
                                Log.w(K9.LOG_TAG, "Strange... Why?");
                                return null;
                            }

                            long messagePartId = attachment.getAttachmentId();
                            String messagePartIdStr = Long.toString(messagePartId);
                            String[] idArg = new String[] { messagePartIdStr };

                            Cursor cursor = db.query("message_parts", new String[] {"id"},
                                    "id = ?", idArg, null, null, null);

                            try {
                                if (!cursor.moveToFirst()) {
                                    throw new MessagingException("Can't find attachment that needs updating");
                                }
                            } finally {
                                if (cursor != null) {
                                    cursor.close();
                                }
                            }

                            long size = writeMessagePartFile(db, part.getBody(), messagePartId);

                            if (size >= 0) {
                                ContentValues cv = new ContentValues();
                                cv.put("size", size);
                                db.update("message_part_cache", cv, "message_part_id = ?", new String[] {Long.toString(messagePartId)});
                            }
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        /**
         * Changes the stored uid of the given message (using it's internal id as a key) to
         * the uid in the message.
         * @param message
         * @throws com.fsck.k9.mail.MessagingException
         */
        public void changeUid(final LocalMessage message) throws MessagingException {
            open(OpenMode.READ_WRITE);
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    String uid = message.getUid();

                    final ContentValues cv = new ContentValues();
                    cv.put("uid", uid);
                    cv.put("local_only", uid.startsWith(K9.LOCAL_UID_PREFIX));

                    db.update("messages", cv, "id = ?",
                            new String[] { Long.toString(message.getId()) });

                    return null;
                }
            });
        }

        @Override
        public void setFlags(Message[] messages, Flag[] flags, boolean value)
        throws MessagingException {
            open(OpenMode.READ_WRITE);
            for (Message message : messages) {
                message.setFlags(flags, value);
            }
        }

        @Override
        public void setFlags(Flag[] flags, boolean value)
        throws MessagingException {
            open(OpenMode.READ_WRITE);
            for (Message message : getMessages(null)) {
                message.setFlags(flags, value);
            }
        }

        @Override
        public String getUidFromMessageId(Message message) throws MessagingException {
            throw new MessagingException("Cannot call getUidFromMessageId on LocalFolder");
        }

        private void clearMessagesWhere(final String whereClause, final String[] params)  throws MessagingException {
            open(OpenMode.READ_ONLY);

            List<Long> messageIds = database.execute(false, new DbCallback<List<Long>>() {
                @Override
                public List<Long> doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    List<Long> ids = new ArrayList<Long>();
                    Cursor cursor = db.query("messages", new String[] { "id" }, whereClause, params, null, null, null);
                    try {
                        while (cursor.moveToNext()) {
                            ids.add(cursor.getLong(0));
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    return ids;
                }
            });

            for (long messageId : messageIds) {
                deleteAttachments(messageId);
            }

            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    db.delete("messages", whereClause, params);
                    return null;
                }
            });
            resetUnreadAndFlaggedCounts();
        }

        public void clearMessagesOlderThan(long cutoff) throws MessagingException {
            final String where = "folder_id = ? AND date < ?";
            final String[] params = new String[] {
                Long.toString(mFolderId), Long.toString(cutoff)
            };

            clearMessagesWhere(where, params);
        }



        public void clearAllMessages() throws MessagingException {
            final String where = "folder_id = ?";
            final String[] params = new String[] {
                Long.toString(mFolderId)
            };

            clearMessagesWhere(where, params);

            setPushState(null);
            setLastPush(0);
            setLastChecked(0);
            setVisibleLimit(mAccount.getDisplayCount());
        }

        private void resetUnreadAndFlaggedCounts() {
            try {
                int newUnread = 0;
                int newFlagged = 0;
                Message[] messages = getMessages(null);
                for (Message message : messages) {
                    if (!message.isSet(Flag.SEEN)) {
                        newUnread++;
                    }
                    if (message.isSet(Flag.FLAGGED)) {
                        newFlagged++;
                    }
                }
                setUnreadMessageCount(newUnread);
                setFlaggedMessageCount(newFlagged);
            } catch (Exception e) {
                Log.e(K9.LOG_TAG, "Unable to fetch all messages from LocalStore", e);
            }
        }


        @Override
        public void delete(final boolean recurse) throws MessagingException {
            clearAllMessages();
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    db.delete("folders", "id = ?", new String[] { Long.toString(mFolderId) } );
                    return null;
                }
            });
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof LocalFolder) {
                return ((LocalFolder)o).mName.equals(mName);
            }
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return mName.hashCode();
        }

        @Override
        public Flag[] getPermanentFlags() {
            return PERMANENT_FLAGS;
        }


        private void deleteAttachments(final long messageId) throws MessagingException {
            open(OpenMode.READ_WRITE);
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                    Cursor attachmentsCursor = null;
                    try {
                        attachmentsCursor = db.query("message_parts",
                                new String[] { "data" },
                                "message_id = ?",
                                new String[] { Long.toString(messageId) },
                                null, null, null);

                        final File attachmentDirectory = StorageManager.getInstance(mApplication)
                                                         .getAttachmentDirectory(uUid, database.getStorageProviderId());
                        while (attachmentsCursor.moveToNext()) {
                            long attachmentId = attachmentsCursor.getLong(0);
                            try {
                                File file = new File(attachmentDirectory, Long.toString(attachmentId));
                                if (file.exists()) {
                                    file.delete();
                                }
                            } catch (Exception e) {

                            }
                        }
                    } finally {
                        if (attachmentsCursor != null) {
                            attachmentsCursor.close();
                        }
                    }
                    return null;
                }
            });
        }

        /*
         * calculateContentPreview
         * Takes a plain text message body as a string.
         * Returns a message summary as a string suitable for showing in a message list
         *
         * A message summary should be about the first 160 characters
         * of unique text written by the message sender
         * Quoted text, "On $date" and so on will be stripped out.
         * All newlines and whitespace will be compressed.
         *
         */
        public String calculateContentPreview(String text) {
            if (text == null) {
                return null;
            }

            // Only look at the first 8k of a message when calculating
            // the preview.  This should avoid unnecessary
            // memory usage on large messages
            if (text.length() > 8192) {
                text = text.substring(0, 8192);
            }

            // try to remove lines of dashes in the preview
            text = text.replaceAll("(?m)^----.*?$", "");
            // remove quoted text from the preview
            text = text.replaceAll("(?m)^[#>].*$", "");
            // Remove a common quote header from the preview
            text = text.replaceAll("(?m)^On .*wrote.?$", "");
            // Remove a more generic quote header from the preview
            text = text.replaceAll("(?m)^.*\\w+:$", "");

            // URLs in the preview should just be shown as "..." - They're not
            // clickable and they usually overwhelm the preview
            text = text.replaceAll("https?://\\S+", "...");
            // Don't show newlines in the preview
            text = text.replaceAll("(\\r|\\n)+", " ");
            // Collapse whitespace in the preview
            text = text.replaceAll("\\s+", " ");
            if (text.length() <= 512) {
                return text;
            } else {
                return text.substring(0, 512);
            }

        }

        public String markupContent(String text, String html) {
            if (text.length() > 0 && html.length() == 0) {
                html = HtmlConverter.textToHtml(text);
            }

            html = HtmlConverter.convertEmoji2Img(html);

            return html;
        }


        @Override
        public boolean isInTopGroup() {
            return mInTopGroup;
        }

        public void setInTopGroup(boolean inTopGroup) throws MessagingException {
            mInTopGroup = inTopGroup;
            updateFolderColumn("top_group", mInTopGroup ? 1 : 0);
        }

        public Integer getLastUid() {
            return mLastUid;
        }

        /**
         * <p>Fetches the most recent <b>numeric</b> UID value in this folder.  This is used by
         * {@link com.fsck.k9.controller.MessagingController#shouldNotifyForMessage} to see if messages being
         * fetched are new and unread.  Messages are "new" if they have a UID higher than the most recent UID prior
         * to synchronization.</p>
         *
         * <p>This only works for protocols with numeric UIDs (like IMAP). For protocols with
         * alphanumeric UIDs (like POP), this method quietly fails and shouldNotifyForMessage() will
         * always notify for unread messages.</p>
         *
         * <p>Once Issue 1072 has been fixed, this method and shouldNotifyForMessage() should be
         * updated to use internal dates rather than UIDs to determine new-ness. While this doesn't
         * solve things for POP (which doesn't have internal dates), we can likely use this as a
         * framework to examine send date in lieu of internal date.</p>
         * @throws MessagingException
         */
        public void updateLastUid() throws MessagingException {
            Integer lastUid = database.execute(false, new DbCallback<Integer>() {
                @Override
                public Integer doDbWork(final SQLiteDatabase db) {
                    Cursor cursor = null;
                    try {
                        open(OpenMode.READ_ONLY);
                        cursor = db.rawQuery("SELECT MAX(uid) FROM messages WHERE folder_id = ?",
                                new String[] { Long.toString(mFolderId) });
                        if (cursor.getCount() > 0) {
                            cursor.moveToFirst();
                            return cursor.getInt(0);
                        }
                    } catch (Exception e) {
                        Log.e(K9.LOG_TAG, "Unable to updateLastUid: ", e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    return null;
                }
            });
            if (K9.DEBUG)
                Log.d(K9.LOG_TAG, "Updated last UID for folder " + mName + " to " + lastUid);
            mLastUid = lastUid;
        }

        public long getOldestMessageDate() throws MessagingException {
            return database.execute(false, new DbCallback<Long>() {
                @Override
                public Long doDbWork(final SQLiteDatabase db) {
                    Cursor cursor = null;
                    try {
                        open(OpenMode.READ_ONLY);
                        cursor = db.rawQuery("SELECT MIN(date) FROM messages WHERE folder_id = ?",
                                new String[] { Long.toString(mFolderId) });
                        if (cursor.getCount() > 0) {
                            cursor.moveToFirst();
                            return cursor.getLong(0);
                        }
                    } catch (Exception e) {
                        Log.e(K9.LOG_TAG, "Unable to fetch oldest message date: ", e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    return null;
                }
            });
        }

        /**
         * Loads the cached displayable parts of a message along with information about the
         * available attachments from the database and stores them in the given
         * {@link LocalMessage} object.
         *
         * <p>
         * Note: This won't restore the original MIME message. It will be a simple one part message
         * if only a text part is available. Otherwise it will be a multipart message with the
         * first part being the text and the rest being attachments.
         * </p>
         *
         * @param message
         * @throws UnavailableStorageException
         */
        public void loadMessageForView(final LocalMessage message) throws UnavailableStorageException {
            database.execute(false, new DbCallback<Void>() {
                @Override
                public Void doDbWork(final SQLiteDatabase db) throws WrappedException {
                    String[] args = new String[] { Long.toString(message.getId()) };

                    Cursor cursor = null;
                    try {
                        //TODO: sort
                        String query =
                            "SELECT c.message_part_id, c.type, c.name, c.data, p.mime_type, a.key, a.value, c.size, c.id " +
                            "FROM message_part_cache AS c " +
                            "JOIN message_parts AS p ON (p.id = c.message_part_id) " +
                            "LEFT JOIN message_part_attributes AS a ON (p.id = a.message_part_id) " +
                            "WHERE p.message_id = ? " +
                            "ORDER BY c.seq";

                        cursor = db.rawQuery(query, args);

                        List<BodyPart> attachments = new ArrayList<BodyPart>();

                        String textContent = null;
                        String htmlContent = null;

                        Map<String, String> messagePartAttributes = new HashMap<String, String>();

                        if (cursor.moveToNext()) {
                            do {
                                long partId = cursor.getLong(0);
                                int type = cursor.getInt(1);
                                String name = cursor.getString(2);
                                String data = cursor.getString(3);
                                String mimeType = cursor.getString(4);
                                long size = cursor.getLong(7);
                                long cachePartId = cursor.getLong(8);

                                do {
                                    String key = cursor.getString(5);
                                    if (key != null) {
                                        String value = cursor.getString(6);
                                        messagePartAttributes.put(key, value);
                                    }
                                } while (cursor.moveToNext() &&
                                        cursor.getLong(0) == partId &&
                                        cursor.getLong(8) == cachePartId);

                                if (type == 1) {
                                    textContent = data;
                                } else if (type == 2) {
                                    htmlContent = data;
                                } else if (type == 3) {
                                    String contentDisposition = "attachment";

                                    Body body = null;
                                    if (data != null) {
                                        body = new LocalAttachmentBody(Uri.parse(data), mApplication);
                                    }

                                    MimeBodyPart bp = new LocalAttachmentBodyPart(body, partId);
                                    bp.setHeader(MimeHeader.HEADER_CONTENT_TRANSFER_ENCODING, "base64");
                                    if (name != null) {
                                        String encoded_name = EncoderUtil.encodeIfNecessary(name,
                                                                                       EncoderUtil.Usage.WORD_ENTITY, 7);

                                        bp.setHeader(MimeHeader.HEADER_CONTENT_TYPE,
                                                String.format("%s;\n name=\"%s\"",
                                                              mimeType,
                                                              encoded_name));
                                        bp.setHeader(MimeHeader.HEADER_CONTENT_DISPOSITION,
                                                String.format("%s;\n filename=\"%s\";\n size=%d",
                                                              contentDisposition,
                                                              encoded_name, // TODO: Should use encoded word defined in RFC 2231.
                                                              size));
                                    }

                                    String storeData = messagePartAttributes.get("imap_bodystructure_id");
                                    if (storeData != null) {
                                        /*
                                         * HEADER_ANDROID_ATTACHMENT_STORE_DATA is a custom header we add to that
                                         * we can later pull the attachment from the remote store if necessary.
                                         */
                                        bp.setHeader(MimeHeader.HEADER_ANDROID_ATTACHMENT_STORE_DATA, storeData);
                                    }

                                    attachments.add(bp);
                                }
                            } while (!cursor.isAfterLast());
                        }

                        String mimeType = message.getMimeType();

                        MimeMultipart mp = new MimeMultipart();
                        mp.setSubType("mixed");

                        if (mimeType != null && mimeType.toLowerCase().startsWith("multipart/")) {
                            // If this is a multipart message, preserve both text
                            // and html parts, as well as the subtype.
                            mp.setSubType(mimeType.toLowerCase().replaceFirst("^multipart/", ""));
                            if (textContent != null) {
                                LocalTextBody body = new LocalTextBody(textContent, htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/plain");
                                mp.addBodyPart(bp);
                            }
                            if (htmlContent != null) {
                                TextBody body = new TextBody(htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/html");
                                mp.addBodyPart(bp);
                            }

                            // If we have both text and html content and our MIME type
                            // isn't multipart/alternative, then corral them into a new
                            // multipart/alternative part and put that into the parent.
                            // If it turns out that this is the only part in the parent
                            // MimeMultipart, it'll get fixed below before we attach to
                            // the message.
                            if (textContent != null && htmlContent != null && !mimeType.equalsIgnoreCase("multipart/alternative")) {
                                MimeMultipart alternativeParts = mp;
                                alternativeParts.setSubType("alternative");
                                mp = new MimeMultipart();
                                mp.addBodyPart(new MimeBodyPart(alternativeParts));
                            }
                        } else if (mimeType != null && mimeType.equalsIgnoreCase("text/plain")) {
                            // If it's text, add only the plain part. The MIME
                            // container will drop away below.
                            if (textContent != null) {
                                LocalTextBody body = new LocalTextBody(textContent, htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/plain");
                                mp.addBodyPart(bp);
                            }
                        } else if (mimeType != null && mimeType.equalsIgnoreCase("text/html")) {
                            // If it's html, add only the html part. The MIME
                            // container will drop away below.
                            if (htmlContent != null) {
                                TextBody body = new TextBody(htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/html");
                                mp.addBodyPart(bp);
                            }
                        } else {
                            // MIME type not set. Grab whatever part we can get,
                            // with Text taking precedence. This preserves pre-HTML
                            // composition behaviour.
                            if (textContent != null) {
                                LocalTextBody body = new LocalTextBody(textContent, htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/plain");
                                mp.addBodyPart(bp);
                            } else if (htmlContent != null) {
                                TextBody body = new TextBody(htmlContent);
                                MimeBodyPart bp = new MimeBodyPart(body, "text/html");
                                mp.addBodyPart(bp);
                            }
                        }

                        for (BodyPart attachment : attachments) {
                            mp.addBodyPart(attachment);
                        }

                        if (mp.getCount() == 0) {
                            // If we have no body, remove the container and create a
                            // dummy plain text body. This check helps prevents us from
                            // triggering T_MIME_NO_TEXT and T_TVD_MIME_NO_HEADERS
                            // SpamAssassin rules.
                            message.setHeader(MimeHeader.HEADER_CONTENT_TYPE, "text/plain");
                            message.setBody(new TextBody(""));
                        } else if (mp.getCount() == 1 && (mp.getBodyPart(0) instanceof LocalAttachmentBodyPart) == false)

                        {
                            // If we have only one part, drop the MimeMultipart container.
                            BodyPart part = mp.getBodyPart(0);
                            message.setHeader(MimeHeader.HEADER_CONTENT_TYPE, part.getContentType());
                            message.setBody(part.getBody());
                        } else {
                            // Otherwise, attach the MimeMultipart to the message.
                            message.setBody(mp);
                        }
                    } catch (Exception e) {
                        Log.e(K9.LOG_TAG, "Got an exception ", e);
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                    return null;
                }
            });
        }


        private void parseHeader(final Part part, String header) throws MessagingException {

            MimeEntityConfig parserConfig = new MimeEntityConfig();
            parserConfig.setMaxHeaderLen(-1);
            parserConfig.setMaxLineLen(-1);
            parserConfig.setStrictParsing(false);
            MimeStreamParser parser = new MimeStreamParser(parserConfig);
            parser.setContentHandler(new ContentHandler() {

                @Override
                public void startMultipart(BodyDescriptor arg0) throws MimeException
                {
                    // unused
                }

                @Override
                public void startMessage() throws MimeException
                {
                    // unused
                }

                @Override
                public void startHeader() throws MimeException
                {
                    // unused
                }

                @Override
                public void startBodyPart() throws MimeException
                {
                    // unused
                }

                @Override
                public void raw(InputStream arg0) throws MimeException, IOException
                {
                    // unused
                }

                @Override
                public void preamble(InputStream arg0) throws MimeException, IOException
                {
                    // unused
                }

                @Override
                public void field(RawField field) throws MimeException
                {
                    try
                    {
                        part.addHeader(field.getName(), field.getBody());
                    }
                    catch (MessagingException e)
                    {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                @Override
                public void epilogue(InputStream arg0) throws MimeException, IOException
                {
                    // unused
                }

                @Override
                public void endMultipart() throws MimeException
                {
                    // unused
                }

                @Override
                public void endMessage() throws MimeException
                {
                    // unused
                }

                @Override
                public void endHeader() throws MimeException
                {
                    // unused
                }

                @Override
                public void endBodyPart() throws MimeException
                {
                    // unused
                }

                @Override
                public void body(BodyDescriptor arg0, InputStream arg1)
                        throws MimeException, IOException
                {
                    // unused
                }
            });

            try
            {
                parser.parse(new ByteArrayInputStream(header.getBytes()));
            }
            catch (Exception me)
            {
                throw new MessagingException("oops", me);
            }
        }
    }

    public static class LocalTextBody extends TextBody {
        /**
         * This is an HTML-ified version of the message for display purposes.
         */
        private String mBodyForDisplay;

        public LocalTextBody(String body) {
            super(body);
        }

        public LocalTextBody(String body, String bodyForDisplay) {
            super(body);
            this.mBodyForDisplay = bodyForDisplay;
        }

        public String getBodyForDisplay() {
            return mBodyForDisplay;
        }

        public void setBodyForDisplay(String mBodyForDisplay) {
            this.mBodyForDisplay = mBodyForDisplay;
        }

    }//LocalTextBody

    public class LocalMessage extends MimeMessage {
        private long mId;
        private int mAttachmentCount;
        private String mSubject;

        private String mPreview = "";

        private boolean mToMeCalculated = false;
        private boolean mCcMeCalculated = false;
        private boolean mToMe = false;
        private boolean mCcMe = false;

        private boolean mHeadersLoaded = false;
        private boolean mMessageDirty = false;

        public LocalMessage() {
        }

        LocalMessage(String uid, Folder folder) {
            this.mUid = uid;
            this.mFolder = folder;
        }

        private void populateFromGetMessageCursor(SQLiteDatabase db, Cursor cursor)
        throws MessagingException {

            if (mFolder == null) {
                LocalFolder f = new LocalFolder(cursor.getLong(19));
                f.open(LocalFolder.OpenMode.READ_WRITE);
                mFolder = f;
            }

            final String subject = cursor.getString(15);
            this.setSubject(subject == null ? "" : subject);
            this.setInternalSentDate(new Date(cursor.getLong(3)));
            this.setSentDate(new Date(cursor.getLong(3)));
            this.setUid(cursor.getString(1));

            this.setHeader(MimeHeader.HEADER_CONTENT_TYPE, cursor.getString(18));

            this.setFlagInternal(Flag.DELETED, cursor.getInt(2) == 1);
            this.setFlagInternal(Flag.SEEN, cursor.getInt(4) == 1);
            this.setFlagInternal(Flag.FLAGGED, cursor.getInt(5) == 1);
            this.setFlagInternal(Flag.ANSWERED, cursor.getInt(6) == 1);
            this.setFlagInternal(Flag.X_DESTROYED, cursor.getInt(8) == 1);
            this.setFlagInternal(Flag.X_SEND_FAILED, cursor.getInt(9) == 1);
            this.setFlagInternal(Flag.X_SEND_IN_PROGRESS, cursor.getInt(10) == 1);
            this.setFlagInternal(Flag.X_DOWNLOADED_FULL, cursor.getInt(11) == 1);
            this.setFlagInternal(Flag.X_DOWNLOADED_PARTIAL, cursor.getInt(12) == 1);
            this.setFlagInternal(Flag.X_REMOTE_COPY_STARTED, cursor.getInt(13) == 1);
            this.setFlagInternal(Flag.X_GOT_ALL_HEADERS, cursor.getInt(14) == 1);

            this.mId = cursor.getLong(0);

            this.mAttachmentCount = cursor.getInt(17);

            final String preview = cursor.getString(16);
            mPreview = (preview == null ? "" : preview);

            Cursor addrCursor = db.query( "addresses",
                    new String[] {"type", "name", "email"},
                    "message_id = ?",
                    new String[] { Long.toString(mId) },
                    null, null, "id");

            try {
                List<Address> to = new ArrayList<Address>();
                List<Address> cc = new ArrayList<Address>();
                List<Address> bcc = new ArrayList<Address>();
                List<Address> replyTo = new ArrayList<Address>();

                while (addrCursor.moveToNext()) {
                    String type = addrCursor.getString(0);
                    String name = addrCursor.getString(1);
                    String email = addrCursor.getString(2);

                    Address address = new Address(email, name);

                    if ("FROM".equals(type)) {
                        this.setFrom(address);
                    } else if ("TO".equals(type)) {
                        to.add(address);
                    } else if ("CC".equals(type)) {
                        cc.add(address);
                    } else if ("BCC".equals(type)) {
                        bcc.add(address);
                    } else if ("REPLY_TO".equals(type)) {
                        replyTo.add(address);
                    }
                }

                this.setRecipients(RecipientType.TO, to.toArray(EMPTY_ADDRESS_ARRAY));
                this.setRecipients(RecipientType.CC, cc.toArray(EMPTY_ADDRESS_ARRAY));
                this.setRecipients(RecipientType.BCC, bcc.toArray(EMPTY_ADDRESS_ARRAY));
                this.setReplyTo(replyTo.toArray(EMPTY_ADDRESS_ARRAY));

            } finally {
                if (addrCursor != null) {
                    addrCursor.close();
                }
            }
        }

        /**
         * Fetch the message text for display. This always returns an HTML-ified version of the
         * message, even if it was originally a text-only message.
         * @return HTML version of message for display purposes or null.
         * @throws MessagingException
         */
        public String getTextForDisplay() throws MessagingException {
            String text = null;    // First try and fetch an HTML part.
            Part part = MimeUtility.findFirstPartByMimeType(this, "text/html");
            if (part == null) {
                // If that fails, try and get a text part.
                part = MimeUtility.findFirstPartByMimeType(this, "text/plain");
                if (part != null && part.getBody() instanceof LocalStore.LocalTextBody) {
                    text = ((LocalStore.LocalTextBody) part.getBody()).getBodyForDisplay();
                }
            } else {
                // We successfully found an HTML part; do the necessary character set decoding.
                text = MimeUtility.getTextFromPart(part);
            }
            return text;
        }


        /* Custom version of writeTo that updates the MIME message based on localMessage
         * changes.
         */

        @Override
        public void writeTo(OutputStream out) throws IOException, MessagingException {
            if (mMessageDirty) buildMimeRepresentation();
            super.writeTo(out);
        }

        private void buildMimeRepresentation() throws MessagingException {
            if (!mMessageDirty) {
                return;
            }

            super.setSubject(mSubject);
            if (this.mFrom != null && this.mFrom.length > 0) {
                super.setFrom(this.mFrom[0]);
            }

            super.setReplyTo(mReplyTo);
            super.setSentDate(this.getSentDate());
            super.setRecipients(RecipientType.TO, mTo);
            super.setRecipients(RecipientType.CC, mCc);
            super.setRecipients(RecipientType.BCC, mBcc);
            if (mMessageId != null) super.setMessageId(mMessageId);

            mMessageDirty = false;
        }

        public String getPreview() {
            return mPreview;
        }

        @Override
        public String getSubject() {
            return mSubject;
        }


        @Override
        public void setSubject(String subject) throws MessagingException {
            mSubject = subject;
            mMessageDirty = true;
        }


        @Override
        public void setMessageId(String messageId) {
            mMessageId = messageId;
            mMessageDirty = true;
        }

        public boolean hasAttachments() {
            if (mAttachmentCount > 0) {
                return true;
            } else {
                return false;
            }

        }

        public int getAttachmentCount() {
            return mAttachmentCount;
        }

        @Override
        public void setFrom(Address from) throws MessagingException {
            this.mFrom = new Address[] { from };
            mMessageDirty = true;
        }


        @Override
        public void setReplyTo(Address[] replyTo) throws MessagingException {
            if (replyTo == null || replyTo.length == 0) {
                mReplyTo = null;
            } else {
                mReplyTo = replyTo;
            }
            mMessageDirty = true;
        }


        /*
         * For performance reasons, we add headers instead of setting them (see super implementation)
         * which removes (expensive) them before adding them
         */
        @Override
        public void setRecipients(RecipientType type, Address[] addresses) throws MessagingException {
            if (type == RecipientType.TO) {
                if (addresses == null || addresses.length == 0) {
                    this.mTo = null;
                } else {
                    this.mTo = addresses;
                }
            } else if (type == RecipientType.CC) {
                if (addresses == null || addresses.length == 0) {
                    this.mCc = null;
                } else {
                    this.mCc = addresses;
                }
            } else if (type == RecipientType.BCC) {
                if (addresses == null || addresses.length == 0) {
                    this.mBcc = null;
                } else {
                    this.mBcc = addresses;
                }
            } else {
                throw new MessagingException("Unrecognized recipient type.");
            }
            mMessageDirty = true;
        }



        public boolean toMe() {
            try {
                if (!mToMeCalculated) {
                    for (Address address : getRecipients(RecipientType.TO)) {
                        if (mAccount.isAnIdentity(address)) {
                            mToMe = true;
                            mToMeCalculated = true;
                        }
                    }
                }
            } catch (MessagingException e) {
                // do something better than ignore this
                // getRecipients can throw a messagingexception
            }
            return mToMe;
        }





        public boolean ccMe() {
            try {

                if (!mCcMeCalculated) {
                    for (Address address : getRecipients(RecipientType.CC)) {
                        if (mAccount.isAnIdentity(address)) {
                            mCcMe = true;
                            mCcMeCalculated = true;
                        }
                    }

                }
            } catch (MessagingException e) {
                // do something better than ignore this
                // getRecipients can throw a messagingexception
            }

            return mCcMe;
        }






        public void setFlagInternal(Flag flag, boolean set) throws MessagingException {
            super.setFlag(flag, set);
        }

        public long getId() {
            return mId;
        }

        @Override
        public void setFlag(final Flag flag, final boolean set) throws MessagingException {

            try {
                database.execute(true, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {
                        try {
                            if (flag == Flag.DELETED && set) {
                                delete();
                            }

                            updateFolderCountsOnFlag(flag, set);


                            LocalMessage.super.setFlag(flag, set);
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        /*
                         * Set the flags on the message.
                         */
                        String field = flagToDatabaseField(flag);
                        if (field != null) {
                            ContentValues cv = new ContentValues();
                            cv.put(field, set);
                            db.update("messages", cv, "id = ?", new String[] { Long.toString(mId) });
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }


        }

        /*
         * If a message is being marked as deleted we want to clear out it's content
         * and attachments as well. Delete will not actually remove the row since we need
         * to retain the uid for synchronization purposes.
         */
        private void delete() throws MessagingException

        {
            /*
             * Delete all of the message's content to save space.
             */
            try {
                database.execute(true, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException, UnavailableStorageException {

                        ContentValues cv = new ContentValues();
                        cv.put("deleted", true);
                        cv.put("notified", false);
                        cv.putNull("date");
                        cv.putNull("internal_date");
                        db.update("messages", cv, "id = ?", new String[] { Long.toString(getId()) });

                        try {
                            deleteMessageContents(db, getId());
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        private void deleteMessageContents(SQLiteDatabase db, long messageId) throws MessagingException {
            String[] messageIdArg = new String[] { Long.toString(getId()) };

            ((LocalFolder) mFolder).deleteAttachments(mId);

            db.execSQL(
                    "DELETE FROM message_part_cache " +
                    "WHERE message_part_id IN " +
                    "(SELECT id FROM message_parts WHERE message_id = ?)",
                    messageIdArg);

            db.execSQL(
                    "DELETE FROM message_part_attributes " +
                    "WHERE message_part_id IN " +
                    "(SELECT id FROM message_parts WHERE message_id = ?)",
                    messageIdArg);

            db.delete("addresses", "message_id = ?", messageIdArg);
            db.delete("message_parts", "message_id = ?", messageIdArg);
            db.delete("message_cache", "message_id = ?", messageIdArg);
        }

        /*
         * Completely remove a message from the local database
         */
        @Override
        public void destroy() throws MessagingException {
            try {
                database.execute(true, new DbCallback<Void>() {
                    @Override
                    public Void doDbWork(final SQLiteDatabase db) throws WrappedException,
                        UnavailableStorageException {
                        try {
                            updateFolderCountsOnFlag(Flag.X_DESTROYED, true);

                            deleteMessageContents(db, mId);
                            db.delete("messages", "id = ?", new String[] { Long.toString(mId) });
                        } catch (MessagingException e) {
                            throw new WrappedException(e);
                        }
                        return null;
                    }
                });
            } catch (WrappedException e) {
                throw(MessagingException) e.getCause();
            }
        }

        private void updateFolderCountsOnFlag(Flag flag, boolean set) {
            /*
             * Update the unread count on the folder.
             */
            try {
                LocalFolder folder = (LocalFolder)mFolder;
                if (flag == Flag.DELETED || flag == Flag.X_DESTROYED) {
                    if (!isSet(Flag.SEEN)) {
                        folder.setUnreadMessageCount(folder.getUnreadMessageCount() + (set ? -1 : 1));
                    }
                    if (isSet(Flag.FLAGGED)) {
                        folder.setFlaggedMessageCount(folder.getFlaggedMessageCount() + (set ? -1 : 1));
                    }
                }


                if (!isSet(Flag.DELETED)) {

                    if (flag == Flag.SEEN) {
                        if (set != isSet(Flag.SEEN)) {
                            folder.setUnreadMessageCount(folder.getUnreadMessageCount() + (set ? -1 : 1));
                        }
                    }

                    if (flag == Flag.FLAGGED) {
                        folder.setFlaggedMessageCount(folder.getFlaggedMessageCount() + (set ?  1 : -1));
                    }
                }
            } catch (MessagingException me) {
                Log.e(K9.LOG_TAG, "Unable to update LocalStore unread message count",
                      me);
                throw new RuntimeException(me);
            }
        }

        private void loadHeaders() throws UnavailableStorageException {
            ArrayList<LocalMessage> messages = new ArrayList<LocalMessage>();
            messages.add(this);
            mHeadersLoaded = true; // set true before calling populate headers to stop recursion
            ((LocalFolder) mFolder).populateHeaders(messages);
        }

        @Override
        public void addHeader(String name, String value) throws UnavailableStorageException {
            if (!mHeadersLoaded)
                loadHeaders();
            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) throws UnavailableStorageException {
            if (!mHeadersLoaded)
                loadHeaders();
            super.setHeader(name, value);
        }

        @Override
        public String[] getHeader(String name) throws UnavailableStorageException {
            if (!mHeadersLoaded)
                loadHeaders();
            return super.getHeader(name);
        }

        @Override
        public void removeHeader(String name) throws UnavailableStorageException {
            if (!mHeadersLoaded)
                loadHeaders();
            super.removeHeader(name);
        }

        @Override
        public Set<String> getHeaderNames() throws UnavailableStorageException {
            if (!mHeadersLoaded)
                loadHeaders();
            return super.getHeaderNames();
        }
    }

    public static class LocalAttachmentBodyPart extends MimeBodyPart {
        private long mAttachmentId = -1;

        public LocalAttachmentBodyPart(Body body, long attachmentId) throws MessagingException {
            super(body);
            mAttachmentId = attachmentId;
        }

        /**
         * Returns the local attachment id of this body, or -1 if it is not stored.
         * @return
         */
        public long getAttachmentId() {
            return mAttachmentId;
        }

        public void setAttachmentId(long attachmentId) {
            mAttachmentId = attachmentId;
        }

        @Override
        public String toString() {
            return "" + mAttachmentId;
        }
    }

    public static class LocalAttachmentBody implements Body {
        private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
        private Application mApplication;
        private Uri mUri;

        public LocalAttachmentBody(Uri uri, Application application) {
            mApplication = application;
            mUri = uri;
        }

        public InputStream getInputStream() throws MessagingException {
            try {
                return mApplication.getContentResolver().openInputStream(mUri);
            } catch (FileNotFoundException fnfe) {
                /*
                 * Since it's completely normal for us to try to serve up attachments that
                 * have been blown away, we just return an empty stream.
                 */
                return new ByteArrayInputStream(EMPTY_BYTE_ARRAY);
            }
        }

        public void writeTo(OutputStream out) throws IOException, MessagingException {
            InputStream in = getInputStream();
            Base64OutputStream base64Out = new Base64OutputStream(out);
            IOUtils.copy(in, base64Out);
            base64Out.close();
        }

        public Uri getContentUri() {
            return mUri;
        }
    }

    private static class PartDescription {
        public final Part part;
        public final long messageId;
        public final long parentId;
        public final int order;

        PartDescription(Part part, long messageId, long parentId, int order) {
            this.part = part;
            this.messageId = messageId;
            this.parentId = parentId;
            this.order = order;
        }
    }
}
