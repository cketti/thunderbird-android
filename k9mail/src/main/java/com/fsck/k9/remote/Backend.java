package com.fsck.k9.remote;


import java.util.List;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.data.Message;


public interface Backend {
    boolean syncFolders();

    boolean syncFolder(String serverId);

    boolean sendMessage(Message message);

    boolean setFlag(String folderServerId, List<String> messageServerIds, Flag flag, boolean newState);
}
