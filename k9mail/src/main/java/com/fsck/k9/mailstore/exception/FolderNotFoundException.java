package com.fsck.k9.mailstore.exception;


import com.fsck.k9.mail.MessagingException;


public class FolderNotFoundException extends MessagingException {
    public FolderNotFoundException(String message) {
        super(message);
    }
}
