package com.fsck.k9.protocol.eas;


import java.util.HashSet;
import java.util.Set;

import android.app.admin.DevicePolicyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


/**
 * Stub class to get a minimal version of the imported EAS code running.
 */
public class Policy {
    public static final int PASSWORD_MODE_NONE = 0;
    public static final int PASSWORD_MODE_SIMPLE = 1;
    public static final int PASSWORD_MODE_STRONG = 2;

    /* Convert days to mSec (used for password expiration) */
    private static final long DAYS_TO_MSEC = 24 * 60 * 60 * 1000;
    /* Small offset (2 minutes) added to policy expiration to make user testing easier. */
    private static final long EXPIRATION_OFFSET_MSEC = 2 * 60 * 1000;


    public Set<Unsupported> protocolPoliciesUnsupported = new HashSet<>();
    public boolean doNotAllowAttachments;
    public boolean requireManualSyncWhenRoaming;
    public int passwordMode;
    public int passwordMinLength;
    public int maxScreenLockTime;
    public int passwordMaxFails;
    public int passwordExpirationDays;
    public int passwordHistory;
    public boolean doNotAllowCamera;
    public boolean doNotAllowHtml;
    public boolean requireEncryption;
    public boolean passwordRecoveryEnabled;
    public int maxAttachmentSize;
    public int passwordComplexChars;
    public int maxCalendarLookback;
    public int maxEmailLookback;
    public int maxTextTruncationSize;
    public int maxHtmlTruncationSize;


    /**
     * Normalize the Policy.  If the password mode is "none", zero out all password-related fields;
     * zero out complex characters for simple passwords.
     */
    public void normalize() {
        if (passwordMode == PASSWORD_MODE_NONE) {
            passwordMaxFails = 0;
            maxScreenLockTime = 0;
            passwordMinLength = 0;
            passwordComplexChars = 0;
            passwordHistory = 0;
            passwordExpirationDays = 0;
        } else {
            if ((passwordMode != PASSWORD_MODE_SIMPLE) &&
                    (passwordMode != PASSWORD_MODE_STRONG)) {
                throw new IllegalArgumentException("password mode");
            }
            // If we're only requiring a simple password, set complex chars to zero; note
            // that EAS can erroneously send non-zero values in this case
            if (passwordMode == PASSWORD_MODE_SIMPLE) {
                passwordComplexChars = 0;
            }
        }
    }

    public int getDPManagerPasswordQuality() {
        switch (passwordMode) {
            case PASSWORD_MODE_SIMPLE:
                return DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
            case PASSWORD_MODE_STRONG:
                if (passwordComplexChars == 0) {
                    return DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
                } else {
                    return DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
                }
            default:
                return DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        }
    }

    public long getDPManagerPasswordExpirationTimeout() {
        long result = passwordExpirationDays * DAYS_TO_MSEC;
        // Add a small offset to the password expiration.  This makes it easier to test
        // by changing (for example) 1 day to 1 day + 5 minutes.  If you set an expiration
        // that is within the warning period, you should get a warning fairly quickly.
        if (result > 0) {
            result += EXPIRATION_OFFSET_MSEC;
        }
        return result;
    }

    public JSONObject toJsonObject() {
        try {
            JSONObject policyJson = new JSONObject();
            policyJson.put("doNotAllowAttachments", doNotAllowAttachments);
            policyJson.put("requireManualSyncWhenRoaming", requireManualSyncWhenRoaming);
            policyJson.put("passwordMode", passwordMode);
            policyJson.put("passwordMinLength", passwordMinLength);
            policyJson.put("maxScreenLockTime", maxScreenLockTime);
            policyJson.put("passwordMaxFails", passwordMaxFails);
            policyJson.put("passwordExpirationDays", passwordExpirationDays);
            policyJson.put("passwordHistory", passwordHistory);
            policyJson.put("doNotAllowCamera", doNotAllowCamera);
            policyJson.put("doNotAllowHtml", doNotAllowHtml);
            policyJson.put("requireEncryption", requireEncryption);
            policyJson.put("passwordRecoveryEnabled", passwordRecoveryEnabled);
            policyJson.put("maxAttachmentSize", maxAttachmentSize);
            policyJson.put("passwordComplexChars", passwordComplexChars);
            policyJson.put("maxCalendarLookback", maxCalendarLookback);
            policyJson.put("maxEmailLookback", maxEmailLookback);
            policyJson.put("maxTextTruncationSize", maxTextTruncationSize);
            policyJson.put("maxHtmlTruncationSize", maxHtmlTruncationSize);

            JSONArray unsupportedPolicies = new JSONArray();
            for (Unsupported unsupported : protocolPoliciesUnsupported) {
                unsupportedPolicies.put(unsupported.name());
            }
            policyJson.put("protocolPoliciesUnsupported", unsupportedPolicies);

            return policyJson;
        } catch (JSONException e) {
            //FIXME CrashAnalytics.getInstance().logException(e);
            return new JSONObject();
        }
    }


    public enum Unsupported {
        ALLOW_STORAGE_CARD,
        ALLOW_UNSIGNED_APPLICATIONS,
        ALLOW_UNSIGNED_INSTALLATION_PACKAGES,
        ALLOW_WIFI,
        ALLOW_TEXT_MESSAGING,
        ALLOW_POP_IMAP_EMAIL,
        ALLOW_IRDA,
        ALLOW_HTML_EMAIL,
        ALLOW_BROWSER,
        ALLOW_CONSUMER_EMAIL,
        ALLOW_INTERNET_SHARING,
        ALLOW_BLUETOOTH,
        REQUIRE_DEVICE_ENCRYPTION,
        REQUIRE_SDCARD_ENCRYPTION,
        REQUIRE_MANUAL_SYNC_WHEN_ROAMING,
        REQUIRE_SMIME_SUPPORT,
        UNAPPROVED_IN_ROM_APPLICATION_LIST,
        APPROVED_APPLICATION_LIST,
        MAX_EMAIL_BODY_TRUNCATION_SIZE,
        MAX_EMAIL_HTML_BODY_TRUNCATION_SIZE
    }
}
