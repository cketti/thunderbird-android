package com.fsck.k9.mail.store.eas.adapter;


import java.util.ArrayList;
import java.util.List;

import com.fsck.k9.mail.Address;


//TODO: make this a proper Message subclass
public class MessageData {
    public static final int FLAG_LOADED_UNLOADED = 0;
    public static final int FLAG_LOADED_COMPLETE = 1;
    public static final int FLAG_LOADED_PARTIAL = 2;
    public static final int FLAG_LOADED_DELETED = 3;
    public static final int FLAG_LOADED_UNKNOWN = 4;

    // Bits used in mFlags
    // The following three states are mutually exclusive, and indicate whether the message is an
    // original, a reply, or a forward
    public static final int FLAG_TYPE_REPLY = 1<<0;
    public static final int FLAG_TYPE_FORWARD = 1<<1;
    public static final int FLAG_TYPE_MASK = FLAG_TYPE_REPLY | FLAG_TYPE_FORWARD;
    // The following flags indicate messages that are determined to be incoming meeting related
    // (e.g. invites from others)
    public static final int FLAG_INCOMING_MEETING_INVITE = 1<<2;
    public static final int FLAG_INCOMING_MEETING_CANCEL = 1<<3;
    public static final int FLAG_INCOMING_MEETING_MASK =
            FLAG_INCOMING_MEETING_INVITE | FLAG_INCOMING_MEETING_CANCEL;
    // The following flags indicate messages that are outgoing and meeting related
    // (e.g. invites TO others)
    public static final int FLAG_OUTGOING_MEETING_INVITE = 1<<4;
    public static final int FLAG_OUTGOING_MEETING_CANCEL = 1<<5;
    public static final int FLAG_OUTGOING_MEETING_ACCEPT = 1<<6;
    public static final int FLAG_OUTGOING_MEETING_DECLINE = 1<<7;
    public static final int FLAG_OUTGOING_MEETING_TENTATIVE = 1<<8;
    public static final int FLAG_OUTGOING_MEETING_MASK =
            FLAG_OUTGOING_MEETING_INVITE | FLAG_OUTGOING_MEETING_CANCEL |
                    FLAG_OUTGOING_MEETING_ACCEPT | FLAG_OUTGOING_MEETING_DECLINE |
                    FLAG_OUTGOING_MEETING_TENTATIVE;
    public static final int FLAG_OUTGOING_MEETING_REQUEST_MASK =
            FLAG_OUTGOING_MEETING_INVITE | FLAG_OUTGOING_MEETING_CANCEL;
    // 8 general purpose flags (bits) that may be used at the discretion of the sync adapter
    public static final int FLAG_SYNC_ADAPTER_SHIFT = 9;
    public static final int FLAG_SYNC_ADAPTER_MASK = 255 << FLAG_SYNC_ADAPTER_SHIFT;
    /** If set, the outgoing message should *not* include the quoted original message. */
    public static final int FLAG_NOT_INCLUDE_QUOTED_TEXT = 1 << 17;
    public static final int FLAG_REPLIED_TO = 1 << 18;
    public static final int FLAG_FORWARDED = 1 << 19;

    // Outgoing, original message
    public static final int FLAG_TYPE_ORIGINAL = 1 << 20;
    // Outgoing, reply all message; note, FLAG_TYPE_REPLY should also be set for backward
    // compatibility
    public static final int FLAG_TYPE_REPLY_ALL = 1 << 21;


    private String serverId;
    private Address[] to;
    private Address[] from;
    private Address[] cc;
    private Address[] replyTo;
    private long timeStamp;
    private String subject;
    private boolean flagRead;
    private boolean flagFavorite;
    private String body;
    private int flagLoaded;
    private int flags;
    private String threadTopic;
    private String serverConversationId;
    private List<AttachmentData> attachments;
    private String html;
    private String text;


    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public Address[] getTo() {
        return to;
    }

    public void setTo(Address[] to) {
        this.to = to;
    }

    public void setFrom(Address[] from) {
        this.from = from;
    }

    public Address[] getFrom() {
        return from;
    }

    public void setCc(Address[] cc) {
        this.cc = cc;
    }

    public Address[] getCc() {
        return cc;
    }

    public void setReplyTo(Address[] replyTo) {
        this.replyTo = replyTo;
    }

    public Address[] getReplyTo() {
        return replyTo;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    public void setFlagRead(boolean flagRead) {
        this.flagRead = flagRead;
    }

    public boolean isFlagRead() {
        return flagRead;
    }

    public void setFlagFavorite(boolean flagFavorite) {
        this.flagFavorite = flagFavorite;
    }

    public boolean getFlagFavorite() {
        return flagFavorite;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public void setFlagLoaded(int flagLoaded) {
        this.flagLoaded = flagLoaded;
    }

    public int getFlagLoaded() {
        return flagLoaded;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public void addFlags(int flags) {
        this.flags |= flags;
    }

    public void setThreadTopic(String threadTopic) {
        this.threadTopic = threadTopic;
    }

    public String getThreadTopic() {
        return threadTopic;
    }

    public void setServerConversationId(String serverConversationId) {
        this.serverConversationId = serverConversationId;
    }

    public String getServerConversationId() {
        return serverConversationId;
    }

    public void setAttachments(List<AttachmentData> attachments) {
        this.attachments = attachments;
    }

    public List<AttachmentData> getAttachments() {
        return attachments;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getHtml() {
        return html;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
