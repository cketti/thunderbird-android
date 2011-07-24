package com.fsck.k9.message;

import com.fsck.k9.mail.Flag;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stores data about a {@link Message}.
 *
 * @see MessageContainer
 */
public class Metadata {
    private String mServerId;
    private Date mDate;
    private Set<Flag> mFlags = new HashSet<Flag>();
    private Map<Part, Map<String, String>> mAttributes = new HashMap<Part, Map<String, String>>();;

    /**
     * Get the ID that identifies the message in a remote store.
     *
     * @return The ID of the message on the server. {@code null} if the ID is unknown or no such ID
     *         exists.
     */
    public String getServerId() {
        return mServerId;
    }

    /**
     * Set the ID that identifies the message in a remote store.
     *
     * @param serverId
     *         The ID of the message on the server. May be {@code null}.
     */
    public void setServerId(String serverId) {
        mServerId = serverId;
    }

    /**
     * Get the flags of the associated message.
     *
     * @return The {@link Flag}s of the associated message.
     */
    public Set<Flag> getFlags() {
        return new HashSet<Flag>(mFlags);
    }

    /**
     * Find out whether or not a certain flag is set.
     *
     * @param flag
     *         The {@link Flag} to check for.
     *
     * @return {@code true} if the flag is set; {@code false} otherwise.
     */
    public boolean isFlagSet(Flag flag) {
        return mFlags.contains(flag);
    }

    /**
     * Set or remove a certain flag.
     *
     * @param flag
     *         The {@link Flag} to set or remove.
     * @param newState
     *         {@code true} if the flag should be set; {@code false} otherwise.
     *
     * @return The old state of the flag. {@code true} if set; {@code false} otherwise.
     */
    public boolean setFlag(Flag flag, boolean newState) {
        boolean oldState = mFlags.contains(flag);
        if (newState != oldState) {
            if (newState) {
                mFlags.remove(flag);
            } else {
                mFlags.add(flag);
            }
        }

        return oldState;
    }

    /**
     * Get the message's date.
     *
     * <p>
     * <strong>Note:</strong>
     * If no date was set, the current date is stored in this {@link Metadata} object and used until
     * the date is changed using {@link #setDate(Date)}.
     * </p>
     *
     * @return The message's server date. Never {@code null}.
     */
    public Date getDate() {
        if (mDate == null) {
            mDate = new Date();
        }
        return mDate;
    }

    /**
     * Set the message's date.
     *
     * @param date
     *         The date of the message. Never {@code null}.
     */
    public void setDate(Date date) {
        mDate = date;
    }

    /**
     * Set an attribute consisting of a key/value-pair for a {@code Part}.
     *
     * @param part
     *         The {@link Part} object to set the attribute for. Never {@code null}.
     * @param key
     *         The attribute key. Never {@code null}.
     * @param value
     *         The attribute value. Use {@code null} to remove the attribute.
     */
    public void setAttribute(Part part, String key, String value) {
        Map<String, String> partAttributes = mAttributes.get(part);
        if (partAttributes == null) {
            partAttributes = new HashMap<String, String>();
            mAttributes.put(part, partAttributes);
        }

        partAttributes.put(key, value);
    }

    /**
     * Get attributes for a {@code Part}.
     *
     * @param part
     *         The {@link Part} object to get the attributes for. Never {@code null}.
     *
     * @return A map containing all attributes. May be {@code null}.
     */
    public Map<String, String> getAttributes(Part part) {
        return mAttributes.get(part);
    }
}
