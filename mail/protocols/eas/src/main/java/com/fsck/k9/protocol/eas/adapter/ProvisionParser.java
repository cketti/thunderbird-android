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


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Environment;

import androidx.core.content.ContextCompat;
import com.fsck.k9.protocol.eas.Eas;
import com.fsck.k9.protocol.eas.EasProvision;
import com.fsck.k9.protocol.eas.LogUtils;
import com.fsck.k9.protocol.eas.Policy;
import com.fsck.k9.protocol.eas.Policy.Unsupported;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;


/**
 * Parse the result of the Provision command
 */
public class ProvisionParser extends Parser {
    private static final String TAG = Eas.LOG_TAG;

    private final Context mContext;
    private Policy mPolicy = null;
    private String mSecuritySyncKey = null;
    private boolean mRemoteWipe = false;
    private boolean mIsSupportable = true;
    private boolean smimeRequired = false;

    public ProvisionParser(final Context context, final InputStream in) throws IOException {
        super(in);
        mContext = context;
    }

    public Policy getPolicy() {
        return mPolicy;
    }

    public String getSecuritySyncKey() {
        return mSecuritySyncKey;
    }

    public void setSecuritySyncKey(String securitySyncKey) {
        mSecuritySyncKey = securitySyncKey;
    }

    public boolean getRemoteWipe() {
        return mRemoteWipe;
    }

    public boolean hasSupportablePolicySet() {
        return (mPolicy != null) && mIsSupportable &&
                !mPolicy.doNotAllowAttachments;
    }

    public void clearUnsupportablePolicies() {
        mIsSupportable = true;
        mPolicy.protocolPoliciesUnsupported = null;
    }

    private void setPolicy(Policy policy) {
        policy.normalize();
        mPolicy = policy;
    }

