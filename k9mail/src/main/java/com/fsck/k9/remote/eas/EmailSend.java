package com.fsck.k9.remote.eas;


import android.content.Context;

import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.store.eas.EasOutboxSync;
import com.fsck.k9.remote.eas.EasBackend.EasAccount;


class EmailSend {
    private final Context context;
    private final EasAccount account;


    EmailSend(Context context, EasAccount account) {
        this.context = context;
        this.account = account;
    }

    public boolean sendMessage(Message message) {
        String messageIdentifier = getMessageIdentifierForLogging(message);

        EasOutboxSync easOutboxSync = new EasOutboxSync(context, account, message, messageIdentifier);
        int result = easOutboxSync.performOperation();

        return result == EasOutboxSync.RESULT_OK;
    }

    private String getMessageIdentifierForLogging(Message message) {
        String messageId = message.header().value("Message-Id");
        return messageId != null ? messageId : "unknown";
    }
}
