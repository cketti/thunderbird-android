package com.fsck.k9.mail.store.eas;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;

import com.fsck.k9.mail.store.eas.adapter.AbstractSyncParser;
import com.fsck.k9.mail.store.eas.adapter.Serializer;
import com.fsck.k9.mail.store.eas.adapter.Tags;


/**
 * Abstract base class that handles the details of syncing a specific collection type.
 * These details include:
 * - Forming the request options. Contacts, Calendar, and Mail set this up differently.
 * - Getting the appropriate parser for this collection type.
 */
public abstract class EasSyncCollectionTypeBase {

    public static final int MAX_WINDOW_SIZE = 512;

//    /**
//     * Get the flag for traffic bookkeeping for this sync type.
//     * @return The appropriate value from {@link com.android.emailcommon.TrafficFlags} for this
//     *         sync.
//     */
//    public abstract int getTrafficFlag();

    /**
     * Write the contents of a Collection node in an EAS sync request appropriate for our mailbox.
     * See http://msdn.microsoft.com/en-us/library/gg650891(v=exchg.80).aspx for documentation on
     * the contents of this sync request element.
     * @param context
     * @param s The {@link Serializer} for the current request. This should be within a
     *          {@link com.android.exchange.adapter.Tags#SYNC_COLLECTION} element.
     * @param protocolVersion
     * @param account
     * @param mailbox
     * @param isInitialSync
     * @param numWindows
     * @throws IOException
     */
    public abstract void setSyncOptions(final Context context, final Serializer s,
            final double protocolVersion, final Account account, final Mailbox mailbox,
            final boolean isInitialSync, final int numWindows) throws IOException;

    /**
     * Create a parser for the current response data, appropriate for this collection type.
     * @param context
     * @param account
     * @param mailbox
     * @param is The {@link InputStream} for the server response we're processing.
     * @return An appropriate parser for this input.
     * @throws IOException
     */
    public abstract AbstractSyncParser getParser(final Context context, final Account account,
            final Mailbox mailbox, final InputStream is) throws IOException;

    /**
     * After every successful sync iteration, this function gets called to cleanup any state to
     * match the sync result (e.g., to clean up an external ContentProvider for PIM data).
     * @param context
     * @param account
     */
    public void cleanup(final Context context, final Account account) {}
}
