package com.fsck.k9.remote;


public interface Backend {
    boolean syncFolders();

    boolean syncFolder(String serverId);
}