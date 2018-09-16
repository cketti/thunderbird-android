package com.fsck.k9.backend.eas;


import com.fsck.k9.mail.MessagingException;


public class AccessDeniedException extends MessagingException {
    public AccessDeniedException() {
        super("Access denied");
    }
}
