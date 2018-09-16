package com.fsck.k9.protocol.eas.callback;


public interface BaseSyncCallback {
    /**
     * @return {@code true} iff the current sync key is "0".
     */
    boolean isFirstSync();

    /**
     * @return {@code true} iff the new sync key is different from the old one.
     */
    boolean setSyncKey(String syncKey);

    void prepareSyncRestart();
}
