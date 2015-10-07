/*
 * Copyright (C) 2008-2009 Marc Blank
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fsck.k9.mail.store.eas.adapter;


import java.io.IOException;
import java.io.InputStream;

import android.content.Context;

import com.fsck.k9.mail.store.eas.Account;
import com.fsck.k9.mail.store.eas.CommandStatusException;
import com.fsck.k9.mail.store.eas.callback.FolderSyncCallback;


/**
 * Parse the result of a FolderSync command
 *
 * Notifies the callback of the addition, deletion, and changes to folders in the user's Exchange account.
 **/
public class FolderSyncParser extends Parser {

    public static final String TAG = "FolderSyncParser";

    private final FolderSyncCallback callback;
    private final FolderSyncController controller;

    /** Indicates whether this sync is an initial FolderSync. */
    private boolean mInitialSync;
    /** Indicates whether the sync response provided a different sync key than we had. */
    private boolean mSyncKeyChanged = false;

    private final Account mAccount;

    // If true, we only care about status (this is true when validating an account) and ignore
    // other data
    private final boolean mStatusOnly;

    public FolderSyncParser(Context context, InputStream in, Account account, boolean statusOnly,
            FolderSyncCallback callback, FolderSyncController controller) throws IOException {
        super(in);
        mAccount = account;
        mStatusOnly = statusOnly;
        this.callback = checkNotNull(callback, "Argument 'callback' can't be null");
        this.controller = checkNotNull(controller, "Argument 'controller' can't be null");
    }

    @Override
    public boolean parse() throws IOException, CommandStatusException {
        int status;
        boolean res = false;
        boolean resetFolders = false;
        mInitialSync = (mAccount.mSyncKey == null) || "0".equals(mAccount.mSyncKey);
        if (mInitialSync) {
            wipe();
        }
        if (nextTag(START_DOCUMENT) != Tags.FOLDER_FOLDER_SYNC)
            throw new EasParserException();
        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            if (tag == Tags.FOLDER_STATUS) {
                status = getValueInt();
                handleFolderStatus(status);
            } else if (tag == Tags.FOLDER_SYNC_KEY) {
                final String newKey = getValue();
                handleNewSyncKey(resetFolders, newKey);
            } else if (tag == Tags.FOLDER_CHANGES) {
                if (mStatusOnly) return res;
                changesParser();
            } else
                skipTag();
        }
        if (!mStatusOnly) {
            commit();
        }
        return res;
    }

    private void deleteParser() throws IOException {
        while (nextTag(Tags.FOLDER_DELETE) != END) {
            switch (tag) {
                case Tags.FOLDER_SERVER_ID:
                    final String serverId = getValue();
                    callback.removeFolder(serverId);
                    break;
                default:
                    skipTag();
            }
        }
    }

    private void addParser() throws IOException {
        String name = null;
        String serverId = null;
        String parentId = null;
        int type = 0;

        while (nextTag(Tags.FOLDER_ADD) != END) {
            switch (tag) {
                case Tags.FOLDER_DISPLAY_NAME: {
                    name = getValue();
                    break;
                }
                case Tags.FOLDER_TYPE: {
                    type = getValueInt();
                    break;
                }
                case Tags.FOLDER_PARENT_ID: {
                    parentId = getValue();
                    break;
                }
                case Tags.FOLDER_SERVER_ID: {
                    serverId = getValue();
                    break;
                }
                default:
                    skipTag();
            }
        }
        if (name != null && serverId != null && parentId != null) {
            callback.addFolder(serverId, name, type, parentId);
        }
    }

    private void updateParser() throws IOException {
        String serverId = null;
        String displayName = null;
        String parentId = null;
        while (nextTag(Tags.FOLDER_UPDATE) != END) {
            switch (tag) {
                case Tags.FOLDER_SERVER_ID:
                    serverId = getValue();
                    break;
                case Tags.FOLDER_DISPLAY_NAME:
                    displayName = getValue();
                    break;
                case Tags.FOLDER_PARENT_ID:
                    parentId = getValue();
                    break;
                default:
                    skipTag();
                    break;
            }
        }
        // We'll make a change if one of parentId or displayName are specified
        // serverId is required, but let's be careful just the same
        if (serverId != null && (displayName != null || parentId != null)) {
            callback.changeFolder(serverId, displayName, parentId);
        }
    }

    /**
     * Handle the Changes element of the FolderSync response. This is the container for Add, Delete,
     * and Update elements.
     * @throws IOException
     */
    private void changesParser() throws IOException {
        while (nextTag(Tags.FOLDER_CHANGES) != END) {
            if (tag == Tags.FOLDER_ADD) {
                addParser();
            } else if (tag == Tags.FOLDER_DELETE) {
                deleteParser();
            } else if (tag == Tags.FOLDER_UPDATE) {
                updateParser();
            } else if (tag == Tags.FOLDER_COUNT) {
                // TODO: Maybe we can make use of this count somehow.
                getValueInt();
            } else
                skipTag();
        }
    }

    private void commit() throws IOException {
        callback.commitFolderChanges();
    }

    private void wipe() {
        callback.clearFolders();
    }

    private void handleFolderStatus(int status) {
        controller.folderStatus(status);
    }

    private void handleNewSyncKey(boolean resetFolders, String newKey) {
        if (newKey != null && !resetFolders) {
            controller.updateSyncKey(newKey);
            mSyncKeyChanged = !newKey.equals(mAccount.mSyncKey);
        }
    }

    private <T> T checkNotNull(T object, String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
        return object;
    }
}
