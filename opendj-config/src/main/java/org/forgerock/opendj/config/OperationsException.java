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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.forgerock.opendj.config;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Exceptions thrown as a result of errors that occurred when reading, listing,
 * and modifying managed objects.
 */
public abstract class OperationsException extends AdminException {

    /** Serialization ID. */
    private static final long serialVersionUID = 6329910102360262187L;

    /**
     * Create an operations exception with a message and cause.
     *
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    protected OperationsException(LocalizableMessage message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an operations exception with a message.
     *
     * @param message
     *            The message.
     */
    protected OperationsException(LocalizableMessage message) {
        super(message);
    }
}
