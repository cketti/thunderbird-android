package com.fsck.k9.preferences;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.search.ConditionsTreeNode;
import com.fsck.k9.search.SearchSpecification.SearchCondition;

public class Storage implements SharedPreferences {
    private static ConcurrentHashMap<Context, Storage> storages =
        new ConcurrentHashMap<Context, Storage>();

    private volatile ConcurrentHashMap<String, String> preferenceStorage = new ConcurrentHashMap<String, String>();

    private CopyOnWriteArrayList<OnSharedPreferenceChangeListener> listeners =
        new CopyOnWriteArrayList<OnSharedPreferenceChangeListener>();

    private int DB_VERSION = 3;
    private String DB_NAME = "preferences_storage";

    private ThreadLocal<ConcurrentHashMap<String, String>> workingStorage
    = new ThreadLocal<ConcurrentHashMap<String, String>>();
    private ThreadLocal<SQLiteDatabase> workingDB =
        new ThreadLocal<SQLiteDatabase>();
    private ThreadLocal<ArrayList<String>> workingChangedKeys = new ThreadLocal<ArrayList<String>>();


    private Context context = null;

    private SQLiteDatabase openDB() {
        SQLiteDatabase mDb = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null);

        if (mDb.getVersion() < 1) {
            Log.i(K9.LOG_TAG, "Creating Storage database");
            mDb.execSQL("DROP TABLE IF EXISTS preferences_storage");
            mDb.execSQL("CREATE TABLE preferences_storage " +
                        "(primkey TEXT PRIMARY KEY ON CONFLICT REPLACE, value TEXT)");
        }

        if (mDb.getVersion() < 2) {
            Log.i(K9.LOG_TAG, "Updating preferences to urlencoded username/password");

            String accountUuids = readPreferenceValue(mDb, "accountUuids");
            if (accountUuids != null && accountUuids.length() != 0) {
                String[] uuids = accountUuids.split(",");
                for (String uuid : uuids) {
                    try {
                        String storeUriStr = Utility.base64Decode(readPreferenceValue(mDb, uuid + ".storeUri"));
                        String transportUriStr = Utility.base64Decode(readPreferenceValue(mDb, uuid + ".transportUri"));

                        URI uri = new URI(transportUriStr);
                        String newUserInfo = null;
                        if (transportUriStr != null) {
                            String[] userInfoParts = uri.getUserInfo().split(":");

                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");
                            String passwordEnc = "";
                            String authType = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }
                            if (userInfoParts.length > 2) {
                                authType = ":" + userInfoParts[2];
                            }

                            newUserInfo = usernameEnc + passwordEnc + authType;
                        }

                        if (newUserInfo != null) {
                            URI newUri = new URI(uri.getScheme(), newUserInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                            String newTransportUriStr = Utility.base64Encode(newUri.toString());
                            writePreferenceValue(mDb, uuid + ".transportUri", newTransportUriStr);
                        }

                        uri = new URI(storeUriStr);
                        newUserInfo = null;
                        if (storeUriStr.startsWith("imap")) {
                            String[] userInfoParts = uri.getUserInfo().split(":");
                            if (userInfoParts.length == 2) {
                                String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");
                                String passwordEnc = URLEncoder.encode(userInfoParts[1], "UTF-8");

                                newUserInfo = usernameEnc + ":" + passwordEnc;
                            } else {
                                String authType = userInfoParts[0];
                                String usernameEnc = URLEncoder.encode(userInfoParts[1], "UTF-8");
                                String passwordEnc = URLEncoder.encode(userInfoParts[2], "UTF-8");

                                newUserInfo = authType + ":" + usernameEnc + ":" + passwordEnc;
                            }
                        } else if (storeUriStr.startsWith("pop3")) {
                            String[] userInfoParts = uri.getUserInfo().split(":", 2);
                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");

                            String passwordEnc = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }

                            newUserInfo = usernameEnc + passwordEnc;
                        } else if (storeUriStr.startsWith("webdav")) {
                            String[] userInfoParts = uri.getUserInfo().split(":", 2);
                            String usernameEnc = URLEncoder.encode(userInfoParts[0], "UTF-8");

                            String passwordEnc = "";
                            if (userInfoParts.length > 1) {
                                passwordEnc = ":" + URLEncoder.encode(userInfoParts[1], "UTF-8");
                            }

                            newUserInfo = usernameEnc + passwordEnc;
                        }

                        if (newUserInfo != null) {
                            URI newUri = new URI(uri.getScheme(), newUserInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                            String newStoreUriStr = Utility.base64Encode(newUri.toString());
                            writePreferenceValue(mDb, uuid + ".storeUri", newStoreUriStr);
                        }
                    } catch (Exception e) {
                        Log.e(K9.LOG_TAG, "ooops", e);
                    }
                }
            }
        }

