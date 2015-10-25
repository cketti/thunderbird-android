package com.fsck.k9.controller.legacy;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Body;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.data.Message;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.field.DateTimeField;
import org.apache.james.mime4j.field.DefaultFieldParser;

import static com.fsck.k9.controller.legacy.LegacyPart.unfold;


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
    public void setSubject(String subject) throws MessagingException {
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
    public void setSentDate(Date sentDate, boolean hideTimeZone) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Address[] getRecipients(RecipientType type) throws MessagingException {
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
            throw new MessagingException("Unrecognized recipient type.");
        }
    }

    @Override
    public void setRecipients(RecipientType type, Address[] addresses) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Address[] getFrom() {
        if (from == null) {
            from = Address.parse(unfold(legacyPart.header("From")));
        }
        return from;
    }

    @Override
    public void setFrom(Address from) throws MessagingException {
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
    public void setReplyTo(Address[] from) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getMessageId() throws MessagingException {
        return legacyPart.header("Message-Id");
    }

    @Override
    public void setInReplyTo(String inReplyTo) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String[] getReferences() throws MessagingException {
        if (references == null) {
            references = legacyPart.getHeader("References");
        }
        return references;
    }

    @Override
    public void setReferences(String references) throws MessagingException {
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
    public String getDisposition() throws MessagingException {
        return legacyPart.getDisposition();
    }

    @Override
    public String getContentId() {
        return null;
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        legacyPart.addHeader(name, value);
    }

    @Override
    public void addRawHeader(String name, String raw) throws MessagingException {
        legacyPart.addRawHeader(name, raw);
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        legacyPart.setHeader(name, value);
    }

    @Override
    public String[] getHeader(String name) throws MessagingException {
        return legacyPart.getHeader(name);
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return legacyPart.isMimeType(mimeType);
    }

    @Override
    public String getMimeType() {
        return legacyPart.getMimeType();
    }

    @Override
    public Set<String> getHeaderNames() throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        legacyPart.removeHeader(name);
    }

    @Override
    public void setBody(Body body) {
        legacyPart.setBody(body);
    }

    @Override
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        legacyPart.writeTo(out);
    }

    @Override
    public void writeHeaderTo(OutputStream out) throws IOException, MessagingException {
        legacyPart.writeHeaderTo(out);
    }

    @Override
    public void setUsing7bitTransport() throws MessagingException {
        legacyPart.setUsing7bitTransport();
    }

    @Override
    public String getServerExtra() {
        return legacyPart.getServerExtra();
    }

    @Override
    public void setServerExtra(String serverExtra) {
        legacyPart.setServerExtra(serverExtra);
    }

    @Override
    public long getId() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getPreview() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean hasAttachments() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int getSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public InputStream getInputStream() throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setEncoding(String encoding) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setCharset(String charset) throws MessagingException {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public com.fsck.k9.mail.Message clone() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
