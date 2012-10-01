package com.fsck.k9.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import com.fsck.k9.helper.Utility;
import com.fsck.k9.preferences.Storage;


/*
 * Temporarily class until the global database has a nicer interface.
 */

public class SearchesStorage {

    private Storage mStorage;

    public SearchesStorage(Context context) {
        mStorage = Storage.getStorage(context);
    }

    void removeSearchConditions(long searchId) {
        mStorage.openDatabase().delete("search_conditions", "search_id = ?",
                new String[] { Long.toString(searchId) });
        mStorage.closeDatabase();
    }

    protected void removeSearch(String searchName) {
        mStorage.openDatabase().delete("searches", "name = ?", new String[] { searchName });
        mStorage.closeDatabase();
    }

    protected long addSearch(String name, String accounts, boolean predefined) {
        // API 7 doesn't have insertWithOnConflict yet so manually
        String query = "INSERT OR IGNORE INTO searches (name, accounts, predefined) VALUES ('" +
                name + "', '" + accounts + "', '" + String.valueOf(predefined) + "');";
        SQLiteStatement stmt = mStorage.openDatabase().compileStatement(query);

        try {
            return stmt.executeInsert();
        } finally {
            stmt.close();
            mStorage.closeDatabase();
        }
    }

    /*
     * We store the conditions tree in the database. See the following link for
     * the approach ( nested sets ):
     *
     * http://www.sitepoint.com/hierarchical-data-database-2/
     */
    protected void addSearchConditions(long searchId, ConditionsTreeNode conditions) {

    }

    public ConditionsTreeNode getSearchConditions(long searchId) {
        Cursor cursor = null;
        try {
            cursor = mStorage.openDatabase().rawQuery("SELECT key, value, attribute, lft, rgt, op" +
                    "FROM search_conditions WHERE search_id = " + String.valueOf(searchId) +
                    " ORDER BY lft ASC", null);

            return ConditionsTreeNode.buildTreeFromDB(cursor);
        } finally {
            Utility.closeQuietly(cursor);
            mStorage.closeDatabase();
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
    protected List<String> getMetaForSearch(Long searchId) {
        List<String> tmp = new ArrayList<String>();

        Cursor cursor = null;

        try {
            cursor = mStorage.openDatabase().rawQuery("SELECT accounts, predefined FROM searches " +
                    "WHERE id = '" + String.valueOf(searchId) + "'", null);

            // should be only 1 hit
            if (cursor.moveToNext()) {
                tmp.add(cursor.getString(0));
                tmp.add(cursor.getString(1));
            }
        } finally {
            Utility.closeQuietly(cursor);
            mStorage.closeDatabase();
        }

        return tmp;
    }

    protected Map<String, Long> getSavedSearchesIndex(boolean predefined) {
        Map<String, Long> tmp = new HashMap<String, Long>();

        Cursor cursor = null;

        try {
            cursor = mStorage.openDatabase().rawQuery("SELECT id, name, predefined FROM searches",
                    new String[] {});
            while (cursor.moveToNext()) {
                boolean isPredefined = Boolean.parseBoolean(cursor.getString(2));
                if (predefined == isPredefined) {
                    tmp.put(cursor.getString(1), cursor.getLong(0));
                }
            }
        } finally {
            Utility.closeQuietly(cursor);
            mStorage.closeDatabase();
        }

        return tmp;
    }

    public void doInTransaction(Runnable runnable) {
        mStorage.doInTransaction(runnable);
    }

}
