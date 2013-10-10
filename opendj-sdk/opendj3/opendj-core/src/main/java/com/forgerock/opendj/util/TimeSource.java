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
 *      Copyright 2013 ForgeRock AS.
 */

package com.forgerock.opendj.util;

/**
 * An interface which is used by various components in order to obtain the
 * current time.
 */
public interface TimeSource {
    /**
     * Default implementation which delegates to
     * {@link System#currentTimeMillis()}.
     */
    public static final TimeSource DEFAULT = new TimeSource() {

        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    };

    /**
     * Returns the current time in milli-seconds.
     *
     * @return The current time in milli-seconds.
     */
    long currentTimeMillis();
}
