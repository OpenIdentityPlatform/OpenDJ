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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

import java.security.KeyStoreException;

import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;

/** A localized {@link KeyStoreException}. */
@SuppressWarnings("serial")
final class LocalizedKeyStoreException extends KeyStoreException implements LocalizableException {
    /** The I18N message associated with this exception. */
    private final LocalizableMessage message;

    LocalizedKeyStoreException(final LocalizableMessage message) {
        super(String.valueOf(message));
        this.message = message;
    }

    LocalizedKeyStoreException(final LocalizableMessage message, final Throwable cause) {
        super(String.valueOf(message), cause);
        this.message = message;
    }

    @Override
    public LocalizableMessage getMessageObject() {
        return this.message;
    }
}
