package com.fsck.k9.remote.eas;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.Context;

import com.fsck.k9.mail.store.eas.EasMoveItems;
import com.fsck.k9.mail.store.eas.MessageMove;
import com.fsck.k9.mail.store.eas.adapter.MoveItemsParser;
import com.fsck.k9.remote.MoveStatus;
import com.fsck.k9.remote.eas.EasBackend.EasAccount;


public class EmailMove {
    private final Context context;
    private final EasAccount account;


    public EmailMove(Context context, EasAccount account) {
        this.context = context;
        this.account = account;
    }

    public MoveStatus moveMessages(String sourceFolderServerId, String destinationFolderServerId,
            List<String> messageServerIds) {

        List<MessageMove> moves = createMoves(sourceFolderServerId, destinationFolderServerId, messageServerIds);

        EasMoveItems easMoveItems = new EasMoveItems(context, account);
        easMoveItems.upsyncMovedMessages(moves);

        Map<String, Integer> moveStatus = easMoveItems.getMoveStatus();
        Map<String, String> moveResults = easMoveItems.getMoveResults();

        return new EasMoveStatus(moveStatus, moveResults);
    }

    private List<MessageMove> createMoves(String sourceFolderId, String destinationFolderId, List<String> serverIds) {
        List<MessageMove> moves = new ArrayList<MessageMove>(serverIds.size());
        for (String messageServerId : serverIds) {
            MessageMove move = MessageMove.builder()
                    .sourceFolderId(sourceFolderId)
                    .destinationFolderId(destinationFolderId)
                    .serverId(messageServerId)
                    .build();

            moves.add(move);
        }

        return moves;
    }


    private static class EasMoveStatus implements MoveStatus {
        private List<String> retries = new ArrayList<String>();
        private List<String> reverts = new ArrayList<String>();
        private final Map<String, String> moveResults;


        public EasMoveStatus(Map<String, Integer> moveStatus, Map<String, String> moveResults) {
            this.moveResults = moveResults;
            init(moveStatus);
        }

        private void init(Map<String, Integer> moveStatus) {
            for (Entry<String, Integer> entry : moveStatus.entrySet()) {
                String serverId = entry.getKey();
                int status = entry.getValue();

                switch (status) {
                    case MoveItemsParser.STATUS_CODE_SUCCESS: {
                        break;
                    }
                    case MoveItemsParser.STATUS_CODE_RETRY: {
                        retries.add(serverId);
                        break;
                    }
                    case MoveItemsParser.STATUS_CODE_REVERT: {
                        reverts.add(serverId);
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown EAS move status: " + status);
                    }
                }
            }
        }

        @Override
        public List<String> getServerIdsForRetries() {
            return Collections.unmodifiableList(retries);
        }

        @Override
        public List<String> getServerIdsForReverts() {
            return Collections.unmodifiableList(reverts);
        }

        @Override
        public Map<String, String> getServerIdMappingForSuccessfulMoves() {
            return Collections.unmodifiableMap(moveResults);
        }
    }
}
