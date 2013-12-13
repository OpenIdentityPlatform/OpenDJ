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
