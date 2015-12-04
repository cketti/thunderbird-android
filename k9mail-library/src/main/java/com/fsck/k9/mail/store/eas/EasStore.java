package com.fsck.k9.mail.store.eas;


import java.util.List;

import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Store;

/*
 * FIXME: Get rid of this class
 * Right now a lot of places assume each backend/protocol has a corresponding Store implementation. So we provide
 * one until all those places have been cleaned up
 */
public class EasStore extends Store {
    @Override
    public Folder getFolder(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends Folder> getPersonalNamespaces(boolean forceListAll) throws MessagingException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkSettings() throws MessagingException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCopyCapable() {
        return false;
    }

    @Override
    public boolean isMoveCapable() {
        return true;
    }
}
