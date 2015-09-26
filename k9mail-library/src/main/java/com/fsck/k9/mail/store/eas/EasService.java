package com.fsck.k9.mail.store.eas;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class EasService {
    private static boolean protocolLogging;

    public static boolean getProtocolLogging() {
        return protocolLogging;
    }

    public static boolean getFileLogging() {
        return false;
    }

    public static void setProtocolLogging(boolean enable) {
        protocolLogging = enable;
    }
}
