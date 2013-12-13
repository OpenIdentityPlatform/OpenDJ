/*
* CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *       Copyright 2010 Sun Microsystems, Inc.
 *       Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

/**
 * Class used to define dynamic typed attachments on {@link AttachmentHolder}
 * instances. Storing attachment values in {@link AttachmentHolder} has the
 * advantage of the <tt>Attachment</tt> value being typed when compared to Map
 * storage:
 *
 * @param <T>
 *            The type of attachment.
 */
public final class Attachment<T> {
    private final T defaultValue;
    private final String name;

    /**
     * Construct a new attachment with the specified name and default value.
     *
     * @param name
     *            Attachment name.
     * @param defaultValue
     *            Attachment default value, which will be used, if it is not
     *            set.
     */
    public Attachment(final String name, final T defaultValue) {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    /**
     * Retrieves the attachment value, stored on the {@link AttachmentHolder}.
     *
     * @param attachmentHolder
     *            {@link AttachmentHolder}.
     * @return attachment value.
     */
    public T get(final AttachmentHolder attachmentHolder) {
        T value = get0(attachmentHolder);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Remove attachment value, stored on the {@link AttachmentHolder}.
     *
     * @param attachmentHolder
     *            {@link AttachmentHolder}.
     * @return The former value or {@code null} if there was previously no
     *         value.
     */
    public T remove(final AttachmentHolder attachmentHolder) {
        final T value = get0(attachmentHolder);
        if (value != null) {
            set(attachmentHolder, null);
        }
        return value;
    }

    /**
     * Set attachment value, stored on the {@link AttachmentHolder}. If a value
     * already exists, it will be replaced.
     *
     * @param attachmentHolder
     *            {@link AttachmentHolder}.
     * @param value
     *            attachment value to set.
     * @return The former value or {@code null} if there was previously no
     *         value.
     */
    public T set(final AttachmentHolder attachmentHolder, final T value) {
        final T oldValue = get0(attachmentHolder);
        attachmentHolder.setAttachment(name, value);
        return oldValue;
    }

    @SuppressWarnings("unchecked")
    private T get0(final AttachmentHolder attachmentHolder) {
        return (T) attachmentHolder.getAttachment(name);
    }
}
