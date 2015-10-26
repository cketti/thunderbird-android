package com.fsck.k9.remote.eas;


import android.content.Context;
import android.net.Uri;

import com.fsck.k9.Account;
import com.fsck.k9.mail.store.eas.HostAuth;
import com.fsck.k9.remote.BackendStorage;
import com.fsck.k9.remote.Backend;


public class EasBackend implements Backend {
    private final FolderSync folderSync;
    private final com.fsck.k9.mail.store.eas.Account easAccount;


    //TODO: Instead of Account pass EasConfig
    public EasBackend(Context context, Account account, BackendStorage backendStorage) {
        easAccount = createEasAccount(account);
        this.folderSync = new FolderSync(context, easAccount, backendStorage);
    }

    public static String createEasStoreUri(String hostname, String username, String password) {
        return new Uri.Builder()
                .scheme("eas")
                .authority(hostname)
                .appendQueryParameter("username", username)
                .appendQueryParameter("password", password)
                .build()
                .toString();
    }

    @Override
    public boolean syncFolders() {
        return folderSync.syncFolders();
    }

    private com.fsck.k9.mail.store.eas.Account createEasAccount(Account account) {
        Uri storeUri = Uri.parse(account.getStoreUri());

        String username = storeUri.getQueryParameter("username");
        String password = storeUri.getQueryParameter("password");
        String host = storeUri.getHost();

        HostAuth hostAuth = new HostAuth();
        hostAuth.mLogin = username;
        hostAuth.mPassword = password;
        hostAuth.mAddress = host;
        hostAuth.mFlags = HostAuth.FLAG_SSL;

        com.fsck.k9.mail.store.eas.Account easAccount = new com.fsck.k9.mail.store.eas.Account();
        easAccount.mHostAuthRecv = hostAuth;
        easAccount.mEmailAddress = hostAuth.mLogin;

        return easAccount;
    }
}
