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
package org.forgerock.opendj.config.server;

import java.util.Collection;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * Common features of config listener adaptors.
 */
abstract class AbstractConfigListenerAdaptor {

    /**
     * Create a new config listener adaptor.
     */
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
