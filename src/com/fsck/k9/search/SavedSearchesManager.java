package com.fsck.k9.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.util.Log;

import com.fsck.k9.K9;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.preferences.Storage;


public class SavedSearchesManager {

    private Storage mStorage;

    private Map<String, Long> mUserSearchesIdMap = new HashMap<String, Long>();

    private Map<String, Long> mPredefinedSearchesIdMap = new HashMap<String, Long>();

    private SavedSearchesManager(Context context) {
        mStorage = Storage.getStorage(context);
        mUserSearchesIdMap = mStorage.getSavedSearchesIndex(false);
        mPredefinedSearchesIdMap = mStorage.getSavedSearchesIndex(true);
    }

    /* ********************************************************************
     *  Singleton Pattern
     * ********************************************************************/
    private static SavedSearchesManager mInstance = null;

    public static SavedSearchesManager getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SavedSearchesManager(context.getApplicationContext());
        }

        return mInstance;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new UnsupportedOperationException();
    }

    /* ********************************************************************
     *  Interaction
     * ********************************************************************/

    /**
     * Saves the passed SavedSearch to the database. If it already existed
     * everything will be overwritten. If it's a new search new entries will
     * be created in all the relevant tables.
     *
     * @param s The saved search to make persistent.
     */
    public void save(final LocalSearch s) {
        try {
            save(s, false);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * Save a new predefined search. Used to hardcode searches for 'Unified Inbox',
     * 'All Messages',... features.
     *
     * @param s Search to define.
     */
    public void define(final LocalSearch s) {
        try {
            save(s, true);
        } catch (Exception e) {
            Log.e(K9.LOG_TAG, "Failed to save a search: " + e.getMessage());
            return;
        }
    }

    private void save(final LocalSearch s, final boolean predefined) throws Exception {
        if (s.getName() == null) {
            throw new Exception("Can't save search without a name was set.");
        }

        long startTime = System.currentTimeMillis();

        mStorage.doInTransaction(new Runnable() {
            @Override
            public void run() {
                /*
                 *  update of existing search
                 *  we remove all conditions and read them because keeping
                 *  track of what exactly changed would require even more resources
                 *  considering the how often this probably will happen
                 */
                if (mUserSearchesIdMap.containsKey(s.getName())) {
                    long searchId = mUserSearchesIdMap.get(s.getName());
                    mStorage.removeSearchConditions(searchId);
                }

                // update or insert metadata
                long searchId;

                String tmpAccounts = Utility.combine(s.getAccountUuids(), ',');
                if (tmpAccounts == null) {
                    tmpAccounts = SearchSpecification.ALL_ACCOUNTS;
                }

                searchId = mStorage.addSearch(s.getName(), tmpAccounts, predefined);
                assert(searchId >= 0);

                // add conditions to the db
                // empty conditions = select all messages
                if (s.getConditions() != null) {
                    mStorage.addSearchConditions(searchId, s.getConditions());
                }

                // reflect change locally
                mUserSearchesIdMap.remove(s.getName());
                mUserSearchesIdMap.put(s.getName(), searchId);
            }
        });

        long endTime = System.currentTimeMillis();
        Log.i(K9.LOG_TAG, "Saving a SavedSearch took " + (endTime - startTime) + "ms");
    }

    /**
     * Will delete the saved search from every relevant table.
     *
     * @param s Search to delete
     */
    public void delete(final LocalSearch s) {
        long startTime = System.currentTimeMillis();

        mStorage.doInTransaction(new Runnable() {
            @Override
            public void run() {
                mStorage.removeSearchConditions(mUserSearchesIdMap.get(s.getName()));
                mStorage.removeSearch(s.getName());
                mUserSearchesIdMap.remove(s.getName());
            }
        });

        long endTime = System.currentTimeMillis();
        Log.i(K9.LOG_TAG, "Deleting a SavedSearch took " + (endTime - startTime) + "ms");
    }

    /**
     * Load a saved search specified by it's name from the
     * database.
     *
     * @param name Name of the search to load.
     * @return Loaded search.
     */
    public LocalSearch load(String name) {
        long startTime = System.currentTimeMillis();
        Long searchId = mUserSearchesIdMap.get(name);
        if (searchId == null) {
            searchId = mPredefinedSearchesIdMap.get(name);
        }
        /*
         * Our index is always synced with the db,
         * if it's not in the index it doesn't exist.
         */
        if (searchId == null) {
            return null;
        }

        // get related accounts
        List<String> meta = mStorage.getMetaForSearch(searchId);

        // get conditions
        ConditionsTreeNode conditions = mStorage.getSearchConditions(searchId);

        // end timing and build the search object to return
        long endTime = System.currentTimeMillis();
        Log.i(K9.LOG_TAG, "Loading a SavedSearch took " + (endTime - startTime) + "ms");

        boolean btmp = Boolean.parseBoolean(meta.get(1));
        String accounts= meta.get(0);

        LocalSearch ss = new LocalSearch(name, conditions, accounts, btmp);
        return ss;
    }

    /**
     * Returns a list of names
     * @return
     */
    public List<String> listUserSavedSearches() {
        return new ArrayList<String>(mUserSearchesIdMap.keySet());
    }

    public List<String> listPredefinedSearches() {
        return new ArrayList<String>(mPredefinedSearchesIdMap.keySet());
    }
}
