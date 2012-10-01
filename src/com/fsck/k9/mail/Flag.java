
package com.fsck.k9.mail;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * Flags that can be applied to Messages.
 */
public enum Flag implements Parcelable {
    DELETED,
    SEEN,
    ANSWERED,
    FLAGGED,
    DRAFT,
    RECENT,
    FORWARDED,

    /*
     * The following flags are for internal library use only.
     */
    /**
     * Delete and remove from the LocalStore immediately.
     */
    X_DESTROYED,

    /**
     * Sending of an unsent message failed. It will be retried. Used to show status.
     */
    X_SEND_FAILED,

    /**
     * Sending of an unsent message is in progress.
     */
    X_SEND_IN_PROGRESS,

    /**
     * Indicates that a message is fully downloaded from the server and can be viewed normally.
     * This does not include attachments, which are never downloaded fully.
     */
    X_DOWNLOADED_FULL,

    /**
     * Indicates that a message is partially downloaded from the server and can be viewed but
     * more content is available on the server.
     * This does not include attachments, which are never downloaded fully.
     */
    X_DOWNLOADED_PARTIAL,

    /**
     * Indicates that the copy of a message to the Sent folder has started.
     */
    X_REMOTE_COPY_STARTED,

    /**
     * Indicates that all headers of the message have been stored in the
     * database. If this is false, additional headers might be retrieved from
     * the server (if the message is still there).
     */
    X_GOT_ALL_HEADERS;


    /* ********************************************************************
     *  Parcelable
     * ********************************************************************/
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name());
    }

    public static final Parcelable.Creator<Flag> CREATOR = new Parcelable.Creator<Flag>() {

        @Override
        public Flag createFromParcel(Parcel in) {
            return Flag.valueOf(in.readString());
        }

        @Override
        public Flag[] newArray(int size) {
            return new Flag[size];
        }
    };
}
