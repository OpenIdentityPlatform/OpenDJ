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
 * Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;

/**
 * Thrown when a schema query fails because the requested schema element could
 * not be found or is ambiguous.
 */
@SuppressWarnings("serial")
public class UnknownSchemaElementException extends LocalizedIllegalArgumentException {
    /**
     * Creates a new unknown schema element exception with the provided message.
     *
     * @param message
     *            The message that explains the problem that occurred.
     */
    public UnknownSchemaElementException(final LocalizableMessage message) {
        super(message);
    }
}
