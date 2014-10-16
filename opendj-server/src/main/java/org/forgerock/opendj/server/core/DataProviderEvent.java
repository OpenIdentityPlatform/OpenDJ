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
 *       Copyright 2008 Sun Microsystems, Inc.
 *       Portions copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.server.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.util.Reject;

/**
 * An object that provides information about the source of a data provider
 * related event. {@code DataProviderEvent} objects are generated when a data
 * provider experiences an operational error or a state change resulting from
 * configuration updates or administrative actions.
 * <p>
 * TODO: what else should this contain?
 */
public final class DataProviderEvent {

    /**
     * Indicates the type of event that has occurred in the data provider.
     */
    public static enum Type {
        /**
         * The data provider's access mode has changed.
         */
        ACCESS_MODE,

        /**
         * The data provider's set of base DNs has changed.
         */
        BASE_DNS,

        /**
         * The data provider's set of supported controls has changed.
         */
        SUPPORTED_CONTROLS,

        /**
         * The data provider's set of supported features has changed.
         */
        SUPPORTED_FEATURES;
    }

    /** A message describing this event. */
    private final LocalizableMessage reason;

    /** The types of event that have occurred in the data provider. */
    private final Set<Type> types;

    /**
     * Creates a new data provider event.
     *
     * @param reason
     *            A message describing this event.
     * @param types
     *            The types of event that have occurred in the data provider.
     */
    public DataProviderEvent(final LocalizableMessage reason, final Set<Type> types) {
        Reject.ifNull(reason, types);
        Reject.ifTrue(types.isEmpty());

        this.reason = reason;

        final EnumSet<Type> tmp = EnumSet.noneOf(Type.class);
        tmp.addAll(types);
        this.types = Collections.unmodifiableSet(tmp);
    }

    /**
     * Returns an unmodifiable set containing the types of event that have
     * occurred in the data provider.
     *
     * @return The unmodifiable set containing the types of event that have
     *         occurred in the data provider.
     */
    public Set<Type> getEventTypes() {
        return types;
    }

    /**
     * Returns a message describing this event.
     *
     * @return A message describing this event.
     */
    public LocalizableMessage getReason() {
        return reason;
    }

    /**
     * Returns a string describing this event.
     *
     * @return A string describing this event.
     */
    @Override
    public String toString() {
        return reason.toString();
    }

}
