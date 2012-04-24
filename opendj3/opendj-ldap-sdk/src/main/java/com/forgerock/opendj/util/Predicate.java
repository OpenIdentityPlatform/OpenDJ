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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.util;

/**
 * Predicates transform input values of type {@code M} to a boolean output value
 * and are typically used for performing filtering.
 *
 * @param <M>
 *            The type of input values matched by this predicate.
 * @param <P>
 *            The type of the additional parameter to this predicate's
 *            {@code matches} method. Use {@link java.lang.Void} for predicates
 *            that do not need an additional parameter.
 */
public interface Predicate<M, P> {
    /**
     * Indicates whether or not this predicate matches the provided input value
     * of type {@code M}.
     *
     * @param value
     *            The input value for which to make the determination.
     * @param p
     *            A predicate specified parameter.
     * @return {@code true} if this predicate matches {@code value}, otherwise
     *         {@code false}.
     */
    boolean matches(M value, P p);
}
