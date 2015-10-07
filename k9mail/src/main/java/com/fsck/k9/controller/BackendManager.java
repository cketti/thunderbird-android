package com.fsck.k9.controller;


import java.util.HashMap;
import java.util.Map;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.remote.Backend;
import com.fsck.k9.remote.BackendStorage;
import com.fsck.k9.remote.eas.EasBackend;


class BackendManager {
    private final Map<String, Backend> instances = new HashMap<String, Backend>();
    private final Context context;
    private final BackendStorageFactory backendStorageFactory;


    private BackendManager(Context context, BackendStorageFactory backendStorageFactory) {
        this.context = context;
        this.backendStorageFactory = backendStorageFactory;
    }

    public static BackendManager newInstance(Context context) {
        BackendStorageFactory backendStorageFactory = BackendStorageFactory.newInstance(context);
        return new BackendManager(context, backendStorageFactory);
    }

    public synchronized Backend getBackend(Account account) {
        if (!isBackendSupported(account)) {
            throw new IllegalStateException("This account doesn't support the Backend interface: " + account);
        }

        String accountUuid = account.getUuid();
        Backend backend = instances.get(accountUuid);
        if (backend == null) {
            backend = createBackend(account);
            instances.put(accountUuid, backend);
        }

        return backend;
    }

    public boolean isBackendSupported(Account account) {
        return account.getStoreUri().startsWith("eas");
    }

    private Backend createBackend(Account account) {
        BackendStorage backendStorage = backendStorageFactory.createBackendStorage(account);
        return new EasBackend(context, account, backendStorage);
    }
}
