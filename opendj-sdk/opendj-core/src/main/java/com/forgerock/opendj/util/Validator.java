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
 *      Portions copyright 2012 ForgeRock AS.
 */

package com.forgerock.opendj.util;

/**
 * Common methods for validating method arguments.
 */
public final class Validator {

    /**
     * Throws a {@code NullPointerException} if any of the provided arguments
     * are {@code null}.
     *
     * @param objects
     *            The objects to test.
     * @throws NullPointerException
     *             If any of the provided arguments are {@code null}.
     */
    public static void ensureNotNull(final Object... objects) {
        for (final Object o : objects) {
            if (o == null) {
                throw new NullPointerException();
            }
        }
    }

    /**
     * Throws a {@code NullPointerException} if any of the provided arguments
     * are {@code null}.
     *
     * @param o1
     *            The first object to test.
     * @param o2
     *            The second object to test.
     * @throws NullPointerException
     *             If any of the provided arguments are {@code null}.
     */
    public static void ensureNotNull(final Object o1, final Object o2) {
        if (o1 == null || o2 == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Throws a {@code NullPointerException} if any of the provided arguments
     * are {@code null}.
     *
     * @param o1
     *            The first object to test.
     * @param o2
     *            The second object to test.
     * @param o3
     *            The third object to test.
     * @throws NullPointerException
     *             If any of the provided arguments are {@code null}.
     */
    public static void ensureNotNull(final Object o1, final Object o2, final Object o3) {
        if (o1 == null || o2 == null || o3 == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Throws a {@code NullPointerException} if any of the provided arguments
     * are {@code null}.
     *
     * @param o1
     *            The first object to test.
     * @param o2
     *            The second object to test.
     * @param o3
     *            The third object to test.
     * @param o4
     *            The fourth object to test.
     * @throws NullPointerException
     *             If any of the provided arguments are {@code null}.
     */
    public static void ensureNotNull(final Object o1, final Object o2, final Object o3,
            final Object o4) {
        if (o1 == null || o2 == null || o3 == null || o4 == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Throws a {@code NullPointerException} if any of the provided arguments
     * are {@code null}.
     *
     * @param o1
     *            The first object to test.
     * @param o2
     *            The second object to test.
     * @param o3
     *            The third object to test.
     * @param o4
     *            The fourth object to test.
     * @param o5
     *            The fifth object to test.
     * @throws NullPointerException
     *             If any of the provided arguments are {@code null}.
     */
    public static void ensureNotNull(final Object o1, final Object o2, final Object o3,
            final Object o4, final Object o5) {
        if (o1 == null || o2 == null || o3 == null || o4 == null || o5 == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Throws a {@code NullPointerException} if the provided argument is
     * {@code null}. This method returns a reference to its single parameter so
     * that it can be easily used in constructors.
     *
     * @param <T>
     *            The type of {@code o1}.
     * @param o1
     *            The object to test.
     * @return A reference to {@code o1}.
     * @throws NullPointerException
     *             If the provided argument is {@code null}.
     */
    public static <T> T ensureNotNull(final T o1) {
        if (o1 == null) {
            throw new NullPointerException();
        }
        return o1;
    }

    /**
     * Throws an {@code IllegalArgumentException} if the provided condition is
     * {@code false}.
     *
     * @param condition
     *            The condition, which must be {@code true} to avoid an
     *            exception.
     * @param message
     *            The error message to include in the exception if it is thrown.
     * @throws IllegalArgumentException
     *             If {@code condition} was {@code false}.
     */
    public static void ensureTrue(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    // Prevent instantiation.
    private Validator() {
        // No implementation required.
    }
}
