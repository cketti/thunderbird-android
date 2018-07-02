package com.fsck.k9.appconfig

import android.content.res.Resources
import com.fsck.k9.R
import com.fsck.k9.autocrypt.AutocryptStringProvider

class K9AutocryptStringProvider(private val resources: Resources) : AutocryptStringProvider {
    override val subjectText: String
        get() = resources.getString(R.string.ac_transfer_msg_subject)

    override val messageText: String
        get() = resources.getString(R.string.ac_transfer_msg_body)
}
