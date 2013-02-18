package com.fsck.k9.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.Uri;

import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.store.LocalStore.LocalFolder;
import com.fsck.k9.mail.store.LocalStore.LocalMessage;
import com.fsck.k9.provider.EmailProvider;

public class EmailProviderCache {
    private static Context sContext;
    private static Map<String, EmailProviderCache> sInstances =
            new HashMap<String, EmailProviderCache>();

    public static synchronized EmailProviderCache getInstanceForAccount(String accountUuid,
            Context context) {

        if (sContext == null) {
            sContext = context.getApplicationContext();
        }

        EmailProviderCache instance = sInstances.get(accountUuid);
        if (instance == null) {
            instance = new EmailProviderCache(accountUuid);
            sInstances.put(accountUuid, instance);
        }

        return instance;
    }


    private Map<Long, Map<String, String>> mMessageCache = new HashMap<Long, Map<String, String>>();
    private Map<Long, Map<String, String>> mThreadCache = new HashMap<Long, Map<String, String>>();
    private String mAccountUuid;
    private Map<Long, Long> mHiddenMessageCache = new HashMap<Long, Long>();

    private EmailProviderCache(String accountUuid) {
        mAccountUuid = accountUuid;
    }

    public String getValue(Long messageId, String columnName) {
        synchronized (mMessageCache) {
            Map<String, String> map = mMessageCache.get(messageId);
            return (map == null) ? null : map.get(columnName);
        }
    }

    public String getValueForThread(Long threadRootId, String columnName) {
        synchronized (mThreadCache) {
            Map<String, String> map = mThreadCache.get(threadRootId);
            return (map == null) ? null : map.get(columnName);
        }
    }

    public void setValues(long messageId, Map<String, String> values) {
        synchronized (mMessageCache) {
            Map<String, String> map = mMessageCache.get(messageId);
            if (map == null) {
                mMessageCache.put(messageId, values);
            } else {
                map.putAll(values);
            }
        }

        notifyChange();
    }

    public void setValue(Long messageId, String columnName, String value) {
        synchronized (mMessageCache) {
            Map<String, String> map = mMessageCache.get(messageId);
            if (map == null) {
                map = new HashMap<String, String>();
                mMessageCache.put(messageId, map);
            }
            map.put(columnName, value);
        }

        notifyChange();
    }

    public void setValueForThread(Long threadRootId, String columnName, String value) {
        synchronized (mThreadCache) {
            Map<String, String> map = mThreadCache.get(threadRootId);
            if (map == null) {
                map = new HashMap<String, String>();
                mThreadCache.put(threadRootId, map);
            }
            map.put(columnName, value);
        }

        notifyChange();
    }


    public void removeEntry(Long messageId, String columnName) {
        synchronized (mMessageCache) {
            Map<String, String> map = mMessageCache.get(messageId);
            if (map != null) {
                map.remove(columnName);
                if (map.size() == 0) {
                    mMessageCache.remove(messageId);
                }
            }
        }

        notifyChange();
    }

    public void hideMessageInFolder(Long messageId, Long folderId) {
        synchronized (mHiddenMessageCache) {
            mHiddenMessageCache.put(messageId, folderId);
        }

        notifyChange();
    }

    public void hideMessages(List<Message> messages) {
        synchronized (mHiddenMessageCache) {
            for (Message message : messages) {
                LocalMessage localMessage = (LocalMessage) message;
                long messageId = localMessage.getId();
                long folderId = ((LocalFolder) localMessage.getFolder()).getId();
                mHiddenMessageCache.put(messageId, folderId);
            }
        }

        notifyChange();
    }

    public boolean isMessageHidden(Long messageId, long folderId) {
        synchronized (mHiddenMessageCache) {
            Long hiddenInFolder = mHiddenMessageCache.get(messageId);
            return (hiddenInFolder != null && hiddenInFolder.longValue() == folderId);
        }
    }

    public void unHideMessage(Long messageId, long folderId) {
        synchronized (mHiddenMessageCache) {
            Long hiddenInFolder = mHiddenMessageCache.get(messageId);

            if (hiddenInFolder != null && hiddenInFolder.longValue() == folderId) {
                mHiddenMessageCache.remove(messageId);
            }
        }
        notifyChange();
    }

    public void unHideMessages(Message[] messages) {
        synchronized (mHiddenMessageCache) {
            for (Message message : messages) {
                LocalMessage localMessage = (LocalMessage) message;
                long messageId = localMessage.getId();
                long folderId = ((LocalFolder) localMessage.getFolder()).getId();
                Long hiddenInFolder = mHiddenMessageCache.get(messageId);

                if (hiddenInFolder != null && hiddenInFolder.longValue() == folderId) {
                    mHiddenMessageCache.remove(messageId);
                }
            }
        }
        notifyChange();
    }


    public void notifyChange() {
        Uri uri = Uri.withAppendedPath(EmailProvider.CONTENT_URI, "account/" + mAccountUuid + "/messages");
        sContext.getContentResolver().notifyChange(uri, null);
    }
}
