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


    /**
     * Normalize the Policy.  If the password mode is "none", zero out all password-related fields;
     * zero out complex characters for simple passwords.
     */
    public void normalize() {
        if (mPasswordMode == PASSWORD_MODE_NONE) {
            mPasswordMaxFails = 0;
            mMaxScreenLockTime = 0;
            mPasswordMinLength = 0;
            mPasswordComplexChars = 0;
            mPasswordHistory = 0;
            mPasswordExpirationDays = 0;
        } else {
            if ((mPasswordMode != PASSWORD_MODE_SIMPLE) &&
                    (mPasswordMode != PASSWORD_MODE_STRONG)) {
                throw new IllegalArgumentException("password mode");
            }
            // If we're only requiring a simple password, set complex chars to zero; note
            // that EAS can erroneously send non-zero values in this case
            if (mPasswordMode == PASSWORD_MODE_SIMPLE) {
                mPasswordComplexChars = 0;
            }
        }
    }
}
