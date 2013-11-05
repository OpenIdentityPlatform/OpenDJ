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
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldif;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/**
 * Exception that can be thrown if a problem occurs during MakeLDIF processing.
 */
@SuppressWarnings("serial")
public class MakeLDIFException extends Exception implements LocalizableException {

    /** The I18N message associated with this exception. */
    private final LocalizableMessage message;

    /**
     * Creates a new MakeLDIF exception with the provided information.
     *
     * @param message
     *            The message for this exception.
     */
    public MakeLDIFException(final LocalizableMessage message) {
        super(String.valueOf(message));
        this.message = message;
    }

    /**
     * Creates a new MakeLDIF exception with the provided information.
     *
     * @param message
     *            The message for this exception.
     * @param cause
     *            The underlying cause for this exception.
     */
    public MakeLDIFException(final LocalizableMessage message, Throwable cause) {
        super(String.valueOf(message), cause);
        this.message = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LocalizableMessage getMessageObject() {
        return this.message;
    }

}
