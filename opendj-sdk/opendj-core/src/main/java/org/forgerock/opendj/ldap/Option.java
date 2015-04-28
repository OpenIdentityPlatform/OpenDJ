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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

/**
 * Public API for defining new options.
 *
 * @param <T> The option type.
 */
public final class Option<T> {
    private final Class<T> type;
    private final T defaultValue;

    /**
     * Defines a new {@link Boolean} option with the provided default value.
     *
     * @param defaultValue
     *            The {@link Boolean} default value of this option.
     * @return A new {@link Boolean} option with the provided default value.
     */
    public static Option<Boolean> withDefault(boolean defaultValue) {
        return of(Boolean.class, defaultValue);
    }

    /**
     * Defines a new option of the provided type with the provided default
     * value.
     *
     * @param <T>
     *            The type of this option.
     * @param type
     *            The type of the option.
     * @param defaultValue
     *            The new option default value.
     * @return A new option of the provided type with the provided default
     *         value.
     */
    public static <T> Option<T> of(Class<T> type, T defaultValue) {
        return new Option<>(type, defaultValue);
    }

    private Option(Class<T> type, T defaultValue) {
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Returns the type of this option.
     *
     * @return the type of this option.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Returns the provided value if not {@code null}, otherwise returns the
     * default one.
     *
     * @param value
     *            The option which overrides the default one if not null.
     * @return The provided value if not {@code null}, otherwise return the
     *         default one.
     */
    public T getValue(Object value) {
        return value != null ? type.cast(value) : defaultValue;
    }
}
