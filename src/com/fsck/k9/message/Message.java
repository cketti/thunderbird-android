package com.fsck.k9.message;

/**
 * Represents a RFC 822 message or a message/rfc822 part of a MIME message
 *
 * <p>
 * This interface supports incomplete messages. That is, messages without a {@link Body} or
 * incomplete set of header lines in the {@link Header} object. This way partially downloaded
 * messages can be supported.
 * </p>
 *
 * @see Part#getBody()
 * @see #isComplete()
 */
public interface Message extends Part, Body {

    /**
     * Returns whether or not this message is complete (i.e. contains a body and includes all
     * headers).
     *
     * @return {@code true} if the message contains a {@link Body} and the associated
     *         {@link Header} object contains ALL header lines; {@code false} otherwise.
     */
    public boolean isComplete();
}
