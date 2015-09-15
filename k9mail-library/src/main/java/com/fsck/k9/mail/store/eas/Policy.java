package com.fsck.k9.mail.store.eas;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class Policy {
    public static final char POLICY_STRING_DELIMITER = '\1';

    public static final int PASSWORD_MODE_NONE = 0;
    public static final int PASSWORD_MODE_SIMPLE = 1;
    public static final int PASSWORD_MODE_STRONG = 2;


    public String mProtocolPoliciesUnsupported;
    public boolean mDontAllowAttachments;
    public boolean mRequireManualSyncWhenRoaming;
    public String mProtocolPoliciesEnforced;
    public int mPasswordMode;
    public int mPasswordMinLength;
    public int mMaxScreenLockTime;
    public int mPasswordMaxFails;
    public int mPasswordExpirationDays;
    public int mPasswordHistory;
    public boolean mDontAllowCamera;
    public boolean mDontAllowHtml;
    public boolean mRequireEncryption;
    public boolean mPasswordRecoveryEnabled;
    public int mMaxAttachmentSize;
    public int mPasswordComplexChars;
    public int mMaxCalendarLookback;
    public int mMaxEmailLookback;
    public int mMaxTextTruncationSize;
    public int mMaxHtmlTruncationSize;


    public void normalize() {
        throw new RuntimeException("Not implemented");
    }
}
