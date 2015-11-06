package com.fsck.k9.remote.eas;


import android.content.Context;
import android.net.Uri;

import com.fsck.k9.Account;
import com.fsck.k9.mail.store.eas.HostAuth;
import com.fsck.k9.remote.BackendStorage;
import com.fsck.k9.remote.Backend;


public class EasBackend implements Backend {
    private final FolderSync folderSync;
    private final EmailSync emailSync;
    private final EasAccount easAccount;


    //TODO: Instead of Account pass EasConfig
    public EasBackend(Context context, Account account, BackendStorage backendStorage) {
        easAccount = createEasAccount(account, backendStorage);
        folderSync = new FolderSync(context, easAccount, backendStorage);
        emailSync = new EmailSync(context, easAccount, backendStorage);
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

    @Override
    public boolean syncFolder(String serverId) {
        return emailSync.syncFolder(serverId);
    }

    private EasAccount createEasAccount(Account account, BackendStorage backendStorage) {
        Uri storeUri = Uri.parse(account.getStoreUri());

        String username = storeUri.getQueryParameter("username");
        String password = storeUri.getQueryParameter("password");
        String host = storeUri.getHost();

        HostAuth hostAuth = new HostAuth();
        hostAuth.mLogin = username;
        hostAuth.mPassword = password;
        hostAuth.mAddress = host;
        hostAuth.mFlags = HostAuth.FLAG_SSL;

        EasAccount easAccount = new EasAccount(backendStorage);
        easAccount.mHostAuthRecv = hostAuth;
        easAccount.mEmailAddress = hostAuth.mLogin;

        easAccount.mSyncKey = backendStorage.getFoldersSyncKey();

        String policyKey = backendStorage.getPolicyKey();
        easAccount.setPolicyKeyInternal(policyKey);

        return easAccount;
    }


    static class EasAccount extends com.fsck.k9.mail.store.eas.Account {
        private final BackendStorage backendStorage;


        EasAccount(BackendStorage backendStorage) {
            this.backendStorage = backendStorage;
        }

        @Override
        public void setPolicyKey(String policyKey) {
            setPolicyKeyInternal(policyKey);
            backendStorage.setPolicyKey(policyKey);
        }

        void setPolicyKeyInternal(String policyKey) {
            super.setPolicyKey(policyKey);
        }
    }
}
