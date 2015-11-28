package com.fsck.k9.remote;


import java.util.List;
import java.util.Map;


public interface MoveStatus {
    List<String> getServerIdsForRetries();
    List<String> getServerIdsForReverts();
    Map<String, String> getServerIdMappingForSuccessfulMoves();
}
