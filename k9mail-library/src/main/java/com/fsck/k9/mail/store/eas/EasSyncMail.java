package com.fsck.k9.mail.store.eas;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fsck.k9.mail.store.eas.adapter.AbstractSyncParser;
import com.fsck.k9.mail.store.eas.adapter.EmailSyncParser;
import com.fsck.k9.mail.store.eas.adapter.Serializer;
import com.fsck.k9.mail.store.eas.adapter.Tags;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;


/**
 * Subclass to handle sync details for mail collections.
 */
public class EasSyncMail extends EasSyncCollectionTypeBase {
    private static final int EMAIL_WINDOW_SIZE = 10;


    private final EmailSyncCallback callback;
    private final List<String> messageServerIdsToDelete;
    private final Map<String, Integer> messageDeleteStatus;
    private EmailSyncParser parser;


    public EasSyncMail(EmailSyncCallback callback) {
        this.callback = callback;
        messageServerIdsToDelete = Collections.emptyList();
        messageDeleteStatus = new HashMap<String, Integer>();
    }

    public EasSyncMail(EmailSyncCallback callback, List<String> messageServerIdsToDelete) {
        this.callback = callback;
        this.messageServerIdsToDelete = messageServerIdsToDelete;
        messageDeleteStatus = new HashMap<String, Integer>(messageServerIdsToDelete.size());
    }

//    @Override
//    public int getTrafficFlag() {
//        return TrafficFlags.DATA_EMAIL;
//    }

    @Override
    public void setSyncOptions(final Context context, final Serializer s,
            final double protocolVersion, final Account account, final Mailbox mailbox,
            final boolean isInitialSync, final int numWindows) throws IOException {
        if (isInitialSync) {
            // No special options to set for initial mailbox sync.
            return;
        }

        // Check for messages that aren't fully loaded.
        final ArrayList<String> messagesToFetch = addToFetchRequestList(context, mailbox);
        // The "empty" case is typical; we send a request for changes, and also specify a sync
        // window, body preference type (HTML for EAS 12.0 and later; MIME for EAS 2.5), and
        // truncation
        // If there are fetch requests, we only want the fetches (i.e. no changes from the server)
        // so we turn MIME support off.  Note that we are always using EAS 2.5 if there are fetch
        // requests
        if (messagesToFetch.isEmpty()) {
            // Permanently delete if in trash mailbox
            // In Exchange 2003, deletes-as-moves tag = true; no tag = false
            // In Exchange 2007 and up, deletes-as-moves tag is "0" (false) or "1" (true)
            final boolean isTrashMailbox = !messageServerIdsToDelete.isEmpty();
            if (protocolVersion < Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE) {
                if (!isTrashMailbox) {
                    s.tag(Tags.SYNC_DELETES_AS_MOVES);
                }
            } else {
                s.data(Tags.SYNC_DELETES_AS_MOVES, isTrashMailbox ? "0" : "1");
            }
            s.tag(Tags.SYNC_GET_CHANGES);

            final int windowSize = numWindows * EMAIL_WINDOW_SIZE;
            if (windowSize > MAX_WINDOW_SIZE  + EMAIL_WINDOW_SIZE) {
                throw new IOException("Max window size reached and still no data");
            }
            s.data(Tags.SYNC_WINDOW_SIZE,
                    String.valueOf(windowSize < MAX_WINDOW_SIZE ? windowSize : MAX_WINDOW_SIZE));
            s.start(Tags.SYNC_OPTIONS);
            // Set the lookback appropriately (EAS calls this a "filter")
            s.data(Tags.SYNC_FILTER_TYPE, getEmailFilter(account, mailbox));
            // Set the truncation amount for all classes
            if (protocolVersion >= Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE) {
                s.data(Tags.SYNC_MIME_SUPPORT, Eas.MIME_BODY_PREFERENCE_MIME);
                s.start(Tags.BASE_BODY_PREFERENCE);
                s.data(Tags.BASE_TYPE, Eas.BODY_PREFERENCE_MIME);
                s.data(Tags.BASE_TRUNCATION_SIZE, Eas.EAS12_TRUNCATION_SIZE);
                s.end();
            } else {
                s.data(Tags.SYNC_MIME_SUPPORT, Eas.MIME_BODY_PREFERENCE_MIME);
                s.data(Tags.SYNC_MIME_TRUNCATION, Eas.EAS2_5_TRUNCATION_SIZE);
            }
            s.end();

            if (!messageServerIdsToDelete.isEmpty()) {
                s.start(Tags.SYNC_COMMANDS);
                for (String serverId : messageServerIdsToDelete) {
                    s.start(Tags.SYNC_DELETE);
                    s.data(Tags.SYNC_SERVER_ID, serverId);
                    s.end();
                }
                s.end();
            }
        } else {
            // If we have any messages that are not fully loaded, ask for plain text rather than
            // MIME, to guarantee we'll get usable text body. This also means we should NOT ask for
            // new messages -- we only want data for the message explicitly fetched.
            s.start(Tags.SYNC_OPTIONS);
            s.data(Tags.SYNC_MIME_SUPPORT, Eas.MIME_BODY_PREFERENCE_TEXT);
            s.data(Tags.SYNC_TRUNCATION, Eas.EAS2_5_TRUNCATION_SIZE);
            s.end();

            // Add FETCH commands for messages that need a body (i.e. we didn't find it during our
            // earlier sync; this happens only in EAS 2.5 where the body couldn't be found after
            // parsing the message's MIME data).
            s.start(Tags.SYNC_COMMANDS);
            for (final String serverId : messagesToFetch) {
                s.start(Tags.SYNC_FETCH).data(Tags.SYNC_SERVER_ID, serverId).end();
            }
            s.end();
        }
    }

    @Override
    public AbstractSyncParser getParser(final Context context, final Account account,
            final Mailbox mailbox, final InputStream is) throws IOException {
        String folderServerId = mailbox.mServerId;
        parser = new EmailSyncParser(is, folderServerId, callback);
        return parser;
    }

    @Override
    public void onParsingComplete() {
        messageDeleteStatus.putAll(parser.getMessageStatuses());
        parser = null;
    }

    public Map<String, Integer> getMessageDeleteStatus() {
        return Collections.unmodifiableMap(messageDeleteStatus);
    }

    /**
     * Query the provider for partially loaded messages.
     * @return Server ids for partially loaded messages.
     */
    private ArrayList<String> addToFetchRequestList(final Context context, final Mailbox mailbox) {
        final ArrayList<String> messagesToFetch = new ArrayList<String>();
//        final Cursor c = context.getContentResolver().query(Message.CONTENT_URI,
//                FETCH_REQUEST_PROJECTION,  MessageColumns.FLAG_LOADED + "=" +
//                        Message.FLAG_LOADED_PARTIAL + " AND " +  MessageColumns.MAILBOX_KEY + "=?",
//                new String[] {Long.toString(mailbox.mId)}, null);
//        if (c != null) {
//            try {
//                while (c.moveToNext()) {
//                    messagesToFetch.add(c.getString(FETCH_REQUEST_SERVER_ID));
//                }
//            } finally {
//                c.close();
//            }
//        }
        return messagesToFetch;
    }

    /**
     * Get the sync window for this collection and translate it to EAS's value for that (EAS refers
     * to this as the "filter").
     * @param account The {@link Account} for this sync; its sync window is used if the mailbox
     *                doesn't specify an override.
     * @param mailbox The {@link Mailbox} for this sync.
     * @return The EAS string value for the sync window specified for this mailbox.
     */
    private String getEmailFilter(final Account account, final Mailbox mailbox) {
        return mailbox.syncWindow;
    }
}
