package com.fsck.k9.message;

import java.io.InputStream;

/**
 * Interface for a factory to create {@link Message} and associated objects (e.g.
 * {@link Metadata}).
 *
 * <p>
 * By using this interface with remote stores we decouple them from our own {@link Message}
 * implemenation. Hopefully leading to third-party applications being able to integrate our remote
 * stores without much hassle.
 * </p>
 */
public interface MessageFactory {

    /**
     * Create a {@code Metadata} instance.
     *
     * @return A concrete {@link Metadata} instance.
     */
    public Metadata createMetadata();

    /**
     * Create a {@code Message} instance.
     *
     * @return A concrete {@link Message} instance.
     */
    public Message createMessage();

    /**
     * Create a {@code Message} object from the provided data.
     *
     * @param in
     *         An {@link InputStream} containing the message data. Never {@code null}.
     *
     * @return A concrete {@link Message} instance.
     */
    public Message createMessage(InputStream in);

    /**
     * Create a {@code Part} instance.
     *
     * @return A concrete {@link Part} instance.
     */
    public Part createPart();

    /**
     * Create a {@code Multipart} instance.
     *
     * @return A concrete {@link Multipart} instance.
     */
    public Multipart createMultipart();

    /**
     * Create a {@code Body} instance from {@code in} that represents the body of {@code part}.
     *
     * @param part
     *         The {@link Part} object the {@link Body} to be created belongs to. Never
     *         {@code null}.
     * @param in
     *         An {@link InputStream} containing the body data. Never {@code null}.
     *
     * @return A concrete {@link Body} instance.
     */
    public Body createBody(Part part, InputStream in);

    /**
     * Create a {@code Body} instance from the string {@code text}.
     *
     * @param part
     *         The {@link Part} object the {@link Body} to be created belongs to. Never
     *         {@code null}.
     * @param text
     *         A string containing the body data. Never {@code null}.
     *
     * @return A concrete {@link Body} instance.
     */
    public Body createBody(Part part, String text);
}
