package com.fsck.k9.message;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents a part of a MIME message. Consisting of a {@link Header} and a {@link Body}.
 */
public interface Part {

    /**
     * Get the header of this message part.
     *
     * @return The {@link Header} object that represents the header for this {@link Part}. Never
     *         {@code null}.
     */
    public Header getHeader();

    /**
     * Set the header of this message part.
     *
     * @param header
     *         The {@link Header} object that represents the header for this {@link Part}. Never
     *         {@code null}.
     */
    public void setHeader(Header header);

    /**
     * Get the body of this message part.
     *
     * @return The {@link Body} object that represents the body of this {@link Part}. Never
     *         {@code null}.
     */
    public Body getBody();

    /**
     * Set the body of this message part.
     *
     * @param body
     *         The {@link Body} object that represents the body of this {@link Part}. Never
     *         {@code null}.
     */
    public void setBody(Body body);

    /**
     * Write the contents of this part in RFC 822 compatible form to the specified stream.
     *
     * @param out
     *         The {@link OutputStream} this {@link Part} should be written to.
     *
     * @throws IOException
     */
    public void writeTo(OutputStream out) throws IOException;
}
