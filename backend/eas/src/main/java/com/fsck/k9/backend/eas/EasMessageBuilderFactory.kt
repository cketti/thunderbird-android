package com.fsck.k9.backend.eas

import android.content.Context
import com.fsck.k9.mail.helper.FileFactory
import com.fsck.k9.mail.message.MessageBuilderFactory
import com.fsck.k9.mail.message.basic.FileBackedMessageBuilderFactory
import java.io.File

class EasMessageBuilderFactory(private val context: Context)
    : MessageBuilderFactory by FileBackedMessageBuilderFactory(createFileFactory(context)) {

    companion object {
        private fun createFileFactory(context: Context): FileFactory {
            //TODO: add account identifier
            return FileFactory { File.createTempFile("eas-body", null, context.cacheDir) }
        }
    }
}
