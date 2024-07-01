/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.server.core;

import java.util.Set;

/**
 * Interface declares common functionality for objects, which can store
 * {@link Attachment}s.
 */
public interface AttachmentHolder {
    /**
     * Retrieves the attachment with the specified name.
     *
     * @param name
     *            The name for the attachment to retrieve. It will be treated in
     *            a case-sensitive manner.
     * @return The requested attachment object, or {@code null} if it does not
     *         exist.
     */
    Object getAttachment(String name);

    /**
     * Retrieves the set of attachment names defined for this holder, as a
     * mapping between the attachment name and the associated object.
     *
     * @return The set of attachments defined for this operation.
     */
    Set<String> getAttachmentNames();

    /**
     * Removes the attachment with the specified name.
     *
     * @param name
     *            The name for the attachment to remove. It will be treated in a
     *            case-sensitive manner.
     * @return The attachment that was removed, or {@code null} if it does not
     *         exist.
     */
    Object removeAttachment(String name);

    /**
     * Sets the value of the specified attachment. If an attachment already
     * exists with the same name, it will be replaced. Otherwise, a new
     * attachment will be added.
     *
     * @param name
     *            The name to use for the attachment.
     * @param value
     *            The value to use for the attachment.
     * @return The former value held by the attachment with the given name, or
     *         {@code null} if there was previously no such attachment.
     */
    Object setAttachment(String name, Object value);
}
