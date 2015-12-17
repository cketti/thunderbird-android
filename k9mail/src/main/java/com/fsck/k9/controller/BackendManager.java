package com.fsck.k9.controller;


import java.util.HashMap;
import java.util.Map;

import android.content.Context;

import com.fsck.k9.Account;
import com.fsck.k9.remote.Backend;
import com.fsck.k9.remote.eas.EasBackend;


class BackendManager {
    private final Map<String, Backend> instances = new HashMap<String, Backend>();
    private final Map<String, BackendStorage> storages = new HashMap<String, BackendStorage>();
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

    public synchronized Backend getBackend(Account account, MessagingController controller) {
        if (!isBackendSupported(account)) {
            throw new IllegalStateException("This account doesn't support the Backend interface: " + account);
        }

        String accountUuid = account.getUuid();
        Backend backend = instances.get(accountUuid);
        if (backend == null) {
            backend = createBackend(account, controller);
            instances.put(accountUuid, backend);
        }

        return backend;
    }

    public boolean isBackendSupported(Account account) {
        return account.getStoreUri().startsWith("eas");
    }

    private Backend createBackend(Account account, MessagingController controller) {
        BackendStorage backendStorage = createAndSaveBackendStorage(account, controller);
        return new EasBackend(context, account, backendStorage);
    }

    private BackendStorage createAndSaveBackendStorage(Account account, MessagingController controller) {
        BackendStorage backendStorage = backendStorageFactory.createBackendStorage(account, controller);

        String accountUuid = account.getUuid();
        storages.put(accountUuid, backendStorage);

        return backendStorage;
    }

    public synchronized BackendStorage getBackendStorage(Account account) {
        String accountUuid = account.getUuid();
        BackendStorage backendStorage = storages.get(accountUuid);
        if (backendStorage == null) {
            throw new IllegalStateException("No BackendStorage found for account: " + account);
        }

        return backendStorage;
    }
}
