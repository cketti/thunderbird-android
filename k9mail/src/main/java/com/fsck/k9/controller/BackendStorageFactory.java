package com.fsck.k9.controller;


import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.mailstore.MailStore;
import com.fsck.k9.mailstore.MailStoreFactory;


class BackendStorageFactory {
    private final MailStoreFactory mailStoreFactory;


    BackendStorageFactory(MailStoreFactory mailStoreFactory) {
        this.mailStoreFactory = mailStoreFactory;
    }

    public static BackendStorageFactory newInstance(Context context) {
        MailStoreFactory mailStoreFactory = MailStoreFactory.newInstance(context);
        return new BackendStorageFactory(mailStoreFactory);
    }

    public BackendStorage createBackendStorage(Account account) {
        MailStore mailStore = mailStoreFactory.createMailStore(account);
        return new BackendStorage(account, mailStore);
    }
}
