package com.fsck.k9.protocol.eas.adapter;


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.data.Message;
import com.fsck.k9.mail.message.MessageBuilderFactory;
import com.fsck.k9.mail.message.MessageParser;
import com.fsck.k9.protocol.eas.CommandStatusException;
import com.fsck.k9.protocol.eas.Eas;
import com.fsck.k9.protocol.eas.LogUtils;
import com.fsck.k9.protocol.eas.Utility;
import com.fsck.k9.protocol.eas.callback.EmailSyncCallback;


/**
 * Parser for Sync on an email collection.
 */
public class EmailSyncParser extends AbstractSyncParser {
    private static final String TAG = Eas.LOG_TAG;

    private static final int LAST_VERB_REPLY = 1;
    private static final int LAST_VERB_REPLY_ALL = 2;
    private static final int LAST_VERB_FORWARD = 3;

    // EAS values for status element of sync responses.
//    public static final int EAS_SYNC_STATUS_SUCCESS = 1;
//    public static final int EAS_SYNC_STATUS_BAD_SYNC_KEY = 3;
//    public static final int EAS_SYNC_STATUS_PROTOCOL_ERROR = 4;
    public static final int EAS_SYNC_STATUS_SERVER_ERROR = 5;
//    public static final int EAS_SYNC_STATUS_BAD_CLIENT_DATA = 6;
//    public static final int EAS_SYNC_STATUS_CONFLICT = 7;
//    public static final int EAS_SYNC_STATUS_OBJECT_NOT_FOUND = 8;
//    public static final int EAS_SYNC_STATUS_CANNOT_COMPLETE = 9;
//    public static final int EAS_SYNC_STATUS_FOLDER_SYNC_NEEDED = 12;
//    public static final int EAS_SYNC_STATUS_INCOMPLETE_REQUEST = 13;
//    public static final int EAS_SYNC_STATUS_BAD_HEARTBEAT_VALUE = 14;
//    public static final int EAS_SYNC_STATUS_TOO_MANY_COLLECTIONS = 15;
    public static final int EAS_SYNC_STATUS_RETRY = 16;


    private final String folderServerId;
    private final EmailSyncCallback callback;
    private final MessageBuilderFactory messageBuilderFactory;
    private final Map<String, Integer> messageUpdateStatus = new HashMap<String, Integer>();


    public EmailSyncParser(InputStream in, String folderServerId, EmailSyncCallback callback,
            MessageBuilderFactory messageBuilderFactory) throws IOException {
        super(in, callback);
        this.folderServerId = folderServerId;
        this.callback = callback;
        this.messageBuilderFactory = messageBuilderFactory;
    }

    public static boolean shouldRetry(final int status) {
        return status == EAS_SYNC_STATUS_SERVER_ERROR || status == EAS_SYNC_STATUS_RETRY;
    }

    public Map<String, Integer> getMessageStatuses() {
        return messageUpdateStatus;
    }

