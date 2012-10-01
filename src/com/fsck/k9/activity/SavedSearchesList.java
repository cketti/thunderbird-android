package com.fsck.k9.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.fsck.k9.R;
import com.fsck.k9.search.SavedSearchesManager;

public class SavedSearchesList extends K9ListActivity {

    public static void displayList(Context context) {
        Intent intent = new Intent(context, SavedSearchesList.class);
        context.startActivity(intent);
    }

    SavedSearchesManager mSearchesManager;
    Context mContext;
    ArrayAdapter<String> mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        mSearchesManager = SavedSearchesManager.getInstance(mContext);

        // configure listview behaviour
        ListView mListView = getListView();
        mListView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        mListView.setLongClickable(true);
        mListView.setFastScrollEnabled(true);
        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MessageList.actionDisplaySavedSearch(mContext, mAdapter.getItem(position), false);
            }
        });

        mAdapter = new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1,
                mSearchesManager.listUserSavedSearches());
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        mAdapter.clear();

        for( String ss : mSearchesManager.listUserSavedSearches()) {
            mAdapter.add(ss);
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getSupportMenuInflater().inflate(R.menu.saved_searches_option, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.new_saved_search: {
                ConfigureSearch.actionCreateSavedSearch(mContext);
                break;
            }
            default: {
                super.onOptionsItemSelected(item);
                break;
            }
        }

        return true;
    }
}
