package com.fsck.k9.backend.eas.legacy;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.data.Message;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.util.MimeUtil;
import org.jetbrains.annotations.NotNull;


class LegacySimpleMessage extends com.fsck.k9.mail.Message {
    private final LegacyPart legacyPart;
    private String subject;
    private Address[] from;
    private Address[] replyTo;
    private Date sentDate;
    private Address[] to;
    private Address[] cc;
    private Address[] bcc;
    private String[] references;


    LegacySimpleMessage(Message message) {
        legacyPart = new LegacyPart(message);
    }

    @Override
    public String getSubject() {
        if (subject == null) {
            subject = LegacyPart.unfoldAndDecode(legacyPart.header("Subject"));
        }
        return subject;
    }

    @Override
    public void setSubject(String subject) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Date getSentDate() {
        if (sentDate == null) {
            String dateHeader = legacyPart.header("Date");
            if (dateHeader == null) {
                sentDate = new Date();
            } else {
                try {
                    DateTimeField field = (DateTimeField) DefaultFieldParser.parse("Date: " + dateHeader);
                    sentDate = field.getDate();
                } catch (MimeException e) {
                    sentDate = new Date();
                }
            }
        }
        return sentDate;
    }

    @Override
    public void setSentDate(Date sentDate, boolean hideTimeZone) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Address[] getRecipients(RecipientType type) {
        if (type == RecipientType.TO) {
            if (to == null) {
                to = Address.parse(unfold(legacyPart.header("To")));
            }
            return to;
        } else if (type == RecipientType.CC) {
            if (cc == null) {
                cc = Address.parse(unfold(legacyPart.header("CC")));
            }
            return cc;
        } else if (type == RecipientType.BCC) {
            if (bcc == null) {
                bcc = Address.parse(unfold(legacyPart.header("BCC")));
            }
            return bcc;
        } else {
            throw new IllegalArgumentException("Unrecognized recipient type.");
        }
    }

    @Override
    public Address[] getFrom() {
        if (from == null) {
            from = Address.parse(unfold(legacyPart.header("From")));
        }
        return from;
    }

    @Override
    public void setFrom(Address from) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Address[] getSender() {
        return Address.parse(unfold(legacyPart.header("From")));
    }

    @Override
    public void setSender(Address sender) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Address[] getReplyTo() {
        if (replyTo == null) {
            replyTo = Address.parse(unfold(legacyPart.header("Reply-To")));
        }
        return replyTo;
    }

    @Override
    public void setReplyTo(Address[] from) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getMessageId() {
        return legacyPart.header("Message-Id");
    }

    @Override
    public void setInReplyTo(String inReplyTo) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String[] getReferences() {
        if (references == null) {
            references = legacyPart.getHeader("References");
        }
        return references;
    }

    @Override
    public void setReferences(String references) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Body getBody() {
        return legacyPart.getBody();
    }

    @Override
    public String getContentType() {
        return legacyPart.getContentType();
    }

    @Override
    public String getDisposition() {
        return legacyPart.getDisposition();
    }

    @Override
    public String getContentId() {
        return null;
    }

    @Override
    public void addHeader(String name, String value) {
        legacyPart.addHeader(name, value);
    }

    @Override
    public void addRawHeader(String name, String raw) {
        legacyPart.addRawHeader(name, raw);
    }

    @Override
    public void setHeader(String name, String value) {
        legacyPart.setHeader(name, value);
    }

    @NotNull
    @Override
    public String[] getHeader(String name) {
        return legacyPart.getHeader(name);
    }

    @Override
    public boolean isMimeType(String mimeType) {
        return legacyPart.isMimeType(mimeType);
    }

    @Override
    public String getMimeType() {
        return legacyPart.getMimeType();
    }

    @Override
    public Set<String> getHeaderNames() {
        return legacyPart.getHeaderNames();
    }

    @Override
    public void removeHeader(String name) {
        legacyPart.removeHeader(name);
    }

    @Override
    public void setBody(Body body) {
        legacyPart.setBody(body);
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        legacyPart.writeTo(out);
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException {
        legacyPart.writeHeaderTo(out);
    }

    @Override
    public boolean hasAttachments() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public long getSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public InputStream getInputStream() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setEncoding(String encoding) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setCharset(String charset) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getServerExtra() {
        return null;
    }

    @Override
    public void setServerExtra(String serverExtra) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public com.fsck.k9.mail.Message clone() {
        throw new UnsupportedOperationException("Not implemented");
    }


    private static String unfold(String value) {
        if (value == null) {
            return null;
        }

        return MimeUtil.unfold(value);
    }
}
