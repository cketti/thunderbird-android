package com.fsck.k9.mailstore;


import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.mail.MessagingException;


public class MailStoreFactory {
    private final Context context;


    private MailStoreFactory(Context context) {
        this.context = context;
    }

    public static MailStoreFactory newInstance(Context context) {
        return new MailStoreFactory(context);
    }

    public MailStore createMailStore(Account account) {
        try {
            LocalStore localStore = LocalStore.getInstance(account, context);
            return new MailStore(localStore);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
