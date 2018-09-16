package com.fsck.k9.backend.eas;


import java.io.IOException;

import android.content.Context;

import com.fsck.k9.backend.eas.EasBackend.EasAccount;
import com.fsck.k9.mail.AuthenticationFailedException;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.protocol.eas.EasOperation;
import com.fsck.k9.protocol.eas.EasOutboxSync;


class EmailSend {
    private final Context context;
    private final EasAccount account;


    EmailSend(Context context, EasAccount account) {
        this.context = context;
        this.account = account;
    }

    public boolean sendMessage(Message message) throws MessagingException, IOException {
        String messageIdentifier = getMessageIdentifierForLogging(message);

        EasOutboxSync easOutboxSync = new EasOutboxSync(context, account, message, messageIdentifier);
        int result = easOutboxSync.performOperation();

        if (result == EasOperation.RESULT_AUTHENTICATION_ERROR) {
            throw new AuthenticationFailedException("Authentication failed");
        } else if (result == EasOperation.RESULT_NETWORK_PROBLEM) {
            throw new IOException();
        }

        return result == EasOutboxSync.RESULT_OK;
    }

    private String getMessageIdentifierForLogging(Message message) {
        String messageId = message.header().value("Message-Id");
        return messageId != null ? messageId : "unknown";
    }
}