    private void addData(MessageData messageData, int endingTag) throws IOException {
        ArrayList<AttachmentData> attachmentDataList = new ArrayList<AttachmentData>();
        boolean truncated = false;

        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.EMAIL_ATTACHMENTS:
                // BASE_ATTACHMENTS is used in EAS 12.0 and up
                case Tags.BASE_ATTACHMENTS: {
                    attachmentsParser(attachmentDataList, tag);
                    break;
                }
                case Tags.EMAIL_TO: {
                    messageData.setTo(Address.parse(getValue()));
                    break;
                }
                case Tags.EMAIL_FROM: {
                    messageData.setFrom(Address.parse(getValue()));
                    break;
                }
                case Tags.EMAIL_CC: {
                    messageData.setCc(Address.parse(getValue()));
                    break;
                }
                case Tags.EMAIL_REPLY_TO: {
                    messageData.setReplyTo(Address.parse(getValue()));
                    break;
                }
                case Tags.EMAIL_DATE_RECEIVED: {
                    try {
                        messageData.setTimeStamp(Utility.parseEmailDateTimeToMillis(getValue()));
                    } catch (ParseException e) {
                        LogUtils.w(TAG, "Parse error for EMAIL_DATE_RECEIVED tag.", e);
                    }
                    break;
                }
                case Tags.EMAIL_SUBJECT: {
                    messageData.setSubject(getValue());
                    break;
                }
                case Tags.EMAIL_READ: {
                    messageData.setFlagRead(getValueBoolean());
                    break;
                }
                case Tags.BASE_BODY: {
                    bodyParser(messageData);
                    break;
                }
                case Tags.EMAIL_FLAG: {
                    messageData.setFlagFavorite(flagParser());
                    break;
                }
                case Tags.EMAIL_MIME_TRUNCATED: {
                    truncated = getValueBoolean();
                    messageData.setMessageTruncated(truncated);
                    break;
                }
                case Tags.EMAIL_MIME_DATA: {
                    // We get MIME data for EAS 2.5.  First we parse it, then we take the
                    // html and/or plain text data and store it in the message
                    if (truncated) {
                        // If the MIME data is truncated, don't bother parsing it, because
                        // it will take time and throw an exception anyway when EOF is reached
                        // In this case, we will load the body separately by tagging the message
                        // "partially loaded".
                        // Get the data (and ignore it)
                        getValue();
                        userLog("Partially loaded: ", messageData.getServerId());
                        messageData.setFlagLoaded(MessageData.FLAG_LOADED_PARTIAL);
                    } else {
                        mimeBodyParser(messageData, getValueStream());
                    }
                    break;
                }
                case Tags.EMAIL_BODY: {
                    String text = getValue();
                    messageData.setBody(text);
                    break;
                }
                case Tags.EMAIL_MESSAGE_CLASS: {
                    String messageClass = getValue();
                    if (messageClass.equals("IPM.Schedule.Meeting.Request")) {
                        messageData.addFlags(MessageData.FLAG_INCOMING_MEETING_INVITE);
                    } else if (messageClass.equals("IPM.Schedule.Meeting.Canceled")) {
                        messageData.addFlags(MessageData.FLAG_INCOMING_MEETING_CANCEL);
                    }
                    break;
                }
                case Tags.EMAIL_MEETING_REQUEST: {
                    skipParser(Tags.EMAIL_MEETING_REQUEST);
                    break;
                }
                case Tags.EMAIL_THREAD_TOPIC: {
                    messageData.setThreadTopic(getValue());
                    break;
                }
                case Tags.RIGHTS_LICENSE: {
                    skipParser(tag);
                    break;
                }
                case Tags.EMAIL2_CONVERSATION_ID: {
                    String serverConversationId = Base64.encodeToString(getValueBytes(), Base64.URL_SAFE);
                    messageData.setServerConversationId(serverConversationId);
                    break;
                }
                case Tags.EMAIL2_CONVERSATION_INDEX: {
                    // Ignore this byte array since we're not constructing a tree.
                    getValueBytes();
                    break;
                }
                case Tags.EMAIL2_LAST_VERB_EXECUTED: {
                    int val = getValueInt();
                    if (val == LAST_VERB_REPLY || val == LAST_VERB_REPLY_ALL) {
                        // We aren't required to distinguish between reply and reply all here
                        messageData.addFlags(MessageData.FLAG_REPLIED_TO);
                    } else if (val == LAST_VERB_FORWARD) {
                        messageData.addFlags(MessageData.FLAG_FORWARDED);
                    }
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }

        if (attachmentDataList.size() > 0) {
            messageData.setAttachments(attachmentDataList);
        }
    }

    private void addParser(int endingTag) throws IOException, CommandStatusException {
        int status = 1;

        MessageData messageData = new MessageData();
        messageData.setFolderServerId(folderServerId);
        messageData.setFlagLoaded(MessageData.FLAG_LOADED_COMPLETE);

        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.SYNC_SERVER_ID: {
                    String serverId = getValue();
                    messageData.setServerId(serverId);
                    break;
                }
                case Tags.SYNC_STATUS: {
                    status = getValueInt();
                    break;
                }
                case Tags.SYNC_APPLICATION_DATA: {
                    addData(messageData, tag);
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }

        if (status != 1) {
            throw new CommandStatusException(status, messageData.getServerId());
        }

        if (messageData.message() == null) {
            Message message = MessageDataToMessage.buildEmptyMessage(messageData);
            messageData.setMessage(message);
            messageData.setMessageTruncated(true);
        }

        callback.addMessage(messageData);
    }

