package com.fsck.k9.mail.message;


public class MessageParserException extends RuntimeException {
    MessageParserException(Exception e) {
        super(e);
    }
}
