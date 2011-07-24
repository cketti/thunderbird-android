package com.fsck.k9.message;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Represents the header of a MIME {@link Part} or RFC 822 {@link Message}.
 */
public interface Header {

    /**
     * Returns whether or not this object includes all the header lines.
     *
     * @return {@code true} if this object contains all header lines of the associated
     *         {@link Part}; {@code false} otherwise.
     */
    public boolean isComplete();

    /**
     * Set indicator on whether or not this object contains all header lines.
     *
     * @param complete
     *         {@code true} if this object contains all header lines of the associated
     *         {@link Part}; {@code false} otherwise.
     */
    public void setComplete(boolean complete);

    /**
     * Add an encoded header field.
     *
     * @param name
     *         The name of the header field. Must be an ASCII string and never {@code null}.
     * @param value
     *         The value of the header field as ASCII string. Non-ASCII characters have to be
     *         properly encoded using a valid encoding for the specific header type.
     */
    public void addEncoded(String name, String value);

    /**
     * Get the decoded values of all header fields with the name {@code name}.
     *
     * @param name
     *         The name of the header field. Never {@code null}.
     *
     * @return An array of decoded values as UTF-8 strings.
     */
    public String[] get(String name);

    /**
     * Write the header contents in RFC 822 compatible form to the specified stream.
     *
     * @param out
     *         The {@link OutputStream} this {@link Header} should be written to.
     *
     * @throws IOException
     */
    public void writeTo(OutputStream out) throws IOException;
}
