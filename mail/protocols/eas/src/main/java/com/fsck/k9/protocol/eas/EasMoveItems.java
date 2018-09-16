package com.fsck.k9.protocol.eas;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.protocol.eas.adapter.MoveItemsParser;
import com.fsck.k9.protocol.eas.adapter.Serializer;
import com.fsck.k9.protocol.eas.adapter.Tags;
import okhttp3.RequestBody;


/**
 * Performs a MoveItems request, which is used to move items between collections.
 * See http://msdn.microsoft.com/en-us/library/ee160102(v=exchg.80).aspx for more details.
 * TODO: Investigate how this interacts with ItemOperations.
 */
public class EasMoveItems extends EasOperation {
    /** Result code indicating that no moved messages were found for this account. */
    public final static int RESULT_NO_MESSAGES = 0;
    public final static int RESULT_OK = 1;
    public final static int RESULT_EMPTY_RESPONSE = 2;


    private MessageMove move;
    private MoveResponse moveResponse;
    private final Map<String, Integer> moveStatus = new HashMap<String, Integer>();
    private final Map<String, String> serverIdMapping = new HashMap<String, String>();


    public EasMoveItems(Context context, Account account) {
        super(context, account);
    }

    // TODO: Allow multiple messages in one request. Requires parser changes.
    public int upsyncMovedMessages(List<MessageMove> moves) throws MessagingException {
        if (moves == null) {
            return RESULT_NO_MESSAGES;
        }

        int result = RESULT_NO_MESSAGES;

        for (MessageMove move : moves) {
            this.move = move;
            if (result >= 0) {
                // If our previous time through the loop succeeded, keep making server requests.
                // Otherwise, we carry through the loop for all messages with the last error
                // response, which will stop trying this iteration and force the rest of the
                // messages into the retry state.
                result = performOperation();
            }

            final int status;
            if (result >= 0) {
                if (result == RESULT_OK) {
                    status = moveResponse.moveStatus;
                    if (status == MoveItemsParser.STATUS_CODE_SUCCESS) {
                        serverIdMapping.put(moveResponse.sourceMessageId, moveResponse.newMessageId);
                    }
                } else {
                    // TODO: Should this really be a retry?
                    // We got a 200 response with an empty payload. It's not clear we ought to
                    // retry, but this is how our implementation has worked in the past.
                    status = MoveItemsParser.STATUS_CODE_RETRY;
                }
            } else {
                // performOperation returned a negative status code, indicating a failure before the
                // server actually was able to tell us yea or nay, so we must retry.
                status = MoveItemsParser.STATUS_CODE_RETRY;
            }

            moveStatus.put(move.serverId(), status);

            if (status <= 0) {
                LogUtils.e(LOG_TAG, "MoveItems gave us an invalid status %d", status);
            }
        }

        return (result >= 0) ? RESULT_OK : result;
    }

    @Override
    protected String getCommand() {
        return "MoveItems";
    }

    @Override
    protected RequestBody getRequestBody() throws IOException {
        Serializer serializer = new Serializer();
        serializer.start(Tags.MOVE_MOVE_ITEMS);
        serializer.start(Tags.MOVE_MOVE);
        serializer.data(Tags.MOVE_SRCMSGID, move.serverId());
        serializer.data(Tags.MOVE_SRCFLDID, move.sourceFolderId());
        serializer.data(Tags.MOVE_DSTFLDID, move.destinationFolderId());
        serializer.end();
        serializer.end().done();

        return makeRequestBody(serializer);
    }

    @Override
    protected int handleResponse(EasResponse response) throws IOException {
        if (response.isEmpty()) {
            return RESULT_EMPTY_RESPONSE;
        }

        MoveItemsParser parser = new MoveItemsParser(response.getInputStream());
        parser.parse();

        String sourceMessageId = parser.getSourceServerId();
        String newMessageId = parser.getNewServerId();
        int status = parser.getStatusCode();

        moveResponse = new MoveResponse(sourceMessageId, newMessageId, status);

        return RESULT_OK;
    }

    public Map<String, Integer> getMoveStatus() {
        return moveStatus;
    }

    public Map<String, String> getMoveResults() {
        return serverIdMapping;
    }


    private static class MoveResponse {
        public final String sourceMessageId;
        public final String newMessageId;
        public final int moveStatus;

        public MoveResponse(String srcMsgId, String dstMsgId, int status) {
            sourceMessageId = srcMsgId;
            newMessageId = dstMsgId;
            moveStatus = status;
        }
    }
}
