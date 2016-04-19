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
package org.forgerock.opendj.config.server;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/** Common features of config listener adaptors. */
abstract class AbstractConfigListenerAdaptor {

    /** Create a new config listener adaptor. */
    protected AbstractConfigListenerAdaptor() {
        // No implementation required.
    }

    /**
     * Concatenate a list of messages into a single message.
     *
     * @param reasons
     *            The list of messages to concatenate.
     * @param unacceptableReason
     *            The single message to which messages should be appended.
     */
    protected final void generateUnacceptableReason(Collection<LocalizableMessage> reasons,
            LocalizableMessageBuilder unacceptableReason) {
        boolean isFirst = true;
        for (LocalizableMessage reason : reasons) {
            if (isFirst) {
                isFirst = false;
            } else {
                unacceptableReason.append("  ");
            }
            unacceptableReason.append(reason);
        }
    }
}