        if (mDb.getVersion() < 3) {
            mDb.execSQL("DROP TABLE IF EXISTS searches");
            mDb.execSQL("CREATE TABLE searches (" +
                        "id INTEGER PRIMARY KEY, name TEXT UNIQUE, accounts TEXT, predefined TEXT)");

            mDb.execSQL("DROP TABLE IF EXISTS search_conditions");
            mDb.execSQL("CREATE TABLE search_conditions (" +
                        "id INTEGER PRIMARY KEY, search_id INTEGER REFERENCES searches, tree_id INTEGER, " +
                        "key TEXT, value TEXT, attribute TEXT, lft INTEGER, rgt INTEGER, op TEXT)");
        }

        mDb.setVersion(DB_VERSION);
        return mDb;
    }


    public static Storage getStorage(Context context) {
        Storage tmpStorage = storages.get(context);
        if (tmpStorage != null) {
            if (K9.DEBUG) {
                Log.d(K9.LOG_TAG, "Returning already existing Storage");
            }
            return tmpStorage;
        } else {
            if (K9.DEBUG) {
                Log.d(K9.LOG_TAG, "Creating provisional storage");
            }
            tmpStorage = new Storage(context);
            Storage oldStorage = storages.putIfAbsent(context, tmpStorage);
            if (oldStorage != null) {
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Another thread beat us to creating the Storage, returning that one");
                }
                return oldStorage;
            } else {
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Returning the Storage we created");
                }
                return tmpStorage;
            }
        }
    }


    private void loadPreferenceValues() {
        long startTime = System.currentTimeMillis();
        Log.i(K9.LOG_TAG, "Loading preferences from DB into Storage");
        Cursor cursor = null;
        SQLiteDatabase mDb = null;
        try {
            mDb = openDB();

            cursor = mDb.rawQuery("SELECT primkey, value FROM preferences_storage", null);
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                String value = cursor.getString(1);
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Loading key '" + key + "', value = '" + value + "'");
                }
                preferenceStorage.put(key, value);
            }
        } finally {
            Utility.closeQuietly(cursor);
            if (mDb != null) {
                mDb.close();
            }
            long endTime = System.currentTimeMillis();
            Log.i(K9.LOG_TAG, "Preferences load took " + (endTime - startTime) + "ms");
        }
    }

    private Storage(Context context) {
        this.context = context;
        loadPreferenceValues();
    }

    /* ********************************************************************
     *  Preferences Database Interaction
     * ********************************************************************/
    private void preferenceKeyChange(String key) {
        ArrayList<String> changedKeys = workingChangedKeys.get();
        if (!changedKeys.contains(key)) {
            changedKeys.add(key);
        }
    }

    protected void putPreference(String key, String value) {
        ContentValues cv = generatePreferenceCV(key, value);
        workingDB.get().insert("preferences_storage", "primkey", cv);
        liveUpdatePreference(key, value);
    }

    protected void putPreferences(Map<String, String> insertables) {
        String sql = "INSERT INTO preferences_storage (primkey, value) VALUES (?, ?)";
        SQLiteStatement stmt = workingDB.get().compileStatement(sql);

        for (Map.Entry<String, String> entry : insertables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            stmt.bindString(1, key);
            stmt.bindString(2, value);
            stmt.execute();
            stmt.clearBindings();
            liveUpdatePreference(key, value);
        }
        stmt.close();
    }

    private ContentValues generatePreferenceCV(String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("primkey", key);
        cv.put("value", value);
        return cv;
    }

    private void liveUpdatePreference(String key, String value) {
        workingStorage.get().put(key, value);

        preferenceKeyChange(key);
    }

    protected void removePreference(String key) {
        workingDB.get().delete("preferences_storage", "primkey = ?", new String[] { key });
        workingStorage.get().remove(key);

        preferenceKeyChange(key);
    }

    protected void removeAllPreferences() {
        for (String key : workingStorage.get().keySet()) {
            preferenceKeyChange(key);
        }
        workingDB.get().execSQL("DELETE FROM preferences_storage");
        workingStorage.get().clear();
    }

    protected void doInPreferenceTransaction(Runnable dbWork) {
        ConcurrentHashMap<String, String> newStorage = new ConcurrentHashMap<String, String>();
        newStorage.putAll(preferenceStorage);
        workingStorage.set(newStorage);

        SQLiteDatabase mDb = openDB();
        workingDB.set(mDb);

        ArrayList<String> changedKeys = new ArrayList<String>();
        workingChangedKeys.set(changedKeys);

        mDb.beginTransaction();
        try {
            dbWork.run();
            mDb.setTransactionSuccessful();
            preferenceStorage = newStorage;
            for (String changedKey : changedKeys) {
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(this, changedKey);
                }
            }
        } finally {
            workingDB.remove();
            workingStorage.remove();
            workingChangedKeys.remove();
            mDb.endTransaction();
            mDb.close();
        }
    }

    private String readPreferenceValue(SQLiteDatabase mDb, String key) {
        Cursor cursor = null;
        String value = null;
        try {
            cursor = mDb.query(
                         "preferences_storage",
                         new String[] {"value"},
                         "primkey = ?",
                         new String[] {key},
                         null,
                         null,
                         null);

            if (cursor.moveToNext()) {
                value = cursor.getString(0);
                if (K9.DEBUG) {
                    Log.d(K9.LOG_TAG, "Loading key '" + key + "', value = '" + value + "'");
                }
            }
        } finally {
            Utility.closeQuietly(cursor);
        }

        return value;
    }

    private void writePreferenceValue(SQLiteDatabase mDb, String key, String value) {
        ContentValues cv = new ContentValues();
        cv.put("primkey", key);
        cv.put("value", value);

        long result = mDb.insert("preferences_storage", "primkey", cv);

        if (result == -1) {
            Log.e(K9.LOG_TAG, "Error writing key '" + key + "', value = '" + value + "'");
        }
    }

    public long preferencesSize() {
        return preferenceStorage.size();
    }

    /* ********************************************************************
     *  Generic Database Interaction
     * ********************************************************************/
    // TODO change back to protected
    public void doInTransaction(Runnable dbWork) {
        SQLiteDatabase mDb = openDB();
        workingDB.set(mDb);

        mDb.beginTransaction();
        try {
            dbWork.run();
            mDb.setTransactionSuccessful();
        } finally {
            workingDB.remove();
            mDb.endTransaction();
            mDb.close();
        }
    }

    /* ********************************************************************
     *  SharedPreferences Interface Implementation
     * ********************************************************************/
    //@Override
    public boolean contains(String key) {
        return preferenceStorage.contains(key);
    }

    //@Override
    public com.fsck.k9.preferences.Editor edit() {
        return new com.fsck.k9.preferences.Editor(this);
    }

    //@Override
    public Map<String, String> getAll() {
        return preferenceStorage;
    }

    //@Override
    public boolean getBoolean(String key, boolean defValue) {
        String val = preferenceStorage.get(key);
        if (val == null) {
            return defValue;
        }
        return Boolean.parseBoolean(val);
    }

    //@Override
    public float getFloat(String key, float defValue) {
        String val = preferenceStorage.get(key);
        if (val == null) {
            return defValue;
        }
        return Float.parseFloat(val);
    }

    //@Override
    public int getInt(String key, int defValue) {
        String val = preferenceStorage.get(key);
        if (val == null) {
            return defValue;
        }
        return Integer.parseInt(val);
    }

    //@Override
    public long getLong(String key, long defValue) {
        String val = preferenceStorage.get(key);
        if (val == null) {
            return defValue;
        }
        return Long.parseLong(val);
    }

    //@Override
    public String getString(String key, String defValue) {
        String val = preferenceStorage.get(key);
        if (val == null) {
            return defValue;
        }
        return val;
    }

    //@Override
    public void registerOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {
        listeners.addIfAbsent(listener);
    }

    //@Override
    public void unregisterOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public Set<String> getStringSet(String arg0, Set<String> arg1) {
        throw new RuntimeException("Not implemented");
    }

    /*
     * Temporarily method until this database has a nicer interface.
     */
    public void removeSearchConditions(long searchId){
        workingDB.get().delete("search_conditions", "search_id = ?", new String[] { Long.toString(searchId) });
    }

    public void removeSearch(String searchName){
        workingDB.get().delete("searches", "name = ?", new String[] { searchName });
    }

    public long addSearch(String name, String accounts, boolean predefined){

        boolean inTransaction = true;
        SQLiteDatabase mDb = workingDB.get();
        if (mDb == null) {
            mDb = openDB();
            inTransaction = false;
        }

        // API 7 doesn't have insertWithOnConflict yet so manually
        String query = "INSERT OR IGNORE INTO searches (name, accounts, predefined) VALUES ('" +
        name + "', '" + accounts + "', '" + String.valueOf(predefined) + "');";
        SQLiteStatement stmt = mDb.compileStatement(query);

        try {
            return stmt.executeInsert();
        } finally {
            stmt.close();
            if (!inTransaction) {
                mDb.close();
            }
        }
    }

    /*
     * We store the conditions tree in the database. See the following link for
     * the approach ( nested sets ):
     *
     * http://www.sitepoint.com/hierarchical-data-database-2/
     */
    public void addSearchConditions(long searchId, ConditionsTreeNode conditions){

        String sql = "INSERT INTO search_conditions " +
                "(search_id, tree_id, key, value, attribute, lft, rgt, op) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        boolean inTransaction = true;
        SQLiteDatabase mDb = workingDB.get();
        if (mDb == null) {
            mDb = openDB();
            inTransaction = false;
        }

        SQLiteStatement stmt = mDb.compileStatement(sql);
        conditions.applyMPTTLabel();

        for (ConditionsTreeNode node : conditions.preorder()) {
            stmt.clearBindings();
            stmt.bindLong(5, node.mLeftMPTTMarker);
            stmt.bindLong(6, node.mRightMPTTMarker);
            stmt.bindLong(1, searchId);
            stmt.bindString(7, node.mValue.toString());

            SearchCondition tmpCondition = node.getCondition();

            if (tmpCondition != null) {
                stmt.bindString(2, tmpCondition.field.toString());
                stmt.bindString(3, tmpCondition.value);
                stmt.bindString(4, tmpCondition.attribute.toString());
            }

            stmt.executeInsert();
        }

        stmt.close();
        if (!inTransaction) {
            mDb.close();
        }
    }

    public ConditionsTreeNode getSearchConditions(long searchId) {
        Cursor cursor = null;

        boolean inTransaction = true;
        SQLiteDatabase mDb = workingDB.get();
        if (mDb == null) {
            mDb = openDB();
            inTransaction = false;
        }

        try {
            cursor = mDb.rawQuery("SELECT tree_id, key, value, attribute, lft, rgt, op " +
                    "FROM search_conditions WHERE search_id = " + String.valueOf(searchId) + " ORDER BY tree_id ASC, lft ASC", null);

            return ConditionsTreeNode.buildTreeFromDB(cursor);
        } finally {
            Utility.closeQuietly(cursor);
            if (!inTransaction) {
                mDb.close();
            }
        }
    }

    /**
     * Returns the metadata belonging to the provided searchID in a
     * list. The order of the data will be the same as the order in the
     * table of the database.
     *
     * @param searchId Id of the saved search.
     * @return List of the metadata
     *
     * TODO this is not a very clean solution for when we return other
     * data then strings..
     */
    public List<String> getMetaForSearch(Long searchId) {
        List<String> tmp = new ArrayList<String>();

        boolean inTransaction = true;
        SQLiteDatabase mDb = workingDB.get();
        if (mDb == null) {
            mDb = openDB();
            inTransaction = false;
        }

        Cursor cursor = null;

        try {
            cursor = mDb.rawQuery("SELECT accounts, predefined FROM searches " +
                    "WHERE id = '" + String.valueOf(searchId) + "'", null);

            // should be only 1 hit
            if (cursor.moveToNext()) {
                tmp.add(cursor.getString(0));
                tmp.add(cursor.getString(1));
            }
        } finally {
            Utility.closeQuietly(cursor);
            if (!inTransaction) {
                mDb.close();
            }
        }

        return tmp;
    }

    public Map<String, Long> getSavedSearchesIndex(boolean predefined){
        Map<String, Long> tmp = new HashMap<String, Long>();

        boolean inTransaction = true;
        SQLiteDatabase mDb = workingDB.get();
        if (mDb == null) {
            mDb = openDB();
            inTransaction = false;
        }

        Cursor cursor = null;

        try {
            cursor = mDb.rawQuery("SELECT id, name, predefined FROM searches", new String[] {});
            while (cursor.moveToNext()) {
                boolean isPredefined = Boolean.parseBoolean(cursor.getString(2));
                if (predefined == isPredefined)
                    tmp.put(cursor.getString(1), cursor.getLong(0));
            }
        } finally {
            Utility.closeQuietly(cursor);
            if (!inTransaction) {
                mDb.close();
            }
        }

        return tmp;
    }

    public SQLiteDatabase openDatabase() { return null; }
    public void closeDatabase() {}
}
