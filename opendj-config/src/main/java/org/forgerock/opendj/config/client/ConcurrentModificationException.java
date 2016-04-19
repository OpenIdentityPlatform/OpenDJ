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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.client;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.OperationsException;

/**
 * This exception is thrown when a critical concurrent modification is detected
 * by the client. This may be caused by another client application removing a
 * managed object whilst it is being managed.
 */
public class ConcurrentModificationException extends OperationsException {

    /** Serialization ID. */
    private static final long serialVersionUID = -1467024486347612820L;

    /** Create a concurrent modification exception with a default message. */
    public ConcurrentModificationException() {
        super(ERR_CONCURRENT_MODIFICATION_EXCEPTION_DEFAULT.get());
    }

    /**
     * Create a concurrent modification exception with a cause and a default
     * message.
     *
     * @param cause
     *            The cause.
     */
    public ConcurrentModificationException(Throwable cause) {
        super(ERR_CONCURRENT_MODIFICATION_EXCEPTION_DEFAULT.get(), cause);
    }

    /**
     * Create a concurrent modification exception with a message and cause.
     *
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    public ConcurrentModificationException(LocalizableMessage message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a concurrent modification exception with a message.
     *
     * @param message
     *            The message.
     */
    public ConcurrentModificationException(LocalizableMessage message) {
        super(message);
    }
}
