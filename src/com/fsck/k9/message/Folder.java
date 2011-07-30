package com.fsck.k9.message;

public interface Folder {
    public String getName();
    public String getInternalName();
    public String getAttribute(String key);
    public void setAttribute(String key, String value);
}
