package com.fsck.k9.provider.message;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.internet.MimeUtility;
import com.fsck.k9.message.Body;
import com.fsck.k9.message.Folder;
import com.fsck.k9.message.Message;
import com.fsck.k9.message.MessageContainer;
import com.fsck.k9.message.Metadata;
import com.fsck.k9.message.Multipart;
import com.fsck.k9.message.Part;
import com.fsck.k9.message.Body.StreamType;
import com.fsck.k9.provider.EmailProvider;
import com.fsck.k9.provider.EmailProviderConstants;
import com.fsck.k9.provider.EmailProviderConstants.FolderColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessageColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessagePartAttributeColumns;
import com.fsck.k9.provider.EmailProviderConstants.MessagePartColumns;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.parser.ContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeEntityConfig;
import org.apache.james.mime4j.stream.RawField;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
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

    public static final String[] MESSAGE_PART_ATTRIBUTE_PROJECTION = {
        MessagePartAttributeColumns.ID,
        MessagePartAttributeColumns.MESSAGE_PART_ID,
        MessagePartAttributeColumns.KEY,
        MessagePartAttributeColumns.VALUE
    };

    public static final String[] FOLDER_PROJECTION = {
        FolderColumns.ID,
        FolderColumns.NAME,
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

    /**
     * Restore a message along with it's metadata from {@link EmailProvider}.
     *
     * @param context
     *         A {@link Context} instance.
     * @param accountUuid
     *         The UUID of the account the message should be loaded from.
     * @param messageId
     *         The ID of the message that is to be loaded.
     * @return A {@link MessageContainer} instance describing the loaded message. May be
     *         {@code null} in case the message couldn't be found.
     */
    public static MessageContainer restoreMessageWithId(Context context, String accountUuid,
                long messageId) {

        EmailProviderMetadata metadata = new EmailProviderMetadata(accountUuid);
        metadata.setId(messageId);
        Message message = new EmailProviderMessage();

        MessageContainer container = new MessageContainer(metadata, message);

        // Load data from the "messages" table
        loadMetadata(context, metadata);

        // Load data from the "message_parts" table
        loadMessageParts(context, container);

        // Load data from the "message_part_attributes" table
        loadMessagePartAttributes(context, metadata);

        return container;
    }

    private static void loadMetadata(Context context, EmailProviderMetadata metadata) {
        ContentResolver resolver = context.getContentResolver();
        Uri messageUri = EmailProviderConstants.Message.getContentUri(metadata.getAccountUuid());
        Uri uri = ContentUris.withAppendedId(messageUri, metadata.getId());
        Cursor cursor = resolver.query(uri, MESSAGE_PROJECTION, null, null, null);

        try {
            //TODO: check return value
            cursor.moveToFirst();
            long folderId = cursor.getLong(cursor.getColumnIndex(MessageColumns.FOLDER_ID));
            String uid = cursor.getString(cursor.getColumnIndex(MessageColumns.UID));
            boolean localOnly = (cursor.getInt(cursor.getColumnIndex(MessageColumns.LOCAL_ONLY)) == 1);
            boolean deleted = (cursor.getInt(cursor.getColumnIndex(MessageColumns.DELETED)) == 1);
            //boolean notified = (cursor.getInt(cursor.getColumnIndex(MessageColumns.NOTIFIED)) == 1);
            //long date = cursor.getLong(cursor.getColumnIndex(MessageColumns.DATE));
            long internalDate = cursor.getLong(cursor.getColumnIndex(MessageColumns.INTERNAL_DATE));
            boolean seen = (cursor.getInt(cursor.getColumnIndex(MessageColumns.SEEN)) == 1);
            boolean flagged = (cursor.getInt(cursor.getColumnIndex(MessageColumns.FLAGGED)) == 1);
            boolean answered = (cursor.getInt(cursor.getColumnIndex(MessageColumns.ANSWERED)) == 1);
            //boolean forwarded = (cursor.getInt(cursor.getColumnIndex(MessageColumns.FORWARDED)) == 1);
            boolean destroyed = (cursor.getInt(cursor.getColumnIndex(MessageColumns.DESTROYED)) == 1);
            boolean sendFailed = (cursor.getInt(cursor.getColumnIndex(MessageColumns.SEND_FAILED)) == 1);
            boolean sendInProgress = (cursor.getInt(cursor.getColumnIndex(MessageColumns.SEND_IN_PROGRESS)) == 1);
            boolean downloadedFull = (cursor.getInt(cursor.getColumnIndex(MessageColumns.DOWNLOADED_FULL)) == 1);
            boolean downloadedPartial = (cursor.getInt(cursor.getColumnIndex(MessageColumns.DOWNLOADED_PARTIAL)) == 1);
            boolean remoteCopyStarted = (cursor.getInt(cursor.getColumnIndex(MessageColumns.REMOTE_COPY_STARTED)) == 1);
            boolean gotAllHeaders = (cursor.getInt(cursor.getColumnIndex(MessageColumns.GOT_ALL_HEADERS)) == 1);

            metadata.setFolderId(folderId);
            metadata.setServerId(uid);
            metadata.setLocalOnly(localOnly);
            metadata.setDate(new Date(internalDate));

            metadata.setFlag(Flag.DELETED, deleted);
            metadata.setFlag(Flag.SEEN, seen);
            metadata.setFlag(Flag.FLAGGED, flagged);
            metadata.setFlag(Flag.ANSWERED, answered);
            //metadata.setFlag(Flag.FORWARDED, forwarded);
            metadata.setFlag(Flag.X_DESTROYED, destroyed);
            metadata.setFlag(Flag.X_SEND_FAILED, sendFailed);
            metadata.setFlag(Flag.X_SEND_IN_PROGRESS, sendInProgress);
            metadata.setFlag(Flag.X_DOWNLOADED_FULL, downloadedFull);
            metadata.setFlag(Flag.X_DOWNLOADED_PARTIAL, downloadedPartial);
            metadata.setFlag(Flag.X_REMOTE_COPY_STARTED, remoteCopyStarted);
            metadata.setFlag(Flag.X_GOT_ALL_HEADERS, gotAllHeaders);

        } finally {
            cursor.close();
        }
    }

    private static void loadMessageParts(Context context, MessageContainer container) {
        ContentResolver resolver = context.getContentResolver();
        EmailProviderMetadata metadata = (EmailProviderMetadata) container.getMetadata();
        Message message = container.getMessage();
        String accountUuid = metadata.getAccountUuid();


        Uri partsUri = ContentUris.withAppendedId(
                EmailProviderConstants.MessageParts.getContentUri(accountUuid), metadata.getId());

        Cursor cursor = resolver.query(partsUri, PART_PROJECTION, null, null, null);

        try {
            while (cursor.moveToNext()) {
                int partId = cursor.getInt(cursor.getColumnIndex(MessagePartColumns.ID));
                String mimeType = cursor.getString(cursor.getColumnIndex(MessagePartColumns.MIME_TYPE));
                int parentPartId = cursor.getInt(cursor.getColumnIndex(MessagePartColumns.PARENT));
                String header = cursor.getString(cursor.getColumnIndex(MessagePartColumns.HEADER));

                Part parent;
                Part current;
                if (parentPartId == 0)
                {
                    parent = message;
                }
                else
                {
                    parent = metadata.getPart(parentPartId);
                }

                Body parentBody = parent.getBody();
                if (parentBody instanceof Multipart)
                {
                    current = new EmailProviderBodyPart();
                    ((Multipart) parentBody).addPart(current);
                }
                else if (parentBody instanceof Message)
                {
                    current = (Message) parentBody;
                }
                else
                {
                    current = parent;
                }

                Multipart multipart = null;
                if (mimeType.startsWith("multipart/"))
                {
                    multipart = new EmailProviderMultipart();
                    current.setBody(multipart);
                }
                else if (mimeType.equalsIgnoreCase("message/rfc822"))
                {
                    Message innerMessage = new EmailProviderMessage();
                    current.setBody(innerMessage);
                }
                else
                {
                    Uri bodyUri = ContentUris.withAppendedId(
                            EmailProviderConstants.MessagePart.getContentUri(metadata.getAccountUuid()),
                            partId);
                    Body body = new EmailProviderBody(context, bodyUri);
                    current.setBody(body);
                }

                metadata.updateMapping(current, partId);

                parseHeader(current, header);

                if (multipart != null) {
                    String boundary = MimeUtility.getBoundary(current);
                    multipart.setBoundary(boundary);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private static void parseHeader(final Part part, String header) {
        MimeEntityConfig parserConfig = new MimeEntityConfig();
        parserConfig.setMaxHeaderLen(-1);
        parserConfig.setMaxLineLen(-1);
        parserConfig.setStrictParsing(false);
        MimeStreamParser parser = new MimeStreamParser(parserConfig);
        parser.setContentHandler(new ContentHandler() {
            @Override
            public void field(RawField field) throws MimeException
            {
                part.getHeader().addEncoded(field.getName(), field.getBody());
            }

            @Override public void startMultipart(BodyDescriptor bd) throws MimeException { /* unused */ }
            @Override public void startMessage() throws MimeException { /* unused */ }
            @Override public void startHeader() throws MimeException { /* unused */ }
            @Override public void startBodyPart() throws MimeException { /* unused */ }
            @Override public void raw(InputStream is) throws MimeException, IOException { /* unused */ }
            @Override public void preamble(InputStream is) throws MimeException, IOException { /* unused */ }
            @Override public void epilogue(InputStream arg0) throws MimeException, IOException { /* unused */ }
            @Override public void endMultipart() throws MimeException { /* unused */ }
            @Override public void endMessage() throws MimeException { /* unused */ }
            @Override public void endHeader() throws MimeException { /* unused */ }
            @Override public void endBodyPart() throws MimeException { /* unused */ }
            @Override public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException { /* unused */ }
        });

        try
        {
            parser.parse(new ByteArrayInputStream(header.getBytes()));
        }
        catch (Exception e)
        {
            //FIXME
            e.printStackTrace();
        }
    }

    private static void loadMessagePartAttributes(Context context,
                                                  EmailProviderMetadata metadata) {
        ContentResolver resolver = context.getContentResolver();

        // Get attributes for all message parts
        Uri attributesUri = EmailProviderConstants.MessagePartAttibute.getContentUri(metadata.getAccountUuid());

        Long[] partIds = metadata.getPartIds();

        StringBuilder sb = new StringBuilder(64);
        sb.append(MessagePartAttributeColumns.MESSAGE_PART_ID);
        sb.append(" IN (");
        sb.append('?');
        String[] attributeSelectionArgs = new String[partIds.length];
        for (int i = 0, end = partIds.length; i < end; i++) {
            sb.append(",?");
            attributeSelectionArgs[i] = partIds[i].toString();
        }
        sb.append(')');
        String attributeSelection = sb.toString();

        Cursor cursor = resolver.query(attributesUri, MESSAGE_PART_ATTRIBUTE_PROJECTION, attributeSelection, attributeSelectionArgs, null);
        try {
            while (cursor.moveToNext()) {
                long partId = cursor.getLong(cursor.getColumnIndex(MessagePartAttributeColumns.MESSAGE_PART_ID));
                String key = cursor.getString(cursor.getColumnIndex(MessagePartAttributeColumns.KEY));
                String value = cursor.getString(cursor.getColumnIndex(MessagePartAttributeColumns.VALUE));

                metadata.setAttribute(partId, key, value);
            }
        } finally {
            cursor.close();
        }
    }

    public static List<EmailProviderFolder> getFolders(Context context, String accountUuid) {
        ContentResolver resolver = context.getContentResolver();

        Uri uri = EmailProviderConstants.Folder.getContentUri(accountUuid);

        List<EmailProviderFolder> folders = new ArrayList<EmailProviderFolder>();
        Cursor cursor = resolver.query(uri, FOLDER_PROJECTION, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(FolderColumns.ID));
                String name = cursor.getString(cursor.getColumnIndex(FolderColumns.NAME));

                folders.add(new EmailProviderFolder(name, id));
            }
        } finally {
            cursor.close();
        }

        return folders;
    }

    public static void createFolders(Context context, String accountUuid,
            List<EmailProviderFolder> folders, int visibleLimit) {

        ContentResolver resolver = context.getContentResolver();

        Uri uri = EmailProviderConstants.Folder.getContentUri(accountUuid);

        ContentValues[] values = new ContentValues[folders.size()];
        int i = 0;
        for (Folder folder : folders) {
            ContentValues cv = new ContentValues();
            cv.put(FolderColumns.NAME, folder.getInternalName());
            cv.put(FolderColumns.VISIBLE_LIMIT, visibleLimit);
            values[i++] = cv;
        }

        resolver.bulkInsert(uri, values);
    }

    public static void deleteFolder(Context context, String accountUuid, EmailProviderFolder folder) {
        ContentResolver resolver = context.getContentResolver();

        Uri uri = ContentUris.withAppendedId(
                EmailProviderConstants.Folder.getContentUri(accountUuid), folder.getId());

        resolver.delete(uri, null, null);
    }
}
