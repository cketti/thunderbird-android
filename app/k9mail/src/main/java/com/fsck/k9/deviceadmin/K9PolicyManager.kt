package com.fsck.k9.deviceadmin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.fsck.k9.protocol.eas.Policy
import com.fsck.k9.protocol.eas.PolicyManager

//TODO: Make this work with multiple accounts
class K9PolicyManager(private val context: Context) : PolicyManager {

    override fun setAccountPolicy(policy: Policy) {
        val sharedPreferences = getPolicyPreferences()
        sharedPreferences.edit()
                .putBoolean(ENFORCE_POLICY, true)
                .putLong(POLICY_CREATION_TIMESTAMP, System.currentTimeMillis())
                .putBoolean(DO_NOT_ALLOW_ATTACHMENTS, policy.doNotAllowAttachments)
                .putBoolean(REQUIRE_MANUAL_SYNC_WHEN_ROAMING, policy.requireManualSyncWhenRoaming)
                .putInt(PASSWORD_MODE, policy.passwordMode)
                .putInt(PASSWORD_MINIMUM_LENGTH, policy.passwordMinLength)
                .putInt(MAX_SCREEN_LOCK_TIME, policy.maxScreenLockTime)
                .putInt(PASSWORD_MAX_FAILS, policy.passwordMaxFails)
                .putInt(PASSWORD_EXPIRATION_DAYS, policy.passwordExpirationDays)
                .putInt(PASSWORD_HISTORY, policy.passwordHistory)
                .putBoolean(DO_NOT_ALLOW_CAMERA, policy.doNotAllowCamera)
                .putBoolean(DO_NOT_ALLOW_HTML, policy.doNotAllowHtml)
                .putBoolean(REQUIRE_ENCRYPTION, policy.requireEncryption)
                .putBoolean(PASSWORD_RECOVERY_ENABLED, policy.passwordRecoveryEnabled)
                .putInt(MAX_ATTACHMENT_SIZE, policy.maxAttachmentSize)
                .putInt(PASSWORD_COMPLEX_CHARS, policy.passwordComplexChars)
                .putInt(MAX_CALENDAR_LOOKBACK, policy.maxCalendarLookback)
                .putInt(MAX_EMAIL_LOOKBACK, policy.maxEmailLookback)
                .putInt(MAX_TEXT_TRUNCATION_SIZE, policy.maxTextTruncationSize)
                .putInt(MAX_HTML_TRUNCATION_SIZE, policy.maxHtmlTruncationSize)
                .apply()
    }

    override fun getMaxSyncWindow(): Int {
        val policy = getSavedPolicy()
        return policy.maxEmailLookback
    }

    fun getAccountPolicyCreationTimestamp(): Long {
        val sharedPreferences = getPolicyPreferences()
        return sharedPreferences.getLong(POLICY_CREATION_TIMESTAMP, 0L)
    }

    fun getSavedPolicy(): Policy {
        val sharedPreferences = getPolicyPreferences()
        val policy = Policy()

        policy.doNotAllowAttachments = sharedPreferences.getBoolean(DO_NOT_ALLOW_ATTACHMENTS, false)
        policy.requireManualSyncWhenRoaming = sharedPreferences.getBoolean(REQUIRE_MANUAL_SYNC_WHEN_ROAMING, false)
        policy.passwordMode = sharedPreferences.getInt(PASSWORD_MODE, 0)
        policy.passwordMinLength = sharedPreferences.getInt(PASSWORD_MINIMUM_LENGTH, 0)
        policy.maxScreenLockTime = sharedPreferences.getInt(MAX_SCREEN_LOCK_TIME, 0)
        policy.passwordMaxFails = sharedPreferences.getInt(PASSWORD_MAX_FAILS, 0)
        policy.passwordExpirationDays = sharedPreferences.getInt(PASSWORD_EXPIRATION_DAYS, 0)
        policy.passwordHistory = sharedPreferences.getInt(PASSWORD_HISTORY, 0)
        policy.doNotAllowCamera = sharedPreferences.getBoolean(DO_NOT_ALLOW_CAMERA, false)
        policy.doNotAllowHtml = sharedPreferences.getBoolean(DO_NOT_ALLOW_HTML, false)
        policy.requireEncryption = sharedPreferences.getBoolean(REQUIRE_ENCRYPTION, false)
        policy.passwordRecoveryEnabled = sharedPreferences.getBoolean(PASSWORD_RECOVERY_ENABLED, false)
        policy.maxAttachmentSize = sharedPreferences.getInt(MAX_ATTACHMENT_SIZE, 0)
        policy.passwordComplexChars = sharedPreferences.getInt(PASSWORD_COMPLEX_CHARS, 0)
        policy.maxCalendarLookback = sharedPreferences.getInt(MAX_CALENDAR_LOOKBACK, 0)
        policy.maxEmailLookback = sharedPreferences.getInt(MAX_EMAIL_LOOKBACK, 0)
        policy.maxTextTruncationSize = sharedPreferences.getInt(MAX_TEXT_TRUNCATION_SIZE, 0)
        policy.maxHtmlTruncationSize = sharedPreferences.getInt(MAX_HTML_TRUNCATION_SIZE, 0)

        return policy
    }

    fun removeSavedPolicy() {
        val sharedPreferences = getPolicyPreferences()
        sharedPreferences.edit().clear().apply()
    }

