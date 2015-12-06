package com.fsck.k9.mail.store.eas;


import java.io.IOException;

import android.content.Context;
import android.text.format.DateUtils;

import com.fsck.k9.mail.store.eas.adapter.AbstractSyncParser;
import com.fsck.k9.mail.store.eas.adapter.Parser;
import com.fsck.k9.mail.store.eas.adapter.Serializer;
import com.fsck.k9.mail.store.eas.adapter.Tags;
import com.squareup.okhttp.RequestBody;


/**
 * Performs an EAS sync operation for one folder (excluding mail upsync).
 * TODO: Merge with EasChangeFlag
 */
public class EasSyncBase extends EasOperation {

    private static final String TAG = Eas.LOG_TAG;

    public static final int RESULT_DONE = 0;
    public static final int RESULT_MORE_AVAILABLE = 1;
    public static final int RESULT_FOLDER_SYNC_REQUIRED = RESULT_OP_SPECIFIC_ERROR_RESULT;

    private boolean mInitialSync;
    private final Mailbox mMailbox;
    private EasSyncCollectionTypeBase mCollectionTypeHandler;

    private int mNumWindows;

    // TODO: Convert to accountId when ready to convert to EasService.
    public EasSyncBase(final Context context, final Account account, final Mailbox mailbox,
            EasSyncCollectionTypeBase collectionTypeHandler) {
        super(context, account);
        mMailbox = mailbox;
        mCollectionTypeHandler = collectionTypeHandler;
    }

    /**
     * Get the sync key for this mailbox.
     * @return The sync key for the object being synced. "0" means this is the first sync. If
     *      there is an error in getting the sync key, this function returns null.
     */
    protected String getSyncKey() {
        if (mMailbox == null) {
            return null;
        }
        if (mMailbox.mSyncKey == null) {
            mMailbox.mSyncKey = "0";
        }
        return mMailbox.mSyncKey;
    }

    @Override
    protected String getCommand() {
        return "Sync";
    }

    @Override
    public boolean init() {
//        // Set up traffic stats bookkeeping.
//        final int trafficFlags = TrafficFlags.getSyncFlags(mContext, mAccount);
//        TrafficStats.setThreadStatsTag(trafficFlags | mCollectionTypeHandler.getTrafficFlag());
        return true;
    }

    @Override
    protected RequestBody getRequestBody() throws IOException {
        final String className = Eas.getFolderClass(mMailbox.mType);
        final String syncKey = getSyncKey();
        LogUtils.d(TAG, "Syncing account %d mailbox %d (class %s) with syncKey %s", mAccount.mId,
                mMailbox.mId, className, syncKey);
        mInitialSync = Mailbox.isInitialSyncKey(syncKey);
        final Serializer s = new Serializer();
        s.start(Tags.SYNC_SYNC);
        s.start(Tags.SYNC_COLLECTIONS);
        s.start(Tags.SYNC_COLLECTION);
        // The "Class" element is removed in EAS 12.1 and later versions
        if (getProtocolVersion() < Eas.SUPPORTED_PROTOCOL_EX2007_SP1_DOUBLE) {
            s.data(Tags.SYNC_CLASS, className);
        }
        s.data(Tags.SYNC_SYNC_KEY, syncKey);
        s.data(Tags.SYNC_COLLECTION_ID, mMailbox.mServerId);
        mCollectionTypeHandler.setSyncOptions(mContext, s, getProtocolVersion(), mAccount, mMailbox,
                mInitialSync, mNumWindows);
        s.end().end().end().done();

        return makeRequestBody(s);
    }

    @Override
    protected int handleResponse(final EasResponse response)
            throws IOException, CommandStatusException {
        try {
            final AbstractSyncParser parser = mCollectionTypeHandler.getParser(mContext, mAccount,
                    mMailbox, response.getInputStream());
            final boolean moreAvailable = parser.parse();

            mCollectionTypeHandler.onParsingComplete();

            if (moreAvailable) {
                return RESULT_MORE_AVAILABLE;
            }
        } catch (final Parser.EmptyStreamException e) {
            // This indicates a compressed response which was empty, which is OK.
        } catch (FolderSyncRequiredException e) {
            return RESULT_FOLDER_SYNC_REQUIRED;
        }
        return RESULT_DONE;
    }

    @Override
    public int performOperation() {
        int result = RESULT_MORE_AVAILABLE;
        mNumWindows = 1;
        final String key = getSyncKey();
        while (result == RESULT_MORE_AVAILABLE) {
            result = super.performOperation();
            if (result == RESULT_MORE_AVAILABLE || result == RESULT_DONE) {
                mCollectionTypeHandler.cleanup(mContext, mAccount);
            }
            // TODO: Clear pending request queue.
            final String newKey = getSyncKey();
            if (result == RESULT_MORE_AVAILABLE && key.equals(newKey)) {
                LogUtils.e(TAG,
                        "Server has more data but we have the same key: %s numWindows: %d",
                        key, mNumWindows);
                mNumWindows++;
            } else {
                mNumWindows = 1;
            }
        }
        return result;
    }

    @Override
    protected long getTimeout() {
        if (mInitialSync) {
            return 120 * DateUtils.SECOND_IN_MILLIS;
        }
        return super.getTimeout();
    }
}
