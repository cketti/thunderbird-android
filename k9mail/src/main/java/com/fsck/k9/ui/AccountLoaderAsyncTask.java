package com.fsck.k9.ui;


import android.content.Context;
import android.os.AsyncTask;

import com.fsck.k9.Account;
import com.fsck.k9.Preferences;


public class AccountLoaderAsyncTask extends AsyncTask<Void, Void, Account> {
    private final Context context;
    private final String accountUuid;
    private final AccountLoaderCallback callback;


    private AccountLoaderAsyncTask(Context context, String accountUuid, AccountLoaderCallback callback) {
        this.context = context;
        this.accountUuid = accountUuid;
        this.callback = callback;
    }

    public static void loadAccount(Context context, String accountUuid, AccountLoaderCallback callback) {
        AccountLoaderAsyncTask accountLoader = new AccountLoaderAsyncTask(context, accountUuid, callback);
        accountLoader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    protected Account doInBackground(Void... params) {
        return Preferences.getPreferences(context).getAccount(accountUuid);
    }

    @Override
    protected void onPostExecute(Account account) {
        callback.onAccountLoaded(account);
    }


    public interface AccountLoaderCallback {
        void onAccountLoaded(Account account);
    }
}