    fun needToEnforcePolicy(): Boolean {
        val sharedPreferences = getPolicyPreferences()

        val enforcePolicy = sharedPreferences.getBoolean(ENFORCE_POLICY, false)
        if (!enforcePolicy) {
            return false
        }

        val policy = getSavedPolicy()
        return policy.passwordMode != Policy.PASSWORD_MODE_NONE || policy.requireEncryption
    }

    /**
     * @return `true` if the active password doesn't satisfy the policy
     */
    fun enforcePolicy() {
        val policy = getSavedPolicy()

        val devicePolicyManager = getDevicePolicyManager()

        val componentName = getDeviceAdminComponentName()
        devicePolicyManager.setPasswordQuality(componentName, policy.dpManagerPasswordQuality)
        devicePolicyManager.setPasswordMinimumLength(componentName, policy.passwordMinLength)
        devicePolicyManager.setMaximumTimeToLock(componentName, (policy.maxScreenLockTime * 1000).toLong())
        devicePolicyManager.setMaximumFailedPasswordsForWipe(componentName, policy.passwordMaxFails)
        devicePolicyManager.setPasswordExpirationTimeout(componentName, policy.dpManagerPasswordExpirationTimeout)
        devicePolicyManager.setPasswordHistoryLength(componentName, policy.passwordHistory)
        devicePolicyManager.setPasswordMinimumSymbols(componentName, 0)
        devicePolicyManager.setPasswordMinimumNumeric(componentName, 0)
        devicePolicyManager.setPasswordMinimumNonLetter(componentName, policy.passwordComplexChars)
        devicePolicyManager.setCameraDisabled(componentName, policy.doNotAllowCamera)
        devicePolicyManager.setStorageEncryption(componentName, policy.requireEncryption)
    }

    private fun getPolicyPreferences(): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun isActivePasswordSufficient(): Boolean {
        val devicePolicyManager = getDevicePolicyManager()

        return devicePolicyManager.isActivePasswordSufficient
    }

    fun doesDeviceNeedEncrypting(): Boolean {
        val policy = getSavedPolicy()
        if (!policy.requireEncryption) {
            return false
        }

        val devicePolicyManager = getDevicePolicyManager()
        val encryptionStatus = devicePolicyManager.storageEncryptionStatus
        return encryptionStatus != DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
    }

    private fun getDeviceAdminComponentName(): ComponentName {
        return ComponentName(context, PolicyAdmin::class.java)
    }

    private fun getDevicePolicyManager(): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    fun isDeviceEncryptionRequired(): Boolean {
        val policy = getSavedPolicy()
        return policy.requireEncryption
    }

    fun isPasswordRequired(): Boolean {
        val policy = getSavedPolicy()
        return policy.passwordMode != Policy.PASSWORD_MODE_NONE
    }

    fun isPolicySatisfied(): Boolean {
        if (!needToEnforcePolicy()) {
            return true
        }

        return if (!isDeviceAdminInstalled()) {
            false
        } else isActivePasswordSufficient() && !doesDeviceNeedEncrypting()

    }

    fun isDeviceAdminInstalled(): Boolean {
        val devicePolicyManager = getDevicePolicyManager()
        val componentName = getDeviceAdminComponentName()
        return devicePolicyManager.isAdminActive(componentName)
    }

    fun enforcePolicyIfPossible() {
        if (isDeviceAdminInstalled()) {
            enforcePolicy()
        }
    }

    fun removeDeviceAdmin() {
        val devicePolicyManager = getDevicePolicyManager()
        val componentName = getDeviceAdminComponentName()
        devicePolicyManager.removeActiveAdmin(componentName)

        removeSavedPolicy()
    }


    companion object {
        private const val PREFERENCES_NAME = "eas-policy.xml"
        private const val ENFORCE_POLICY = "enforcePolicy"
        private const val POLICY_CREATION_TIMESTAMP = "policyCreationTimestamp"
        private const val DO_NOT_ALLOW_ATTACHMENTS = "doNotAllowAttachments"
        private const val REQUIRE_MANUAL_SYNC_WHEN_ROAMING = "requireManualSyncWhenRoaming"
        private const val PASSWORD_MODE = "passwordMode"
        private const val PASSWORD_MINIMUM_LENGTH = "passwordMinimumLength"
        private const val MAX_SCREEN_LOCK_TIME = "maxScreenLockTime"
        private const val PASSWORD_MAX_FAILS = "passwordMaxFails"
        private const val PASSWORD_EXPIRATION_DAYS = "passwordExpirationDays"
        private const val PASSWORD_HISTORY = "passwordHistory"
        private const val DO_NOT_ALLOW_CAMERA = "doNotAllowCamera"
        private const val DO_NOT_ALLOW_HTML = "doNotAllowHtml"
        private const val REQUIRE_ENCRYPTION = "requireEncryption"
        private const val PASSWORD_RECOVERY_ENABLED = "passwordRecoveryEnabled"
        private const val MAX_ATTACHMENT_SIZE = "maxAttachmentSize"
        private const val PASSWORD_COMPLEX_CHARS = "passwordComplexChars"
        private const val MAX_CALENDAR_LOOKBACK = "maxCalendarLookback"
        private const val MAX_EMAIL_LOOKBACK = "maxEmaiLookback"
        private const val MAX_TEXT_TRUNCATION_SIZE = "maxTextTruncationSize"
        private const val MAX_HTML_TRUNCATION_SIZE = "maxHtmlTruncationSize"
    }
}
