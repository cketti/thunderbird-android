package com.fsck.k9.message;

import java.util.List;

/**
 * Represents the body of a MIME multipart part. Consisting of a list of {@link Part} objects.
 *
 * <p>
 * The {@link Part} containing this object must have a content type starting with
 * {@code multipart/}.
 * </p>
 */
public interface Multipart extends Body {
    /**
     * Get the preamble of this multipart body. The preamble is the part between the header and the
     * first boundary.
     *
     * @return The raw data of the preamble. May be {@code null}.
     */
    public byte[] getPreamble();

    /**
     * Set the preamble of this multipart body.
     *
     * @param preamble
     *         The raw data of the preamble. May be {@code null}.
     */
    public void setPreamble(byte[] preamble);

    /**
     * Get the epilogue of this multipart body. The epilogue is the part between the last boundary
     * and the end of this MIME part.
     *
     * @return The raw data of the epilogue. May be {@code null}.
     */
    public byte[] getEpilogue();

    /**
     * Set the epilogue of this multipart body.
     *
     * @param epilogue
     *         The raw data of the epilogue. May be {@code null}.
     */
    public void setEpilogue(byte[] epilogue);

    /**
     * Get the boundary of this {@link Multipart}.
     *
     * @return The boundary string (without the leading {@code "--"} when used as delimiter). Never
     *         {@code null}.
     */
    public String getBoundary();

    /**
     * Get the boundary of this {@link Multipart}.
     *
     * @param boundary
     *         The boundary string (without the leading {@code "--"} when used as delimiter).
     *         Never {@code null}.
     */
    public void setBoundary(String boundary);

    /**
     * Get the list of {@link Part}s that are children of this {@link Multipart}.
     *
     * @return The children of this {@code Multipart}.
     */
    public List<Part> getParts();

    /**
     * Append a {@link Part} to the list of children of this {@link Multipart}.
     *
     * @param part
     *         The {@code Part} object to add. Never {@code null}.
     */
    public void addPart(Part part);

    /**
     * Insert a {@link Part} into the list of children of this {@link Multipart}.
     *
     * @param part
     *         The {@code Part} object to add. Never {@code null}.
     * @param position
     *         The 0-indexed position at which {@code part} should be added to the list of
     *         children.
     */
    public void addPart(Part part, int position);

    /**
     * Remove a specific {@link Part} from the list of children of this {@link Multipart}.
     *
     * @param part
     *         The {@code Part} object to remove. Never {@code null}.
     */
    public void removePart(Part part);
}
