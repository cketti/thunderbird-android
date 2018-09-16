/* Copyright (C) 2010 The Android Open Source Project.
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

package com.fsck.k9.protocol.eas.adapter;


import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.protocol.eas.Eas;
import com.fsck.k9.protocol.eas.LogUtils;


/**
 * Parse the result of a MoveItems command.
 */
public class MoveItemsParser extends Parser {
    private static final String TAG = Eas.LOG_TAG;

    // These are the EAS status codes for MoveItems
    private static final int STATUS_NO_SOURCE_FOLDER = 1;
    private static final int STATUS_NO_DESTINATION_FOLDER = 2;
    private static final int STATUS_SUCCESS = 3;
    private static final int STATUS_SOURCE_DESTINATION_SAME = 4;
    private static final int STATUS_INTERNAL_ERROR = 5;
    private static final int STATUS_ALREADY_EXISTS = 6;
    private static final int STATUS_LOCKED = 7;

    // These are the status values we return to callers
    public static final int STATUS_CODE_SUCCESS = 1;
    public static final int STATUS_CODE_REVERT = 2;
    public static final int STATUS_CODE_RETRY = 3;


    private int statusCode = 0;
    private String newServerId;
    private String sourceServerId;


    public MoveItemsParser(InputStream in) throws IOException {
        super(in);
    }

    public int getStatusCode() {
        if (statusCode == 0) {
            LogUtils.e(TAG, "Trying to get status for MoveItems, but no status was set");
            // TODO: We currently treat empty responses as retry, so for now we'll do the same for
            // partially empty responses.
            return STATUS_CODE_RETRY;
        }
        return statusCode;
    }

    public String getNewServerId() {
        return newServerId;
    }

    public String getSourceServerId() {
        return sourceServerId;
    }

    @Override
    public boolean parse() throws IOException {
        if (nextTag(START_DOCUMENT) != Tags.MOVE_MOVE_ITEMS) {
            throw new EasParserException();
        }

        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            if (tag == Tags.MOVE_RESPONSE) {
                parseResponse();
            } else {
                skipTag();
            }
        }

        return false;
    }

    private void parseResponse() throws IOException {
        while (nextTag(Tags.MOVE_RESPONSE) != END) {
            if (tag == Tags.MOVE_STATUS) {
                int status = getValueInt();

                setStatusCode(status);

                if (status != STATUS_SUCCESS) {
                    // There's not much to be done if this fails
                    LogUtils.w(TAG, "Error in MoveItems: %d", status);
                }
            } else if (tag == Tags.MOVE_DSTMSGID) {
                newServerId = getValue();
                LogUtils.d(TAG, "Moved message id is now: %s", newServerId);
            } else if (tag == Tags.MOVE_SRCMSGID) {
                sourceServerId = getValue();
                LogUtils.d(TAG, "Source message id is: %s", sourceServerId);
            } else {
                skipTag();
            }
        }
    }

    private void setStatusCode(int status) {
        // Convert the EAS status code with our external codes
        switch(status) {
            case STATUS_SUCCESS:
            case STATUS_SOURCE_DESTINATION_SAME:
            case STATUS_ALREADY_EXISTS: {
                // Same destination and already exists are ok with us; we'll continue as
                // if the move succeeded
                statusCode = STATUS_CODE_SUCCESS;
                break;
            }
            case STATUS_LOCKED: {
                // This sounds like a transient error, so we can safely retry
                statusCode = STATUS_CODE_RETRY;
                break;
            }
            case STATUS_NO_SOURCE_FOLDER:
            case STATUS_NO_DESTINATION_FOLDER:
            case STATUS_INTERNAL_ERROR:
            default: {
                // These are non-recoverable, so we'll revert the message to its original
                // mailbox.  If there's an unknown response, revert
                statusCode = STATUS_CODE_REVERT;
                break;
            }
        }
    }
}

