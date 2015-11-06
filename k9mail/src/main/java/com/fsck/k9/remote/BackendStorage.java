package com.fsck.k9.remote;


import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.data.MessageServerData;


public interface BackendStorage {
    String getFoldersSyncKey();
    void setFoldersSyncKey(String syncKey);
    String getPolicyKey();
    void setPolicyKey(String policyKey);
    String getDeviceId();
    void setDeviceId(String deviceId);

    String getSyncKeyForFolder(String serverId);
    void setSyncKeyForFolder(String serverId, String syncKey);

    void createFolder(String name, BackendFolderType backendFolderType, String serverId, String parentServerId);
    void changeFolder(String serverId, String name, String parentServerId);
    void deleteFolderByServerId(String serverId);
    void deleteAllFolders();

    void createMessage(MessageServerData messageServerData);
    void removeMessage(String folderServerId, String messageServerId);
    void setMessageFlag(String folderServerId, String messageServerId, Flag flag, boolean state);
    void removeAllMessages(String folderServerId);
}
