package com.fsck.k9.provider.message;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Message;
import com.fsck.k9.message.MessageContainer;
import com.fsck.k9.message.Metadata;
import com.fsck.k9.message.Multipart;
import com.fsck.k9.message.Part;
import com.fsck.k9.message.Body.StreamType;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants;
import com.fsck.k9.provider.EmailProviderConstants.MessageColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessagePartAttributeColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessagePartColumns;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.DefaultFieldParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Map.Entry;

/**
 * Helper class to deal with loading and storing {@link EmailProviderMessage} objects from/to
 * {@link EmailProvider}.
 */
public class EmailProviderHelper {

    public static final String[] MESSAGE_PROJECTION = {
        MessageColumns.ID,
        MessageColumns.FOLDER_ID,
        MessageColumns.UID,
        MessageColumns.LOCAL_ONLY,
        MessageColumns.DELETED,
        MessageColumns.NOTIFIED,
        MessageColumns.DATE,
        MessageColumns.INTERNAL_DATE,
        MessageColumns.SEEN,
        MessageColumns.FLAGGED,
        MessageColumns.ANSWERED,
        MessageColumns.FORWARDED,
        MessageColumns.DESTROYED,
        MessageColumns.SEND_FAILED,
        MessageColumns.SEND_IN_PROGRESS,
        MessageColumns.DOWNLOADED_FULL,
        MessageColumns.DOWNLOADED_PARTIAL,
        MessageColumns.REMOTE_COPY_STARTED,
        MessageColumns.GOT_ALL_HEADERS
    };

    public static final String[] PART_PROJECTION = {
        MessagePartColumns.ID,
        MessagePartColumns.MIME_TYPE,
        MessagePartColumns.PARENT,
        MessagePartColumns.HEADER,
        MessagePartColumns.COMPLETE,
        MessagePartColumns.EPILOGUE,
        MessagePartColumns.PREAMBLE,
        MessagePartColumns.SIZE,
    };

    /**
     * Cache for {@link EmailProviderMessageFactory} objects (one per account).
     */
    private static Map<String, EmailProviderMessageFactory> sFactories =
            new HashMap<String, EmailProviderMessageFactory>();

    /**
     * Get an {@link EmailProviderMessageFactory} for creating {@link Message} and related objects
     * for the specified account.
     *
     * @param context
     *         A {@link Context} instance.
     * @param accountUuid
     *         The UUID of the account to create the {@code EmailProviderMessageFactory} for.
     *
     * @return An {@code EmailProviderMessageFactory} instance for the specified account.
     */
    public static EmailProviderMessageFactory getFactory(Context context, String accountUuid) {
        EmailProviderMessageFactory factory = sFactories.get(accountUuid);
        if (factory == null) {
            factory = new EmailProviderMessageFactory(context, accountUuid);
            sFactories.put(accountUuid, factory);
        }

        return factory;
    }

