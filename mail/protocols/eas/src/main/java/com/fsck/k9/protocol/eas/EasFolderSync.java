/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.fsck.k9.protocol.eas;


import java.io.IOException;

import android.content.Context;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.protocol.eas.adapter.FolderSyncController;
import com.fsck.k9.protocol.eas.adapter.FolderSyncParser;
import com.fsck.k9.protocol.eas.adapter.Serializer;
import com.fsck.k9.protocol.eas.adapter.Tags;
import com.fsck.k9.protocol.eas.callback.FolderSyncCallback;
import okhttp3.RequestBody;


/**
 * Implements the EAS FolderSync command
 *
 * See http://msdn.microsoft.com/en-us/library/ee237648(v=exchg.80).aspx for more details.
 */
public class EasFolderSync extends EasOperation {
    public static final int RESULT_OK = 1;


    private final FolderSyncCallback callback;


    public EasFolderSync(Context context, Account account, FolderSyncCallback callback) {
        super(context, account);
        this.callback = callback;
    }

    @Override
    public int performOperation() throws MessagingException {
        LogUtils.d(LOG_TAG, "Performing FolderSync for account %d", getAccountId());
        clearFoldersBeforeInitialSync();

        return super.performOperation();
    }

    private void clearFoldersBeforeInitialSync() {
        boolean initialSync = (mAccount.mSyncKey == null) || "0".equals(mAccount.mSyncKey);
        if (initialSync) {
            callback.clearFolders();
        }
    }

    @Override
    protected String getCommand() {
        return "FolderSync";
    }

    @Override
    protected RequestBody getRequestBody() throws IOException {
        String syncKey = mAccount.mSyncKey != null ? mAccount.mSyncKey : "0";

        Serializer serializer = new Serializer();
        serializer.start(Tags.FOLDER_FOLDER_SYNC)
                .start(Tags.FOLDER_SYNC_KEY).text(syncKey).end()
                .end().done();

        return makeRequestBody(serializer);
    }

    @Override
    protected int handleResponse(EasResponse response) throws IOException, CommandStatusException {
        if (!response.isEmpty()) {
            EasFolderSyncController controller = new EasFolderSyncController();
            FolderSyncParser folderSyncParser = new FolderSyncParser(response.getInputStream(), callback, controller);
            folderSyncParser.parse();
        }

        return RESULT_OK;
    }

    @Override
    protected boolean handleForbidden() {
        return true;
    }

    class EasFolderSyncController implements FolderSyncController {
        @Override
        public void updateSyncKey(String syncKey) {
            mAccount.mSyncKey = syncKey;
        }

        @Override
        public void folderStatus(int status) throws CommandStatusException {
            if (status != Eas.FOLDER_STATUS_OK) {
                if (status == Eas.FOLDER_STATUS_INVALID_KEY) {
                    callback.clearFolders();
                    updateSyncKey("0");
                    //TODO: trigger another folder sync
                } else if (status == Eas.FOLDER_STATUS_INVALID_REQUEST) {
                    //TODO: log exception
                } else {
                    throw new CommandStatusException(status);
                }
            }
        }
    }
}
