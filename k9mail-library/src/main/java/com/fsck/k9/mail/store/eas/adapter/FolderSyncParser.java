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

import com.fsck.k9.mail.store.eas.CommandStatusException;
import com.fsck.k9.mail.store.eas.callback.FolderSyncCallback;

import static com.fsck.k9.mail.util.Preconditions.checkNotNull;


/**
 * Parse the result of a FolderSync command
 * <p/>
 * Notifies the callback of the addition, deletion, and changes to folders in the user's Exchange account.
 **/
public class FolderSyncParser extends Parser {
    private final FolderSyncCallback callback;
    private final FolderSyncController controller;


    public FolderSyncParser(InputStream inputStream, FolderSyncCallback callback,
            FolderSyncController controller) throws IOException {
        super(inputStream);
        this.callback = checkNotNull(callback, "Argument 'callback' can't be null");
        this.controller = checkNotNull(controller, "Argument 'controller' can't be null");
    }

    @Override
    public boolean parse() throws IOException, CommandStatusException {
        if (nextTag(START_DOCUMENT) != Tags.FOLDER_FOLDER_SYNC) {
            throw new EasParserException();
        }

        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            if (tag == Tags.FOLDER_STATUS) {
                int status = getValueInt();
                handleFolderStatus(status);
            } else if (tag == Tags.FOLDER_SYNC_KEY) {
                String newKey = getValue();
                handleNewSyncKey(newKey);
            } else if (tag == Tags.FOLDER_CHANGES) {
                changesParser();
            } else {
                skipTag();
            }
        }

        commit();

        return false;
    }

    private void deleteParser() throws IOException {
        while (nextTag(Tags.FOLDER_DELETE) != END) {
            switch (tag) {
                case Tags.FOLDER_SERVER_ID: {
                    String serverId = getValue();
                    callback.removeFolder(serverId);
                    break;
                }
                default: {
                    skipTag();
                }
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
                case Tags.FOLDER_SERVER_ID: {
                    serverId = getValue();
                    break;
                }
                case Tags.FOLDER_DISPLAY_NAME: {
                    displayName = getValue();
                    break;
                }
                case Tags.FOLDER_PARENT_ID: {
                    parentId = getValue();
                    break;
                }
                default: {
                    skipTag();
                    break;
                }
            }
        }

        // We'll make a change if one of parentId or displayName are specified
        // serverId is required, but let's be careful just the same
        if (serverId != null && (displayName != null || parentId != null)) {
            callback.changeFolder(serverId, displayName, parentId);
        }
    }

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
            } else {
                skipTag();
            }
        }
    }

    private void commit() throws IOException {
        callback.commitFolderChanges();
    }

    private void handleFolderStatus(int status) {
        controller.folderStatus(status);
    }

    private void handleNewSyncKey(String newKey) {
        if (newKey != null) {
            controller.updateSyncKey(newKey);
        }
    }
}
