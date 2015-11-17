package com.fsck.k9.mail.store.eas.adapter;

import java.io.IOException;
import java.io.InputStream;


public class SendMailParser extends Parser {
    private final int startTag;
    private int status;


    public SendMailParser(InputStream inputStream, int startTag) throws IOException {
        super(inputStream);
        this.startTag = startTag;
    }

    public int getStatus() {
        return status;
    }

    @Override
    public boolean parse() throws IOException {
        if (nextTag(START_DOCUMENT) != startTag) {
            throw new IOException();
        }

        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            if (tag == Tags.COMPOSE_STATUS) {
                status = getValueInt();
            } else {
                skipTag();
            }
        }

        return true;
    }
}
