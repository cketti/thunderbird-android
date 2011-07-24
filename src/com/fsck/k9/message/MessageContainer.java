package com.fsck.k9.message;

/**
 * A container to hold a {@link Message} object along with a {@link Metadata} object that is
 * storing data about that message.
 *
 * <p>
 * By dividing the contents of a RFC 822/MIME message and the metadata we can avoid creating
 * {@code Message} objects if we are only interested in metadata. This overcomes one of the major
 * drawbacks of {@link com.fsck.k9.mail.Message} when dealing with remote stores.
 * </p>
 */
public class MessageContainer {
    private Metadata mMetadata;
    private Message mMessage;

    /**
     * Create a {@link MessageContainer} object containing only metadata.
     *
     * @param metadata
     *         A {@link Metadata} object containing data about a message. Never {@code null}.
     */
    public MessageContainer(Metadata metadata) {
        this(metadata, null);
    }

    /**
     * Create a {@link MessageContainer} object containing a message and metadata.
     *
     * @param metadata
     *         A {@link Metadata} object containing data about a message. Never {@code null}.
     * @param message
     *         The associated {@link Message} object representing the message contents. May be
     *         {@code null}.
     */
    public MessageContainer(Metadata metadata, Message message) {
        mMetadata = metadata;
        mMessage = message;
    }

    /**
     * Get the {@code Metadata} object containing data about the message.
     *
     * @return The {@link Metadata} object.
     */
    public Metadata getMetadata() {
        return mMetadata;
    }

    /**
     * Set the {@code Metadata} object containing data about the message.
     *
     * @param metadata
     *         The {@link Metadata} object.
     */
    public void setMetadata(Metadata metadata) {
        mMetadata = metadata;
    }

    /**
     * Get the {@code Message} object containing the message contents.
     *
     * @return The {@link Message} object.
     */
    public Message getMessage() {
        return mMessage;
    }

    /**
     * Set the {@code Message} object containing the message contents.
     *
     * @param message
     *         The {@link Message} object.
     */
    public void setMessage(Message message) {
        mMessage = message;
    }
}