    private boolean deviceSupportsEncryption() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        int status = dpm.getStorageEncryptionStatus();
        return status != DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
    }

    private void parseProvisionDocWbxml() throws IOException {
        Policy policy = new Policy();
        boolean passwordEnabled = false;

        while (nextTag(Tags.PROVISION_EAS_PROVISION_DOC) != END) {
            boolean tagIsSupported = true;

            switch (tag) {
                case Tags.PROVISION_DEVICE_PASSWORD_ENABLED:
                    if (getValueInt() == 1) {
                        passwordEnabled = true;
                        if (policy.passwordMode == Policy.PASSWORD_MODE_NONE) {
                            policy.passwordMode = Policy.PASSWORD_MODE_SIMPLE;
                        }
                    }
                    break;
                case Tags.PROVISION_MIN_DEVICE_PASSWORD_LENGTH:
                    policy.passwordMinLength = getValueInt();
                    break;
                case Tags.PROVISION_ALPHA_DEVICE_PASSWORD_ENABLED:
                    if (getValueInt() == 1) {
                        policy.passwordMode = Policy.PASSWORD_MODE_STRONG;
                    }
                    break;
                case Tags.PROVISION_MAX_INACTIVITY_TIME_DEVICE_LOCK:
                    // EAS gives us seconds, which is, happily, what the PolicySet requires
                    policy.maxScreenLockTime = getValueInt();
                    break;
                case Tags.PROVISION_MAX_DEVICE_PASSWORD_FAILED_ATTEMPTS:
                    policy.passwordMaxFails = getValueInt();
                    break;
                case Tags.PROVISION_DEVICE_PASSWORD_EXPIRATION:
                    policy.passwordExpirationDays = getValueInt();
                    break;
                case Tags.PROVISION_DEVICE_PASSWORD_HISTORY:
                    policy.passwordHistory = getValueInt();
                    break;
                case Tags.PROVISION_ALLOW_CAMERA:
                    // We need to check to see if it's possible to disable the camera. It's not
                    // possible on devices with managed profiles.
                    policy.doNotAllowCamera = (getValueInt() == 0);
                    break;
                case Tags.PROVISION_ALLOW_SIMPLE_DEVICE_PASSWORD:
                    // Ignore this unless there's any MSFT documentation for what this means
                    // Hint: I haven't seen any that's more specific than "simple"
                    getValue();
                    break;
                // The following policies, if false, can't be supported at the moment
                case Tags.PROVISION_ALLOW_STORAGE_CARD:
                case Tags.PROVISION_ALLOW_UNSIGNED_APPLICATIONS:
                case Tags.PROVISION_ALLOW_UNSIGNED_INSTALLATION_PACKAGES:
                case Tags.PROVISION_ALLOW_WIFI:
                case Tags.PROVISION_ALLOW_TEXT_MESSAGING:
                case Tags.PROVISION_ALLOW_POP_IMAP_EMAIL:
//                case Tags.PROVISION_ALLOW_IRDA:
                case Tags.PROVISION_ALLOW_HTML_EMAIL:
                case Tags.PROVISION_ALLOW_BROWSER:
                case Tags.PROVISION_ALLOW_CONSUMER_EMAIL:
                case Tags.PROVISION_ALLOW_INTERNET_SHARING:
                    if (getValueInt() == 0) {
                        tagIsSupported = false;
                        Unsupported unsupported = null;
                        switch(tag) {
                            case Tags.PROVISION_ALLOW_STORAGE_CARD:
                                unsupported = Unsupported.ALLOW_STORAGE_CARD;
                                break;
                            case Tags.PROVISION_ALLOW_UNSIGNED_APPLICATIONS:
                                unsupported = Unsupported.ALLOW_UNSIGNED_APPLICATIONS;
                                break;
                            case Tags.PROVISION_ALLOW_UNSIGNED_INSTALLATION_PACKAGES:
                                unsupported = Unsupported.ALLOW_UNSIGNED_INSTALLATION_PACKAGES;
                                break;
                            case Tags.PROVISION_ALLOW_WIFI:
                                unsupported = Unsupported.ALLOW_WIFI;
                                break;
                            case Tags.PROVISION_ALLOW_TEXT_MESSAGING:
                                unsupported = Unsupported.ALLOW_TEXT_MESSAGING;
                                break;
                            case Tags.PROVISION_ALLOW_POP_IMAP_EMAIL:
                                unsupported = Unsupported.ALLOW_POP_IMAP_EMAIL;
                                break;
//                            case Tags.PROVISION_ALLOW_IRDA:
//                                unsupported = Unsupported.ALLOW_IRDA;
//                                break;
                            case Tags.PROVISION_ALLOW_HTML_EMAIL:
                                unsupported = Unsupported.ALLOW_HTML_EMAIL;
                                policy.doNotAllowHtml = true;
                                break;
                            case Tags.PROVISION_ALLOW_BROWSER:
                                unsupported = Unsupported.ALLOW_BROWSER;
                                break;
                            case Tags.PROVISION_ALLOW_CONSUMER_EMAIL:
                                unsupported = Unsupported.ALLOW_CONSUMER_EMAIL;
                                break;
                            case Tags.PROVISION_ALLOW_INTERNET_SHARING:
                                unsupported = Unsupported.ALLOW_INTERNET_SHARING;
                                break;
                        }

                        if (unsupported != null) {
                            policy.protocolPoliciesUnsupported.add(unsupported);
                        }
                    }
                    break;
                case Tags.PROVISION_ATTACHMENTS_ENABLED:
                    policy.doNotAllowAttachments = getValueInt() != 1;
                    break;
                // Bluetooth: 0 = no bluetooth; 1 = only hands-free; 2 = allowed
                case Tags.PROVISION_ALLOW_BLUETOOTH:
                    if (getValueInt() != 2) {
                        tagIsSupported = false;
                        policy.protocolPoliciesUnsupported.add(Unsupported.ALLOW_BLUETOOTH);
                    }
                    break;
                // We may now support device (internal) encryption; we'll check this capability
                // below with the call to SecurityPolicy.isSupported()
                case Tags.PROVISION_REQUIRE_DEVICE_ENCRYPTION:
                    if (getValueInt() == 1) {
                         if (!deviceSupportsEncryption()) {
                            tagIsSupported = false;
                            policy.protocolPoliciesUnsupported.add(Unsupported.REQUIRE_DEVICE_ENCRYPTION);
                        } else {
                            policy.requireEncryption = true;
                        }
                    }
                    break;
                // Note that DEVICE_ENCRYPTION_ENABLED refers to SD card encryption, which the OS
                // does not yet support.
                case Tags.PROVISION_DEVICE_ENCRYPTION_ENABLED:
                    if (getValueInt() == 1) {
                        log("Policy requires SD card encryption");
                        // Let's see if this can be supported on our device...
                        if (deviceSupportsEncryption()) {
                            // NOTE: Private API!
                            // Go through volumes; if ANY are removable, we can't support this
                            // policy.
                            tagIsSupported = !hasRemovableStorage();
                            if (tagIsSupported) {
                                // If this policy is requested, we MUST also require encryption
                                log("Device supports SD card encryption");
                                policy.requireEncryption = true;
                                break;
                            }
                        } else {
                            log("Device doesn't support encryption; failing");
                            tagIsSupported = false;
                        }
                        // If we fall through, we can't support the policy
                        policy.protocolPoliciesUnsupported.add(Unsupported.REQUIRE_SDCARD_ENCRYPTION);
                    }
                    break;
                    // Note this policy; we enforce it in EasService
                case Tags.PROVISION_REQUIRE_MANUAL_SYNC_WHEN_ROAMING:
                    policy.requireManualSyncWhenRoaming = getValueInt() == 1;
                    break;
                // We are allowed to accept policies, regardless of value of this tag
                // TODO: When we DO support a recovery password, we need to store the value in
                // the account (so we know to utilize it)
                case Tags.PROVISION_PASSWORD_RECOVERY_ENABLED:
                    // Read, but ignore, value
                    policy.passwordRecoveryEnabled = getValueInt() == 1;
                    break;
                // The following policies, if true, can't be supported at the moment
                case Tags.PROVISION_REQUIRE_SIGNED_SMIME_MESSAGES:
                case Tags.PROVISION_REQUIRE_ENCRYPTED_SMIME_MESSAGES:
                case Tags.PROVISION_REQUIRE_SIGNED_SMIME_ALGORITHM:
                case Tags.PROVISION_REQUIRE_ENCRYPTION_SMIME_ALGORITHM:
                    if (getValueInt() == 1) {
                        tagIsSupported = false;
                        if (!smimeRequired) {
                            policy.protocolPoliciesUnsupported.add(Unsupported.REQUIRE_SMIME_SUPPORT);
                            smimeRequired = true;
                        }
                    }
                    break;
                case Tags.PROVISION_MAX_ATTACHMENT_SIZE:
                    int max = getValueInt();
                    if (max > 0) {
                        policy.maxAttachmentSize = max;
                        tagIsSupported = false; //FIXME
                    }
                    break;
                // Complex characters are supported
                case Tags.PROVISION_MIN_DEVICE_PASSWORD_COMPLEX_CHARS:
                    policy.passwordComplexChars = getValueInt();
                    break;
                // The following policies are moot; they allow functionality that we don't support
                case Tags.PROVISION_ALLOW_DESKTOP_SYNC:
                case Tags.PROVISION_ALLOW_SMIME_ENCRYPTION_NEGOTIATION:
                case Tags.PROVISION_ALLOW_SMIME_SOFT_CERTS:
                case Tags.PROVISION_ALLOW_REMOTE_DESKTOP:
                case Tags.PROVISION_ALLOW_IRDA: //FIXME
                    skipTag();
                    break;
                // We don't handle approved/unapproved application lists
                case Tags.PROVISION_UNAPPROVED_IN_ROM_APPLICATION_LIST:
                case Tags.PROVISION_APPROVED_APPLICATION_LIST:
                    // Parse and throw away the content
                    if (specifiesApplications(tag)) {
                        tagIsSupported = false;
                        if (tag == Tags.PROVISION_UNAPPROVED_IN_ROM_APPLICATION_LIST) {
                            policy.protocolPoliciesUnsupported.add(Unsupported.UNAPPROVED_IN_ROM_APPLICATION_LIST);
                        } else {
                            policy.protocolPoliciesUnsupported.add(Unsupported.APPROVED_APPLICATION_LIST);
                        }
                    }
                    break;
                // We accept calendar age, since we never ask for more than two weeks, and that's
                // the most restrictive policy
                case Tags.PROVISION_MAX_CALENDAR_AGE_FILTER:
                    policy.maxCalendarLookback = getValueInt();
                    break;
                // We handle max email lookback
                case Tags.PROVISION_MAX_EMAIL_AGE_FILTER:
                    policy.maxEmailLookback = getValueInt();
                    break;
                // We currently reject these next two policies
                case Tags.PROVISION_MAX_EMAIL_BODY_TRUNCATION_SIZE:
                case Tags.PROVISION_MAX_EMAIL_HTML_BODY_TRUNCATION_SIZE:
                    String value = getValue();
                    // -1 indicates no required truncation
                    if (!value.equals("-1")) {
                        max = Integer.parseInt(value);
                        if (tag == Tags.PROVISION_MAX_EMAIL_BODY_TRUNCATION_SIZE) {
                            policy.maxTextTruncationSize = max;
                            policy.protocolPoliciesUnsupported.add(Unsupported.MAX_EMAIL_BODY_TRUNCATION_SIZE);
                        } else {
                            policy.maxHtmlTruncationSize = max;
                            policy.protocolPoliciesUnsupported.add(Unsupported.MAX_EMAIL_HTML_BODY_TRUNCATION_SIZE);
                        }
                        tagIsSupported = false;
                    }
                    break;
                default:
                    skipTag();
            }

            if (!tagIsSupported) {
                log("Policy not supported: " + tag);
                mIsSupportable = false;
            }
        }

        // Make sure policy settings are valid; password not enabled trumps other password settings
        if (!passwordEnabled) {
            policy.passwordMode = Policy.PASSWORD_MODE_NONE;
        }

        setPolicy(policy);
    }

    /**
     * Return whether or not either of the application list tags specifies any applications
     * @param endTag the tag whose children we're walking through
     * @return whether any applications were specified (by name or by hash)
     * @throws IOException
     */
    private boolean specifiesApplications(int endTag) throws IOException {
        boolean specifiesApplications = false;
        while (nextTag(endTag) != END) {
            switch (tag) {
                case Tags.PROVISION_APPLICATION_NAME:
                case Tags.PROVISION_HASH:
                    specifiesApplications = true;
                    break;
                default:
                    skipTag();
            }
        }
        return specifiesApplications;
    }

    /*package*/ void parseProvisionDocXml(String doc) throws IOException {
        Policy policy = new Policy();

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new ByteArrayInputStream(doc.getBytes()), "UTF-8");
            int type = parser.getEventType();
            if (type == XmlPullParser.START_DOCUMENT) {
                type = parser.next();
                if (type == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (tagName.equals("wap-provisioningdoc")) {
                        parseWapProvisioningDoc(parser, policy);
                    }
                }
            }
        } catch (XmlPullParserException e) {
           throw new IOException();
        }

        setPolicy(policy);
    }

    /**
     * Return true if password is required; otherwise false.
     */
    private static boolean parseSecurityPolicy(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        boolean passwordRequired = true;
        while (true) {
            int type = parser.nextTag();
            if (type == XmlPullParser.END_TAG && parser.getName().equals("characteristic")) {
                break;
            } else if (type == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if (tagName.equals("parm")) {
                    String name = parser.getAttributeValue(null, "name");
                    if (name.equals("4131")) {
                        String value = parser.getAttributeValue(null, "value");
                        if (value.equals("1")) {
                            passwordRequired = false;
                        }
                    }
                }
            }
        }
        return passwordRequired;
    }

    private static void parseCharacteristic(XmlPullParser parser, Policy policy)
            throws XmlPullParserException, IOException {
        boolean enforceInactivityTimer = true;
        while (true) {
            int type = parser.nextTag();
            if (type == XmlPullParser.END_TAG && parser.getName().equals("characteristic")) {
                break;
            } else if (type == XmlPullParser.START_TAG) {
                if (parser.getName().equals("parm")) {
                    String name = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");
                    if (name.equals("AEFrequencyValue")) {
                        if (enforceInactivityTimer) {
                            if (value.equals("0")) {
                                policy.maxScreenLockTime = 1;
                            } else {
                                policy.maxScreenLockTime = 60* Integer.parseInt(value);
                            }
                        }
                    } else if (name.equals("AEFrequencyType")) {
                        // "0" here means we don't enforce an inactivity timeout
                        if (value.equals("0")) {
                            enforceInactivityTimer = false;
                        }
                    } else if (name.equals("DeviceWipeThreshold")) {
                        policy.passwordMaxFails = Integer.parseInt(value);
                    } else if (name.equals("CodewordFrequency")) {
                        // Ignore; has no meaning for us
                    } else if (name.equals("MinimumPasswordLength")) {
                        policy.passwordMinLength = Integer.parseInt(value);
                    } else if (name.equals("PasswordComplexity")) {
                        if (value.equals("0")) {
                            policy.passwordMode = Policy.PASSWORD_MODE_STRONG;
                        } else {
                            policy.passwordMode = Policy.PASSWORD_MODE_SIMPLE;
                        }
                    }
                }
            }
        }
    }

    private static void parseRegistry(XmlPullParser parser, Policy policy)
            throws XmlPullParserException, IOException {
      while (true) {
          int type = parser.nextTag();
          if (type == XmlPullParser.END_TAG && parser.getName().equals("characteristic")) {
              break;
          } else if (type == XmlPullParser.START_TAG) {
              String name = parser.getName();
              if (name.equals("characteristic")) {
                  parseCharacteristic(parser, policy);
              }
          }
      }
    }

    private static void parseWapProvisioningDoc(XmlPullParser parser, Policy policy)
            throws XmlPullParserException, IOException {
        while (true) {
            int type = parser.nextTag();
            if (type == XmlPullParser.END_TAG && parser.getName().equals("wap-provisioningdoc")) {
                break;
            } else if (type == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (name.equals("characteristic")) {
                    String atype = parser.getAttributeValue(null, "type");
                    if (atype.equals("SecurityPolicy")) {
                        // If a password isn't required, stop here
                        if (!parseSecurityPolicy(parser)) {
                            return;
                        }
                    } else if (atype.equals("Registry")) {
                        parseRegistry(parser, policy);
                        return;
                    }
                }
            }
        }
    }

    private void parseProvisionData() throws IOException {
        while (nextTag(Tags.PROVISION_DATA) != END) {
            if (tag == Tags.PROVISION_EAS_PROVISION_DOC) {
                parseProvisionDocWbxml();
            } else {
                skipTag();
            }
        }
    }

    private void parsePolicy() throws IOException {
        String policyType = null;
        while (nextTag(Tags.PROVISION_POLICY) != END) {
            switch (tag) {
                case Tags.PROVISION_POLICY_TYPE:
                    policyType = getValue();
                    LogUtils.d(TAG, "Policy type: %s", policyType);
                    break;
                case Tags.PROVISION_POLICY_KEY:
                    mSecuritySyncKey = getValue();
                    break;
                case Tags.PROVISION_STATUS:
                    LogUtils.d(TAG, "Policy status: %s", getValue());
                    break;
                case Tags.PROVISION_DATA:
                    if (policyType.equalsIgnoreCase(EasProvision.EAS_2_POLICY_TYPE)) {
                        // Parse the old style XML document
                        parseProvisionDocXml(getValue());
                    } else {
                        // Parse the newer WBXML data
                        parseProvisionData();
                    }
                    break;
                default:
                    skipTag();
            }
        }
    }

    private void parsePolicies() throws IOException {
        while (nextTag(Tags.PROVISION_POLICIES) != END) {
            if (tag == Tags.PROVISION_POLICY) {
                parsePolicy();
            } else {
                skipTag();
            }
        }
    }

    private void parseDeviceInformation() throws IOException {
        while (nextTag(Tags.SETTINGS_DEVICE_INFORMATION) != END) {
            if (tag == Tags.SETTINGS_STATUS) {
                LogUtils.d(TAG, "DeviceInformation status: %s", getValue());
            } else {
                skipTag();
            }
        }
    }

    @Override
    public boolean parse() throws IOException {
        boolean res = false;
        if (nextTag(START_DOCUMENT) != Tags.PROVISION_PROVISION) {
            throw new IOException();
        }
        while (nextTag(START_DOCUMENT) != END_DOCUMENT) {
            switch (tag) {
                case Tags.PROVISION_STATUS:
                    int status = getValueInt();
                    LogUtils.d(TAG, "Provision status: %d", status);
                    res = (status == 1);
                    break;
                case Tags.SETTINGS_DEVICE_INFORMATION:
                    parseDeviceInformation();
                    break;
                case Tags.PROVISION_POLICIES:
                    parsePolicies();
                    break;
                case Tags.PROVISION_REMOTE_WIPE:
                    // Indicate remote wipe command received
                    mRemoteWipe = true;
                    break;
                default:
                    skipTag();
            }
        }
        return res;
    }

    /**
     * In order to determine whether the device has removable storage, we need to use the
     * StorageVolume class, which is hidden (for now) by the framework.  Without this, we'd have
     * to reject all policies that require sd card encryption.
     *
     * TODO: Rewrite this when an appropriate API is available from the framework
     */
    private boolean hasRemovableStorage() {
        final File[] cacheDirs = ContextCompat.getExternalCacheDirs(mContext);
        return Environment.isExternalStorageRemovable()
                || (cacheDirs != null && cacheDirs.length > 1);
    }
}
