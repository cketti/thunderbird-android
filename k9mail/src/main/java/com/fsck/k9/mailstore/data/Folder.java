package com.fsck.k9.mailstore.data;


import com.fsck.k9.mail.Folder.FolderClass;
import com.fsck.k9.mailstore.FolderType;
import com.fsck.k9.mailstore.LocalFolder.MoreMessages;


public class Folder {
    public long id = -1;
    public String name;
    public long lastUpdated;
    public int unreadCount;
    public int visibleLimit;
    public String status;
    public String pushState;
    public long lastPushed;
    public int flagCount = 0;
    public boolean integrate;
    public boolean inTopGroup;
    public FolderClass syncClass;
    public FolderClass pushClass;
    public FolderClass displayClass;
    public FolderClass notifyClass;
    public MoreMessages moreMessages = MoreMessages.UNKNOWN;
    public String serverId;
    public long parent = -1;
    public String syncKey = "0";
    public FolderType type;
}
