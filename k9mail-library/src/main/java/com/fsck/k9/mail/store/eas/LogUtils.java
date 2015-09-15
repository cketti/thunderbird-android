package com.fsck.k9.mail.store.eas;


import android.util.Log;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class LogUtils {
    public static final String TAG = "EAS";

    public static int wtf(String tag, String format, Object... args) {
        return Log.wtf(tag, String.format(format, args), new Error());
    }

    public static int wtf(String tag, Throwable tr, String format, Object... args) {
        return Log.wtf(tag, String.format(format, args), tr);
    }

    public static int i(String tag, String format, Object... args) {
        return Log.i(tag, String.format(format, args));
    }

    public static int d(String tag, String format, Object... args) {
        return Log.d(tag, String.format(format, args));
    }

    public static int w(String tag, String format, Object... args) {
        return Log.w(tag, String.format(format, args));
    }

    public static int w(String tag, Throwable tr, String format, Object... args) {
        return Log.w(tag, String.format(format, args), tr);
    }

    public static int e(String tag, String format, Object... args) {
        return Log.e(tag, String.format(format, args));
    }

    public static int e(String tag, Throwable tr, String format, Object... args) {
        return Log.e(tag, String.format(format, args), tr);
    }
}
