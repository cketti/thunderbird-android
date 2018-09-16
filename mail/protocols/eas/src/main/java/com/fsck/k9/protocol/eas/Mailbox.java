package com.fsck.k9.protocol.eas;


public class Mailbox {
    // Types of mailboxes.  The list is ordered to match a typical UI presentation, e.g.
    // placing the inbox at the top.
    // Arrays of "special_mailbox_display_names" and "special_mailbox_icons" are depends on
    // types Id of mailboxes.
    /** No type specified */
    public static final int TYPE_NONE = -1;
    /** The "main" mailbox for the account, almost always referred to as "Inbox" */
    public static final int TYPE_INBOX = 0;
    // Types of mailboxes
    /** Generic mailbox that holds mail */
    public static final int TYPE_MAIL = 1;
    /** Parent-only mailbox; does not hold any mail */
    public static final int TYPE_PARENT = 2;
    /** Drafts mailbox */
    public static final int TYPE_DRAFTS = 3;
    /** Local mailbox associated with the account's outgoing mail */
    public static final int TYPE_OUTBOX = 4;
    /** Sent mail; mail that was sent from the account */
    public static final int TYPE_SENT = 5;
    /** Deleted mail */
    public static final int TYPE_TRASH = 6;
    /** Junk mail */
    public static final int TYPE_JUNK = 7;
    /** Search results */
    public static final int TYPE_SEARCH = 8;
    /** Starred (virtual) */
    public static final int TYPE_STARRED = 9;
    /** All unread mail (virtual) */
    public static final int TYPE_UNREAD = 10;

    // Types after this are used for non-mail mailboxes (as in EAS)
    public static final int TYPE_NOT_EMAIL = 0x40;
    public static final int TYPE_CALENDAR = 0x41;
    public static final int TYPE_CONTACTS = 0x42;
    public static final int TYPE_TASKS = 0x43;
    @Deprecated
    public static final int TYPE_EAS_ACCOUNT_MAILBOX = 0x44;
    public static final int TYPE_UNKNOWN = 0x45;


    public String mSyncKey = "0";
    public int mType = TYPE_MAIL;
    public long mId = -1;
    public String mServerId;
    public String syncWindow;


    public static boolean isInitialSyncKey(final String syncKey) {
        return syncKey == null || syncKey.isEmpty() || syncKey.equals("0");
    }
}