    // For now, we only care about the "active" state
    private boolean flagParser() throws IOException {
        boolean state = false;
        while (nextTag(Tags.EMAIL_FLAG) != END) {
            switch (tag) {
                case Tags.EMAIL_FLAG_STATUS: {
                    state = getValueInt() == 2;
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }

        return state;
    }

    private void bodyParser(MessageData messageData) throws IOException {
        String bodyType = Eas.BODY_PREFERENCE_TEXT;

        while (nextTag(Tags.BASE_BODY) != END) {
            switch (tag) {
                case Tags.BASE_TYPE: {
                    bodyType = getValue();
                    break;
                }
                case Tags.BASE_DATA: {
                    if (Eas.BODY_PREFERENCE_HTML.equals(bodyType)) {
                        messageData.setHtml(getValue());
                    } else if (Eas.BODY_PREFERENCE_TEXT.equals(bodyType)) {
                        messageData.setText(getValue());
                    } else if (Eas.BODY_PREFERENCE_MIME.equals(bodyType)) {
                        mimeBodyParser(messageData, getValueStream());
                    }
                    break;
                }
                case Tags.BASE_TRUNCATED: {
                    boolean truncated = getValueBoolean();
                    messageData.setMessageTruncated(truncated);
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }
    }

    @VisibleForTesting
    void mimeBodyParser(MessageData messageData, InputStream inputStream) throws IOException {
        Message message = MessageParser.parse(inputStream, messageBuilderFactory);
        messageData.setMessage(message);
    }

    private void attachmentsParser(List<AttachmentData> attachmentDataList, int endingTag) throws IOException {
        while (nextTag(endingTag) != END) {
            switch (tag) {
                case Tags.EMAIL_ATTACHMENT:
                // BASE_ATTACHMENT is used in EAS 12.0 and up
                case Tags.BASE_ATTACHMENT: {
                    attachmentParser(attachmentDataList, tag);
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }
    }

    private void attachmentParser(List<AttachmentData> attachmentDataList, int endingTag) throws IOException {
        String fileName = null;
        String length = null;
        String location = null;
        boolean isInline = false;
        String contentId = null;

        while (nextTag(endingTag) != END) {
            switch (tag) {
                // We handle both EAS 2.5 and 12.0+ attachments here
                case Tags.EMAIL_DISPLAY_NAME:
                case Tags.BASE_DISPLAY_NAME: {
                    fileName = getValue();
                    break;
                }
                case Tags.EMAIL_ATT_NAME:
                case Tags.BASE_FILE_REFERENCE: {
                    location = getValue();
                    break;
                }
                case Tags.EMAIL_ATT_SIZE:
                case Tags.BASE_ESTIMATED_DATA_SIZE: {
                    length = getValue();
                    break;
                }
                case Tags.BASE_IS_INLINE: {
                    isInline = getValueBoolean();
                    break;
                }
                case Tags.BASE_CONTENT_ID: {
                    contentId = getValue();
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }

        if (fileName != null && length != null && location != null) {
            AttachmentData attachmentData = new AttachmentData();
            attachmentData.setEncoding("base64");
            attachmentData.setSize(Long.parseLong(length));
            attachmentData.setFileName(fileName);
            attachmentData.setLocation(location);
            attachmentData.setMimeType(getMimeTypeFromFileName(fileName));
            // Save away the contentId, if we've got one (for inline images); note that the
            // EAS docs appear to be wrong about the tags used; inline images come with
            // contentId rather than contentLocation, when sent from Ex03, Ex07, and Ex10
            if (isInline && !TextUtils.isEmpty(contentId)) {
                attachmentData.setContentId(contentId);
            }
            attachmentDataList.add(attachmentData);
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
    private String getMimeTypeFromFileName(String fileName) {
        String mimeType;
        int lastDot = fileName.lastIndexOf('.');
        String extension = null;

        if (lastDot > 0 && lastDot < fileName.length() - 1) {
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
                case Tags.SYNC_SERVER_ID: {
                    String serverId = getValue();
                    callback.removeMessage(serverId);
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }
    }

    void changeParser() throws IOException {
        String serverId = null;
        while (nextTag(Tags.SYNC_CHANGE) != END) {
            switch (tag) {
                case Tags.SYNC_SERVER_ID: {
                    serverId = getValue();
                    break;
                }
                case Tags.SYNC_APPLICATION_DATA: {
                    changeApplicationDataParser(serverId);
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }
    }

    private void changeApplicationDataParser(String serverId) throws IOException {
        while (nextTag(Tags.SYNC_APPLICATION_DATA) != END) {
            switch (tag) {
                case Tags.EMAIL_READ: {
                    boolean read = getValueBoolean();
                    callback.readStateChanged(serverId, read);
                    break;
                }
                case Tags.EMAIL_FLAG: {
                    boolean flag = flagParser();
                    callback.flagStateChanged(serverId, flag);
                    break;
                }
                case Tags.EMAIL2_LAST_VERB_EXECUTED: {
                    int val = getValueInt();
                    if (val == LAST_VERB_REPLY || val == LAST_VERB_REPLY_ALL) {
                        callback.messageWasRepliedTo(serverId);
                    } else if (val == LAST_VERB_FORWARD) {
                        callback.messageWasForwarded(serverId);
                    }
                    break;
                }
                default: {
                    skipTag();
                }
            }
        }
    }

    @Override
    public void commandsParser() throws IOException, CommandStatusException {
        while (nextTag(Tags.SYNC_COMMANDS) != END) {
            if (tag == Tags.SYNC_ADD) {
                addParser(tag);
            } else if (tag == Tags.SYNC_DELETE || tag == Tags.SYNC_SOFT_DELETE) {
                deleteParser(tag);
            } else if (tag == Tags.SYNC_CHANGE) {
                changeParser();
            } else {
                skipTag();
            }
        }
    }

    private void messageUpdateParser(int endTag) throws IOException {
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
            messageUpdateStatus.put(serverId, status);
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
    public void commit() throws RemoteException, OperationApplicationException {
        callback.commitMessageChanges();
    }
}
