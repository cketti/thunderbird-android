package com.fsck.k9.backend.eas


import com.fsck.k9.mail.Message
import com.fsck.k9.mail.data.Body
import com.fsck.k9.mail.data.Header
import com.fsck.k9.mail.data.HeaderField
import java.io.OutputStream
import com.fsck.k9.mail.data.Message as DataMessage


class WrappedMailMessage(private val message: Message) : DataMessage {
    private var length: Long? = null
    private var header: Header? = null

    override fun length(): Long {
        return length ?: message.calculateSize().also { length = it }
    }

    override fun header(): Header {
        return header ?: WrappedHeader(message).also { header = it }
    }

    override fun body(): Body {
        throw UnsupportedOperationException("not implemented")
    }

    override fun writeTo(outputStream: OutputStream) {
        message.writeTo(outputStream)
    }


    class WrappedHeader(private val message: Message) : Header {
        override fun size(): Int {
            throw UnsupportedOperationException("not implemented")
        }

        override fun fields(): MutableList<out HeaderField> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun value(name: String): String? {
            return message.getHeader(name).firstOrNull()
        }

        override fun values(name: String?): MutableList<String> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun writeTo(outputStream: OutputStream?) {
            throw UnsupportedOperationException("not implemented")
        }

    }
}
