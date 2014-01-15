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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config.server;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Thrown during the course of interactions with the Directory Server
 * configuration.
 */
public final class ConfigException extends Exception implements LocalizableException {
    private static final long serialVersionUID = -540463620272921157L;
    private final LocalizableMessage message;

    /**
     * Returns the message that explains the problem that occurred.
     *
     * @return LocalizableMessage of the problem
     */
    public LocalizableMessage getMessageObject() {
        return message;
    }

    /**
     * Creates a new configuration exception with the provided message.
     *
     * @param message
     *            The message to use for this configuration exception.
     */
    public ConfigException(LocalizableMessage message) {
        super(message.toString());
        this.message = message;
    }

    /**
     * Creates a new configuration exception with the provided message and
     * underlying cause.
     *
     * @param message
     *            The message to use for this configuration exception.
     * @param cause
     *            The underlying cause that triggered this configuration
     *            exception.
     */
    public ConfigException(LocalizableMessage message, Throwable cause) {
        super(message.toString(), cause);
        this.message = message;
    }

}
