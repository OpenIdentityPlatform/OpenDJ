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
 * Copyright 2009 Sun Microsystems, Inc.
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
