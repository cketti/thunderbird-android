package com.fsck.k9.remote;


import com.fsck.k9.mail.data.MessageServerData;


public interface BackendStorage {
    String getFoldersSyncKey();
    void setFoldersSyncKey(String syncKey);
    String getPolicyKey();
    void setPolicyKey(String policyKey);

    void createFolder(String name, BackendFolderType backendFolderType, String serverId, String parentServerId);
    void deleteFolderByServerId(String serverId);
    void deleteAllFolders();

    void createMessage(MessageServerData messageServerData);
}
