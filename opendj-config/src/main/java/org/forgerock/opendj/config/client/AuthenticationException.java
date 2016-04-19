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

import org.forgerock.i18n.LocalizableMessage;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

/**
 * This exception is thrown when an authentication error occurs while connecting
 * to the Directory Server. An authentication error can happen, for example,
 * when the client credentials are invalid.
 */
public class AuthenticationException extends AdminSecurityException {

    /** Serialization ID. */
    private static final long serialVersionUID = 3544797197747686958L;

    /** Creates an authentication exception with a default message. */
    public AuthenticationException() {
        super(ERR_AUTHENTICATION_EXCEPTION_DEFAULT.get());
    }

    /**
     * Create an authentication exception with a cause and a default message.
     *
     * @param cause
     *            The cause.
     */
    public AuthenticationException(Throwable cause) {
        super(ERR_AUTHENTICATION_EXCEPTION_DEFAULT.get(), cause);
    }

    /**
     * Create an authentication exception with a message and cause.
     *
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    public AuthenticationException(LocalizableMessage message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an authentication exception with a message.
     *
     * @param message
     *            The message.
     */
    public AuthenticationException(LocalizableMessage message) {
        super(message);
    }
}
