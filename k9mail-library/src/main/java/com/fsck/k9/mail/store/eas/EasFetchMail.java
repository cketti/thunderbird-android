package com.fsck.k9.mail.store.eas;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.text.TextUtils;

import com.fsck.k9.mail.store.eas.adapter.EmailSyncParser;
import com.fsck.k9.mail.store.eas.adapter.Parser.EmptyStreamException;
import com.fsck.k9.mail.store.eas.adapter.Serializer;
import com.fsck.k9.mail.store.eas.adapter.Tags;
import com.fsck.k9.mail.store.eas.callback.EmailSyncCallback;
import com.squareup.okhttp.RequestBody;


/**
 * Fetch message contents
 */
public class EasFetchMail extends EasOperation {
    public final static int RESULT_OK = 1;


    private final Mailbox mailbox;
    private final EmailSyncCallback callback;
    private final List<String> serverIds;
    private Map<String, Integer> messageUpdateStatus = new HashMap<String, Integer>();


    public EasFetchMail(Context context, Account account, Mailbox mailbox, List<String> serverIds,
            EmailSyncCallback callback) {
        super(context, account);

        this.mailbox = mailbox;
        this.serverIds = serverIds;
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
        addOneCollectionToRequest(serializer, Mailbox.TYPE_MAIL, serverIds);
        serializer.end().end().done();

        return makeRequestBody(serializer);
    }

    private void addOneCollectionToRequest(Serializer serializer, int collectionType,
            List<String> serverIds) throws IOException {

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

        serializer.start(Tags.SYNC_OPTIONS);
        serializer.data(Tags.SYNC_MIME_SUPPORT, Eas.MIME_BODY_PREFERENCE_MIME);
        if (getProtocolVersion() >= Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE) {
            serializer.start(Tags.BASE_BODY_PREFERENCE);
            serializer.data(Tags.BASE_TYPE, Eas.BODY_PREFERENCE_MIME);
            serializer.end();
        }
        serializer.end();

        serializer.start(Tags.SYNC_COMMANDS);
        for (String serverId : serverIds) {
            serializer.start(Tags.SYNC_FETCH);
            serializer.data(Tags.SYNC_SERVER_ID, serverId);
            serializer.end();
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
}
