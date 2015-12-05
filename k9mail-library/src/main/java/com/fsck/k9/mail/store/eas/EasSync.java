package com.fsck.k9.mail.store.eas;


import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.store.eas.adapter.EmailSyncParser;
import com.fsck.k9.mail.store.eas.adapter.Parser.EmptyStreamException;
import com.fsck.k9.mail.store.eas.adapter.Serializer;
import com.fsck.k9.mail.store.eas.adapter.Tags;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import com.squareup.okhttp.RequestBody;


/**
 * Performs an Exchange Sync operation for one {@link Mailbox}.
 * TODO: For now, only handles upsync.
 * TODO: Handle multiple folders in one request. Not sure if parser can handle it yet.
 */
public class EasSync extends EasOperation {
    public final static int RESULT_OK = 1;


    private final Mailbox mailbox;
    private final List<MessageStateChange> stateChanges;
    private final EmailSyncCallback callback;
    private Map<String, Integer> messageUpdateStatus = new HashMap<String, Integer>();


    public EasSync(Context context, Account account, Mailbox mailbox, List<MessageStateChange> changes,
            EmailSyncCallback callback) {
        super(context, account);

        this.mailbox = mailbox;
        this.stateChanges = changes;
        this.callback = callback;
    }

    @Override
    public int performOperation() {
        String mailboxSyncKey = mailbox.mSyncKey;
        if (TextUtils.isEmpty(mailboxSyncKey) || mailboxSyncKey.equals("0")) {
            throw new IllegalStateException("Tried to sync mailbox " + mailbox.mServerId + " with invalid " +
                    "mailbox sync key");
        }

        return super.performOperation();
    }

    @Override
    protected String getCommand() {
        return "Sync";
    }

    @Override
    protected RequestBody getRequestBody() throws IOException {
        Serializer serializer = new Serializer();

        serializer.start(Tags.SYNC_SYNC);
        serializer.start(Tags.SYNC_COLLECTIONS);
        addOneCollectionToRequest(serializer, Mailbox.TYPE_MAIL, stateChanges);
        serializer.end().end().done();

        return makeRequestBody(serializer);
    }

    private void addOneCollectionToRequest(Serializer serializer, int collectionType,
            List<MessageStateChange> stateChanges) throws IOException {

        boolean isFlaggedFlagNotSupported = getProtocolVersion() < Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE;
        String mailboxSyncKey = mailbox.mSyncKey;
        String mailboxServerId = mailbox.mServerId;

        serializer.start(Tags.SYNC_COLLECTION);
        if (getProtocolVersion() < Eas.SUPPORTED_PROTOCOL_EX2007_SP1_DOUBLE) {
            serializer.data(Tags.SYNC_CLASS, Eas.getFolderClass(collectionType));
        }
        serializer.data(Tags.SYNC_SYNC_KEY, mailboxSyncKey);
        serializer.data(Tags.SYNC_COLLECTION_ID, mailboxServerId);
        if (getProtocolVersion() >= Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE) {
            // Exchange 2003 doesn't understand the concept of setting this flag to false. The
            // documentation indicates that its presence alone, with no value, requests a two-way
            // sync.
            // TODO: handle downsync here so we don't need this at all
            serializer.data(Tags.SYNC_GET_CHANGES, "0");
        }

        serializer.start(Tags.SYNC_COMMANDS);
        for (MessageStateChange change : stateChanges) {
            if (isFlaggedFlagNotSupported && change.isSingleChange(Flag.FLAGGED)) {
                continue;
            }

            serializer.start(Tags.SYNC_CHANGE);
            serializer.data(Tags.SYNC_SERVER_ID, change.getServerId());
            serializer.start(Tags.SYNC_APPLICATION_DATA);
            if (change.hasFlagChanged(Flag.SEEN)) {
                serializer.data(Tags.EMAIL_READ, change.getFlagState(Flag.SEEN) ? "1" : "0");
            }
            if (change.hasFlagChanged(Flag.FLAGGED)) {
                // "Flag" is a relatively complex concept in EAS 12.0 and above.  It is not only
                // the boolean "favorite" that we think of in Gmail, but it also represents a
                // follow up action, which can include a subject, start and due dates, and even
                // recurrences.  We don't support any of this as yet, but EAS 12.0 and higher
                // require that a flag contain a status, a type, and four date fields, two each
                // for start date and end (due) date.
                boolean newFlagFavorite = change.getFlagState(Flag.FLAGGED);
                if (newFlagFavorite) {
                    // Status 2 = set flag
                    serializer.start(Tags.EMAIL_FLAG).data(Tags.EMAIL_FLAG_STATUS, "2");
                    // "FollowUp" is the standard type
                    serializer.data(Tags.EMAIL_FLAG_TYPE, "FollowUp");
                    final long now = System.currentTimeMillis();
                    final Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("GMT"));
                    calendar.setTimeInMillis(now);
                    // Flags are required to have a start date and end date (duplicated)
                    // First, we'll set the current date/time in GMT as the start time
                    String utc = formatDateTime(calendar);
                    serializer.data(Tags.TASK_START_DATE, utc).data(Tags.TASK_UTC_START_DATE, utc);
                    // And then we'll use one week from today for completion date
                    calendar.setTimeInMillis(now + DateUtils.WEEK_IN_MILLIS);
                    utc = formatDateTime(calendar);
                    serializer.data(Tags.TASK_DUE_DATE, utc).data(Tags.TASK_UTC_DUE_DATE, utc);
                    serializer.end();
                } else {
                    serializer.tag(Tags.EMAIL_FLAG);
                }
            }
            serializer.end().end();  // SYNC_APPLICATION_DATA, SYNC_CHANGE
        }
        serializer.end().end();  // SYNC_COMMANDS, SYNC_COLLECTION
    }

    @Override
    protected int handleResponse(EasResponse response) throws IOException, CommandStatusException {
        try {
            EmailSyncParser parser = new EmailSyncParser(response.getInputStream(), mailbox.mServerId, callback);
            parser.parse();
            messageUpdateStatus = parser.getMessageStatuses();
        } catch (EmptyStreamException e) {
            // This indicates a compressed response which was empty, which is OK.
        } catch (FolderSyncRequiredException e) {
            return EasSyncBase.RESULT_FOLDER_SYNC_REQUIRED;
        }

        return RESULT_OK;
    }

    public Map<String, Integer> getMessageUpdateStatus() {
        return messageUpdateStatus;
    }

    /**
     * Create date/time in RFC8601 format.
     * <p/>
     * Oddly enough, for calendar date/time, Microsoft uses a different format that excludes the punctuation.
     */
    private static String formatDateTime(Calendar calendar) {
        //YYYY-MM-DDTHH:MM:SS.MSSZ
        return String.valueOf(calendar.get(Calendar.YEAR)) + '-' +
                String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1) + '-' +
                String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH)) + 'T' +
                String.format(Locale.US, "%02d", calendar.get(Calendar.HOUR_OF_DAY)) + ':' +
                String.format(Locale.US, "%02d", calendar.get(Calendar.MINUTE)) + ':' +
                String.format(Locale.US, "%02d", calendar.get(Calendar.SECOND)) + ".000Z";
    }
}
