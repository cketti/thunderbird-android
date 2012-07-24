package com.fsck.k9.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import com.fsck.k9.K9;
import com.fsck.k9.R;
import com.fsck.k9.search.IllegalConditionException;
import com.fsck.k9.search.LocalSearch;
import com.fsck.k9.search.SavedSearchesManager;
import com.fsck.k9.search.SearchSpecification.ATTRIBUTE;
import com.fsck.k9.search.SearchSpecification.SEARCHFIELD;

public class ConfigureSearch extends K9Activity implements OnClickListener{

/****************************************************************** 
*		STATIC INTENT METHOD AND FIELDS
*******************************************************************/
    
    public static void actionCreateSavedSearch(Context context) {
            Intent intent = new Intent(context, ConfigureSearch.class);
            context.startActivity(intent);
    }
	
	// state
    private LocalSearch mSearch;
    
    // gui fields
    private EditText mNameField;
    private EditText mQueryField;
    
/******************************************************************
*		REAL CLASS CODE
*******************************************************************/
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // set looks
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.configure_search);

        // get ui fields
        mNameField = (EditText) findViewById(R.id.configure_search_name);
        mQueryField = (EditText) findViewById(R.id.configure_search_query);
        
        // configure "add new" button
        ((Button)findViewById(R.id.configure_search_done_action)).setOnClickListener(this);
        
        mSearch = new LocalSearch("");
	}
	
	@Override
	public void onResume() {
        super.onResume();
	}
    
	// TODO should save when focus of field is moved not all at once
	private void saveToSearch() {
		// get name
		mSearch.setName(mNameField.getText().toString());
		
		// get query
		try {
			mSearch.addCondition(SEARCHFIELD.SUBJECT,
					mQueryField.getText().toString(), 
					ATTRIBUTE.CONTAINS);
		} catch (IllegalConditionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// save search
		SavedSearchesManager.getInstance(this).save(mSearch);
	}
	
    @Override
    public void onBackPressed() {
        if (K9.manageBack()) {
            Accounts.listAccounts(this);
            finish();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onClick(View v) {
    	if( v.getId() == R.id.configure_search_done_action ){  	
    		saveToSearch();
    		finish();
    	}
    }
}