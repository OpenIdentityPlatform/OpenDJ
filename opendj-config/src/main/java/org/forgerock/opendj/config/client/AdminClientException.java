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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config.client;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AdminException;

/**
 * Administration client exceptions represent non-operational problems which
 * occur whilst interacting with the administration framework. They provide
 * clients with a transport independent interface for handling transport related
 * exceptions.
 * <p>
 * Client exceptions represent communications problems, security problems, and
 * service related problems.
 */
public abstract class AdminClientException extends AdminException {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 4044747533980824456L;

    /**
     * Create an administration client exception with a message and cause.
     *
     * @param message
     *            The message.
     * @param cause
     *            The cause.
     */
    protected AdminClientException(LocalizableMessage message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create an administration client exception with a message.
     *
     * @param message
     *            The message.
     */
    protected AdminClientException(LocalizableMessage message) {
        super(message);
    }
}
