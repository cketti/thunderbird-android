package com.fsck.k9.deviceadmin


import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent


class PolicyAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        //FIXME: Extract string resource
        return "WARNING: Deactivating this app's authority to administer your device will delete the managed email accounts from the device."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        //TODO: Remove EAS accounts. Display notification to let user know?
    }
}