    /**
     * Stores a {@code Message} with associated {@code Metadata} in the database using
     * {@link EmailProvider}.
     *
     * @param context
     *         A {@link Context} instance.
     * @param container
     *         The {@link MessageContainer} object holding the {@link Message} with associated
     *         {@link Metadata}.
     *
     * @return The message ID that can be used to restore this message from {@code EmailProvider}.
     */
    public static long saveMessage(Context context, MessageContainer container) {
        ContentResolver resolver = context.getContentResolver();

        Metadata meta = container.getMetadata();
        if (!(meta instanceof EmailProviderMetadata)) {
            throw new IllegalArgumentException("container.getMetadata() must be of type EmailProviderMetadata");
        }

        EmailProviderMetadata metadata = (EmailProviderMetadata) meta;
        Message message = container.getMessage();

        String accountUuid = metadata.getAccountUuid();

        final Uri messageUri = EmailProviderConstants.Message.getContentUri(accountUuid);
        final Uri partUri = EmailProviderConstants.MessagePart.getContentUri(accountUuid);
        final Uri attributesUri = EmailProviderConstants.MessagePartAttibute.getContentUri(accountUuid);


        Map<Long, int[]> messageOrder = new HashMap<Long, int[]>();

        Stack<PartDescription> stack = new Stack<PartDescription>();
        stack.push(new PartDescription(message, 0, 0, 0));

        long rootMessageId = 0;

        while (!stack.isEmpty()) {
            PartDescription p = stack.pop();
            Part part = p.part;
            long messageId = p.messageId;
            long parentPartId = p.parentId;
            int order = p.order;

            if (part instanceof Message) {
                // Create entry in 'messages' table
                ContentValues msg = new ContentValues();
                int msgOrder;
                if (rootMessageId == 0) {
                    msgOrder = 1;

                    msg.put(MessageColumns.SEEN, metadata.isFlagSet(Flag.SEEN));
                    msg.put(MessageColumns.ANSWERED, metadata.isFlagSet(Flag.ANSWERED));
                    msg.put(MessageColumns.FLAGGED, metadata.isFlagSet(Flag.FLAGGED));
                    msg.put(MessageColumns.DESTROYED, metadata.isFlagSet(Flag.X_DESTROYED));
                    msg.put(MessageColumns.DOWNLOADED_FULL, metadata.isFlagSet(Flag.X_DOWNLOADED_FULL));
                    msg.put(MessageColumns.DOWNLOADED_PARTIAL, metadata.isFlagSet(Flag.X_DOWNLOADED_PARTIAL));
                    msg.put(MessageColumns.GOT_ALL_HEADERS, metadata.isFlagSet(Flag.X_GOT_ALL_HEADERS));
                    msg.put(MessageColumns.REMOTE_COPY_STARTED, metadata.isFlagSet(Flag.X_REMOTE_COPY_STARTED));
                    msg.put(MessageColumns.SEND_FAILED, metadata.isFlagSet(Flag.X_SEND_FAILED));
                    msg.put(MessageColumns.SEND_IN_PROGRESS, metadata.isFlagSet(Flag.X_SEND_IN_PROGRESS));
                } else {
                    int[] currentOrder = messageOrder.get(messageId);
                    msgOrder = currentOrder[0];
                    currentOrder[0]++;

                    msg.put(MessageColumns.ROOT, rootMessageId);
                    msg.put(MessageColumns.PARENT, messageId);
                }

                msg.put(MessageColumns.FOLDER_ID, metadata.getFolderId());
                msg.put(MessageColumns.UID, metadata.getServerId());
                msg.put(MessageColumns.LOCAL_ONLY, metadata.isLocalOnly());
                msg.put(MessageColumns.DELETED, metadata.isFlagSet(Flag.DELETED));
                msg.put(MessageColumns.SEQ, msgOrder);

                long serverTimestamp = metadata.getDate().getTime();
                Date messageDate = MimeUtility.getDate(part);
                long messageTimestamp = (messageDate != null) ? messageDate.getTime() : serverTimestamp;
                msg.put(MessageColumns.DATE, messageTimestamp);
                msg.put(MessageColumns.INTERNAL_DATE, serverTimestamp);

                Uri createUri = resolver.insert(messageUri, msg);
                messageId = Long.parseLong(createUri.getLastPathSegment());

                if (rootMessageId == 0) {
                    rootMessageId = messageId;
                }
                messageOrder.put(messageId, new int[] {1});
            }

            // Create entry in 'message_parts' table
            ContentValues cv = new ContentValues();
            cv.put(MessagePartColumns.MESSAGE_ID, messageId);

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                part.getHeader().writeTo(buf);
            } catch (IOException e2) {
                // TODO Auto-generated catch block
                e2.printStackTrace();
            }
            cv.put(MessagePartColumns.HEADER, buf.toString());

            String[] headers = part.getHeader().get("Content-Type");
            String contentType = (headers != null && headers.length >= 1) ? headers[0] : "";
            String mimeType = null;
            try {
                ContentTypeField ct = (ContentTypeField) DefaultFieldParser.parse("Content-Type: " + contentType);
                //TODO: use ContentTypeField.getMimeType(ContentTypeField,ContentTypeField)
                mimeType = ct.getMimeType();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            cv.put(MessagePartColumns.MIME_TYPE, mimeType);

            Body body = part.getBody();
            boolean isMultipart = (body instanceof Multipart);
            boolean isMessage = (body instanceof Message);

            if (parentPartId != 0) {
                cv.putNull(MessagePartColumns.PARENT);
            }

            cv.put(MessagePartColumns.SEQ, order);

            if (isMultipart) {
                Multipart multipart = (Multipart) body;
                cv.put(MessagePartColumns.DATA_TYPE, 0); //empty
                cv.put(MessagePartColumns.PREAMBLE, multipart.getPreamble());   //TODO: base64encode?
                cv.put(MessagePartColumns.EPILOGUE, multipart.getEpilogue());   //TODO: base64encode?
                cv.put(MessagePartColumns.COMPLETE, true);  //FIXME
            } else if (!isMessage) {
                cv.put(MessagePartColumns.DATA_TYPE, 2); //external file
                cv.put(MessagePartColumns.COMPLETE, false);
            }

            Uri createdPartUri = resolver.insert(partUri, cv);
            long partId = Long.parseLong(createdPartUri.getLastPathSegment());

            metadata.updateMapping(part, partId);

            if (isMultipart) {
                Multipart multipart = (Multipart) body;
                List<Part> parts = multipart.getParts();
                Collections.reverse(parts);

                int seq = parts.size();
                for (Part child : parts) {
                    stack.push(new PartDescription(child, messageId, partId, seq--));
                }
            } else if (body != null) {
                if (isMessage) {
                    stack.push(new PartDescription((Message)body, messageId, partId, 0));
                } else {
                    writeMessagePart(context, metadata, body, partId);
                }
            }

            // Write message part attributes
            Map<String, String> attributes = metadata.getAttributes(part);
            if (attributes != null && attributes.size() > 0) {
                ContentValues[] values = new ContentValues[attributes.size()];
                int i = 0;
                for (Entry<String, String> attribute : attributes.entrySet()) {
                    ContentValues v = new ContentValues();
                    v.put(MessagePartAttributeColumns.MESSAGE_PART_ID, partId);
                    v.put(MessagePartAttributeColumns.KEY, attribute.getKey());
                    v.put(MessagePartAttributeColumns.VALUE, attribute.getValue());
                    values[i++] = v;
                }
                resolver.bulkInsert(attributesUri, values);
            }
        }

        return rootMessageId;
    }

    private static long writeMessagePart(Context context, EmailProviderMetadata metaData, Body body, long partId) {
        long size = -1;
        try {
            String accountUuid = metaData.getAccountUuid();

            Uri uri = ContentUris.withAppendedId(EmailProviderConstants.MessagePart.getContentUri(accountUuid), partId);
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            InputStream in = body.getInputStream(StreamType.UNMODIFIED);
            size = IOUtils.copy(in, out);
            in.close();
            out.close();
        } catch (Exception e) {
            // TODO Auto-generated method stub
            e.printStackTrace();
        }

        return size;
    }

    private static class PartDescription {
        public final Part part;
        public final long messageId;
        public final long parentId;
        public final int order;

        PartDescription(Part part, long messageId, long parentId, int order) {
            this.part = part;
            this.messageId = messageId;
            this.parentId = parentId;
            this.order = order;
        }
    }

}
