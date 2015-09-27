package com.fsck.k9.mail.store.eas.adapter;


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.store.eas.CommandStatusException;
import com.fsck.k9.mail.store.eas.Eas;
import com.fsck.k9.mail.store.eas.LogUtils;
import com.fsck.k9.mail.store.eas.Utility;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;


/**
 * Parser for Sync on an email collection.
 */
public class EmailSyncParser extends AbstractSyncParser {
    private static final String TAG = Eas.LOG_TAG;

    static final int LAST_VERB_REPLY = 1;
    static final int LAST_VERB_REPLY_ALL = 2;
    static final int LAST_VERB_FORWARD = 3;

    private final EmailSyncCallback callback;
    private boolean mFetchNeeded = false;
    private final Map<String, Integer> mMessageUpdateStatus = new HashMap();


    public EmailSyncParser(InputStream in, EmailSyncCallback callback)
            throws IOException {
        super(in, callback);
        this.callback = callback;
    }

    public boolean fetchNeeded() {
        return mFetchNeeded;
    }

    public Map<String, Integer> getMessageStatuses() {
        return mMessageUpdateStatus;
    }

    private void addData(MessageData msg, int endingTag) throws IOException {
        ArrayList<AttachmentData> atts = new ArrayList<AttachmentData>();
        boolean truncated = false;

        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.EMAIL_ATTACHMENTS:
                case Tags.BASE_ATTACHMENTS: // BASE_ATTACHMENTS is used in EAS 12.0 and up
                    attachmentsParser(atts, tag);
                    break;
                case Tags.EMAIL_TO:
                    msg.setTo(Address.parse(getValue()));
                    break;
                case Tags.EMAIL_FROM:
                    msg.setFrom(Address.parse(getValue()));
                    break;
                case Tags.EMAIL_CC:
                    msg.setCc(Address.parse(getValue()));
                    break;
                case Tags.EMAIL_REPLY_TO:
                    msg.setReplyTo(Address.parse(getValue()));
                    break;
                case Tags.EMAIL_DATE_RECEIVED:
                    try {
                        msg.setTimeStamp(Utility.parseEmailDateTimeToMillis(getValue()));
                    } catch (ParseException e) {
                        LogUtils.w(TAG, "Parse error for EMAIL_DATE_RECEIVED tag.", e);
                    }
                    break;
                case Tags.EMAIL_SUBJECT:
                    msg.setSubject(getValue());
                    break;
                case Tags.EMAIL_READ:
                    msg.setFlagRead(getValueInt() == 1);
                    break;
                case Tags.BASE_BODY:
                    bodyParser(msg);
                    break;
                case Tags.EMAIL_FLAG:
                    msg.setFlagFavorite(flagParser());
                    break;
                case Tags.EMAIL_MIME_TRUNCATED:
                    truncated = getValueInt() == 1;
                    break;
                case Tags.EMAIL_MIME_DATA:
                    // We get MIME data for EAS 2.5.  First we parse it, then we take the
                    // html and/or plain text data and store it in the message
                    if (truncated) {
                        // If the MIME data is truncated, don't bother parsing it, because
                        // it will take time and throw an exception anyway when EOF is reached
                        // In this case, we will load the body separately by tagging the message
                        // "partially loaded".
                        // Get the data (and ignore it)
                        getValue();
                        userLog("Partially loaded: ", msg.getServerId());
                        msg.setFlagLoaded(MessageData.FLAG_LOADED_PARTIAL);
                        mFetchNeeded = true;
                    } else {
                        mimeBodyParser(msg, getValue());
                    }
                    break;
                case Tags.EMAIL_BODY:
                    String text = getValue();
                    msg.setBody(text);
                    break;
                case Tags.EMAIL_MESSAGE_CLASS:
                    String messageClass = getValue();
                    if (messageClass.equals("IPM.Schedule.Meeting.Request")) {
                        msg.addFlags(MessageData.FLAG_INCOMING_MEETING_INVITE);
                    } else if (messageClass.equals("IPM.Schedule.Meeting.Canceled")) {
                        msg.addFlags(MessageData.FLAG_INCOMING_MEETING_CANCEL);
                    }
                    break;
                case Tags.EMAIL_MEETING_REQUEST:
                    skipParser(Tags.EMAIL_MEETING_REQUEST);
                    break;
                case Tags.EMAIL_THREAD_TOPIC:
                    msg.setThreadTopic(getValue());
                    break;
                case Tags.RIGHTS_LICENSE:
                    skipParser(tag);
                    break;
                case Tags.EMAIL2_CONVERSATION_ID:
                    String serverConversationId = Base64.encodeToString(getValueBytes(), Base64.URL_SAFE);
                    msg.setServerConversationId(serverConversationId);
                    break;
                case Tags.EMAIL2_CONVERSATION_INDEX:
                    // Ignore this byte array since we're not constructing a tree.
                    getValueBytes();
                    break;
                case Tags.EMAIL2_LAST_VERB_EXECUTED:
                    int val = getValueInt();
                    if (val == LAST_VERB_REPLY || val == LAST_VERB_REPLY_ALL) {
                        // We aren't required to distinguish between reply and reply all here
                        msg.addFlags(MessageData.FLAG_REPLIED_TO);
                    } else if (val == LAST_VERB_FORWARD) {
                        msg.addFlags(MessageData.FLAG_FORWARDED);
                    }
                    break;
                default:
                    skipTag();
            }
        }

        if (atts.size() > 0) {
            msg.setAttachments(atts);
        }
    }

    /**
     * Parse a message from the server stream.
     */
    private void addParser(final int endingTag) throws IOException, CommandStatusException {
        // Default to 1 (success) in case we don't get this tag
        int status = 1;
        MessageData messageData = new MessageData();
        messageData.setFlagLoaded(MessageData.FLAG_LOADED_COMPLETE);

        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.SYNC_SERVER_ID:
                    String serverId = getValue();
                    messageData.setServerId(serverId);
                    break;
                case Tags.SYNC_STATUS:
                    status = getValueInt();
                    break;
                case Tags.SYNC_APPLICATION_DATA:
                    addData(messageData, tag);
                    break;
                default:
                    skipTag();
            }
        }
        // For sync, status 1 = success
        if (status != 1) {
            throw new CommandStatusException(status, messageData.getServerId());
        }

        callback.addMessage(messageData);
    }

    // For now, we only care about the "active" state
    private boolean flagParser() throws IOException {
        boolean state = false;
        while (nextTag(Tags.EMAIL_FLAG) != END) {
            switch (tag) {
                case Tags.EMAIL_FLAG_STATUS:
                    state = getValueInt() == 2;
                    break;
                default:
                    skipTag();
            }
        }
        return state;
    }

    private void bodyParser(MessageData msg) throws IOException {
        String bodyType = Eas.BODY_PREFERENCE_TEXT;
        String body = "";
        while (nextTag(Tags.BASE_BODY) != END) {
            switch (tag) {
                case Tags.BASE_TYPE:
                    bodyType = getValue();
                    break;
                case Tags.BASE_DATA:
                    body = getValue();
                    break;
                default:
                    skipTag();
            }
        }
        // We always ask for TEXT or HTML; there's no third option
        if (bodyType.equals(Eas.BODY_PREFERENCE_HTML)) {
            msg.setHtml(body);
        } else {
            msg.setText(body);
        }
    }

    /**
     * Parses untruncated MIME data, saving away the text parts
     * @param msg the message we're building
     * @param mimeData the MIME data we've received from the server
     * @throws IOException
     */
    private static void mimeBodyParser(MessageData msg, String mimeData)
            throws IOException {
//        try {
//            ByteArrayInputStream in = new ByteArrayInputStream(mimeData.getBytes());
//            // The constructor parses the message
//            MimeMessage mimeMessage = new MimeMessage(in);
//            // Now process body parts & attachments
//            ArrayList<Part> viewables = new ArrayList<Part>();
//            // We'll ignore the attachments, as we'll get them directly from EAS
//            ArrayList<Part> attachments = new ArrayList<Part>();
//            MimeUtility.collectParts(mimeMessage, viewables, attachments);
//            // parseBodyFields fills in the content fields of the Body
//            ConversionUtilities.BodyFieldData data =
//                    ConversionUtilities.parseBodyFields(viewables);
//            // But we need them in the message itself for handling during commit()
//            msg.setFlags(data.isQuotedReply, data.isQuotedForward);
//            msg.mSnippet = data.snippet;
//            msg.mHtml = data.htmlContent;
//            msg.mText = data.textContent;
//        } catch (MessagingException e) {
//            // This would most likely indicate a broken stream
//            throw new IOException(e);
//        }
    }

    private void attachmentsParser(final ArrayList<AttachmentData> atts, final int endingTag) throws IOException {
        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.EMAIL_ATTACHMENT:
                case Tags.BASE_ATTACHMENT:  // BASE_ATTACHMENT is used in EAS 12.0 and up
                    attachmentParser(atts, tag);
                    break;
                default:
                    skipTag();
            }
        }
    }

    private void attachmentParser(final ArrayList<AttachmentData> atts, final int endingTag) throws IOException {
        String fileName = null;
        String length = null;
        String location = null;
        boolean isInline = false;
        String contentId = null;

        while (nextTag(endingTag) != END) {
            switch (tag) {
                // We handle both EAS 2.5 and 12.0+ attachments here
                case Tags.EMAIL_DISPLAY_NAME:
                case Tags.BASE_DISPLAY_NAME:
                    fileName = getValue();
                    break;
                case Tags.EMAIL_ATT_NAME:
                case Tags.BASE_FILE_REFERENCE:
                    location = getValue();
                    break;
                case Tags.EMAIL_ATT_SIZE:
                case Tags.BASE_ESTIMATED_DATA_SIZE:
                    length = getValue();
                    break;
                case Tags.BASE_IS_INLINE:
                    isInline = getValueInt() == 1;
                    break;
                case Tags.BASE_CONTENT_ID:
                    contentId = getValue();
                    break;
                default:
                    skipTag();
            }
        }

        if ((fileName != null) && (length != null) && (location != null)) {
            AttachmentData att = new AttachmentData();
            att.setEncoding("base64");
            att.setSize(Long.parseLong(length));
            att.setFileName(fileName);
            att.setLocation(location);
            att.setMimeType(getMimeTypeFromFileName(fileName));
            // Save away the contentId, if we've got one (for inline images); note that the
            // EAS docs appear to be wrong about the tags used; inline images come with
            // contentId rather than contentLocation, when sent from Ex03, Ex07, and Ex10
            if (isInline && !TextUtils.isEmpty(contentId)) {
                att.setContentId(contentId);
            }
            atts.add(att);
        }
    }

    /**
     * Returns an appropriate mimetype for the given file name's extension. If a mimetype
     * cannot be determined, {@code application/<<x>>} [where @{code <<x>> is the extension,
     * if it exists or {@code application/octet-stream}].
     * At the moment, this is somewhat lame, since many file types aren't recognized
     * @param fileName the file name to ponder
     */
    // Note: The MimeTypeMap method currently uses a very limited set of mime types
    // A bug has been filed against this issue.
    public String getMimeTypeFromFileName(String fileName) {
        String mimeType;
        int lastDot = fileName.lastIndexOf('.');
        String extension = null;
        if ((lastDot > 0) && (lastDot < fileName.length() - 1)) {
            extension = fileName.substring(lastDot + 1).toLowerCase();
        }
        if (extension == null) {
            // A reasonable default for now.
            mimeType = "application/octet-stream";
        } else {
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType == null) {
                mimeType = "application/" + extension;
            }
        }
        return mimeType;
    }

    void deleteParser(int entryTag) throws IOException {
        while (nextTag(entryTag) != END) {
            switch (tag) {
                case Tags.SYNC_SERVER_ID:
                    String serverId = getValue();
                    callback.removeMessage(serverId);
                    break;
                default:
                    skipTag();
            }
        }
    }

    void changeParser() throws IOException {
        String serverId = null;
        while (nextTag(Tags.SYNC_CHANGE) != END) {
            switch (tag) {
                case Tags.SYNC_SERVER_ID:
                    serverId = getValue();
                    break;
                case Tags.SYNC_APPLICATION_DATA:
                    changeApplicationDataParser(serverId);
                    break;
                default:
                    skipTag();
            }
        }
    }

    private void changeApplicationDataParser(String serverId) throws IOException {
        while (nextTag(Tags.SYNC_APPLICATION_DATA) != END) {
            switch (tag) {
                case Tags.EMAIL_READ:
                    boolean read = getValueInt() == 1;
                    callback.readStateChanged(serverId, read);
                    break;
                case Tags.EMAIL_FLAG:
                    boolean flag = flagParser();
                    callback.flagStateChanged(serverId, flag);
                    break;
                case Tags.EMAIL2_LAST_VERB_EXECUTED:
                    int val = getValueInt();
                    if (val == LAST_VERB_REPLY || val == LAST_VERB_REPLY_ALL) {
                        callback.messageWasRepliedTo(serverId);
                    } else if (val == LAST_VERB_FORWARD) {
                        callback.messageWasForwarded(serverId);
                    }
                    break;
                default:
                    skipTag();
            }
        }
    }

    /* (non-Javadoc)
     * @see com.android.exchange.adapter.EasContentParser#commandsParser()
     */
    @Override
    public void commandsParser() throws IOException, CommandStatusException {
        while (nextTag(Tags.SYNC_COMMANDS) != END) {
            if (tag == Tags.SYNC_ADD) {
                addParser(tag);
            } else if (tag == Tags.SYNC_DELETE || tag == Tags.SYNC_SOFT_DELETE) {
                deleteParser(tag);
            } else if (tag == Tags.SYNC_CHANGE) {
                changeParser();
            } else
                skipTag();
        }
    }

    // EAS values for status element of sync responses.
    // TODO: Not all are used yet, but I wanted to transcribe all possible values.
    public static final int EAS_SYNC_STATUS_SUCCESS = 1;
    public static final int EAS_SYNC_STATUS_BAD_SYNC_KEY = 3;
    public static final int EAS_SYNC_STATUS_PROTOCOL_ERROR = 4;
    public static final int EAS_SYNC_STATUS_SERVER_ERROR = 5;
    public static final int EAS_SYNC_STATUS_BAD_CLIENT_DATA = 6;
    public static final int EAS_SYNC_STATUS_CONFLICT = 7;
    public static final int EAS_SYNC_STATUS_OBJECT_NOT_FOUND = 8;
    public static final int EAS_SYNC_STATUS_CANNOT_COMPLETE = 9;
    public static final int EAS_SYNC_STATUS_FOLDER_SYNC_NEEDED = 12;
    public static final int EAS_SYNC_STATUS_INCOMPLETE_REQUEST = 13;
    public static final int EAS_SYNC_STATUS_BAD_HEARTBEAT_VALUE = 14;
    public static final int EAS_SYNC_STATUS_TOO_MANY_COLLECTIONS = 15;
    public static final int EAS_SYNC_STATUS_RETRY = 16;

    public static boolean shouldRetry(final int status) {
        return status == EAS_SYNC_STATUS_SERVER_ERROR || status == EAS_SYNC_STATUS_RETRY;
    }

    /**
     * Parse the status for a single message update.
     * @param endTag the tag we end with
     * @throws IOException
     */
    public void messageUpdateParser(int endTag) throws IOException {
        // We get serverId and status in the responses
        String serverId = null;
        int status = -1;
        while (nextTag(endTag) != END) {
            if (tag == Tags.SYNC_STATUS) {
                status = getValueInt();
            } else if (tag == Tags.SYNC_SERVER_ID) {
                serverId = getValue();
            } else {
                skipTag();
            }
        }
        if (serverId != null && status != -1) {
            mMessageUpdateStatus.put(serverId, status);
        }
    }

    @Override
    public void responsesParser() throws IOException {
        while (nextTag(Tags.SYNC_RESPONSES) != END) {
            if (tag == Tags.SYNC_ADD || tag == Tags.SYNC_CHANGE || tag == Tags.SYNC_DELETE) {
                messageUpdateParser(tag);
            } else if (tag == Tags.SYNC_FETCH) {
                try {
                    addParser(tag);
                } catch (CommandStatusException sse) {
                    if (sse.mStatus == 8) {
                        // 8 = object not found; delete the message from EmailProvider
                        // No other status should be seen in a fetch response, except, perhaps,
                        // for some temporary server failure
                        callback.removeMessage(sse.mItemId);
                    }
                }
            }
        }
    }

    @Override
    public boolean parse() throws IOException, CommandStatusException {
        final boolean result = super.parse();
        return result || fetchNeeded();
    }

    @Override
    public void commit() throws RemoteException, OperationApplicationException {
        callback.commitMessageChanges();
    }
}
