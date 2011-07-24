package com.fsck.k9.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Represents the body of a MIME {@link Part} or RFC 822 {@link Message}.
 */
public interface Body {

    /**
     * Enum for the types of {@link InputStream} the method {@link #getInputStream(StreamType)}
     * can return.
     */
    public enum StreamType {
        /**
         * Used for a stream with the unmodified body (including the original transport encoding
         * and without any character set conversions, i.e. the raw original data).
         */
        UNMODIFIED,
        /**
         * Used for a stream that represents the decoded body. This means the transport encoding is
         * stripped and UTF-8 is used as character set.
         */
        DECODED
    }

    /**
     * Get the body contents in the format described by the parameter {@code type}.
     *
     * @param type
     *         The format the returned data should have.
     *
     * @return An {@link InputStream} that will return the contents of this body in the desired
     *         format.
     *
     * @see StreamType
     */
    public InputStream getInputStream(StreamType type);

    /**
     * Write the body contents in RFC 822 compatible form to the specified stream.
     *
     * @param out
     *         The {@link OutputStream} this {@link Body} should be written to.
     *
     * @throws IOException
     */
    public void writeTo(OutputStream out) throws IOException;
}
