package com.fsck.k9.mail.store.eas;


import android.content.Context;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class PolicyServiceProxy {
    public static void remoteWipe(Context context) {
        throw new RuntimeException("Not implemented");
    }

    public static void setAccountPolicy(Context context, long accountId, Policy policy, String securityKey) {
        //FIXME
    }

    public static boolean isActive(Context context, Policy policy) {
        //FIXME
        return true;
    }

    public static boolean canDisableCamera(Context context) {
        throw new RuntimeException("Not implemented");
    }
}
