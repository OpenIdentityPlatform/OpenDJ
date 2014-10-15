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

package org.forgerock.opendj.ldap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A Search operation alias dereferencing policy as defined in RFC 4511 section
 * 4.5.1.3 is used to indicate whether or not alias entries (as defined in RFC
 * 4512) are to be dereferenced during stages of a Search operation. The act of
 * dereferencing an alias includes recursively dereferencing aliases that refer
 * to aliases.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511#section-4.5.1.3">RFC 4511 -
 *      Lightweight Directory Access Protocol (LDAP): The Protocol </a>
 * @see <a href="http://tools.ietf.org/html/rfc4512">RFC 4512 - Lightweight
 *      Directory Access Protocol (LDAP): Directory Information Models </a>
 */
public final class DereferenceAliasesPolicy {
    private static final DereferenceAliasesPolicy[] ELEMENTS = new DereferenceAliasesPolicy[4];

    private static final List<DereferenceAliasesPolicy> IMMUTABLE_ELEMENTS = Collections
            .unmodifiableList(Arrays.asList(ELEMENTS));

    /**
     * Do not dereference aliases in searching or in locating the base object of
     * a Search operation.
     */
    public static final DereferenceAliasesPolicy NEVER = register(0, "never");

    /**
     * While searching subordinates of the base object, dereference any alias
     * within the scope of the Search operation. Dereferenced objects become the
     * vertices of further search scopes where the Search operation is also
     * applied. If the search scope is {@code WHOLE_SUBTREE}, the Search
     * continues in the subtree(s) of any dereferenced object. If the search
     * scope is {@code SINGLE_LEVEL}, the search is applied to any dereferenced
     * objects and is not applied to their subordinates.
     */
    public static final DereferenceAliasesPolicy IN_SEARCHING = register(1, "search");

    /**
     * Dereference aliases in locating the base object of a Search operation,
     * but not when searching subordinates of the base object.
     */
    public static final DereferenceAliasesPolicy FINDING_BASE = register(2, "find");

    /**
     * Dereference aliases both in searching and in locating the base object of
     * a Search operation.
     */
    public static final DereferenceAliasesPolicy ALWAYS = register(3, "always");

    /**
     * Returns the alias dereferencing policy having the specified integer value
     * as defined in RFC 4511 section 4.5.1.
     *
     * @param intValue
     *            The integer value of the alias dereferencing policy.
     * @return The dereference aliases policy, or {@code null} if there was no
     *         alias dereferencing policy associated with {@code intValue}.
     */
    public static DereferenceAliasesPolicy valueOf(final int intValue) {
        if (intValue < 0 || intValue >= ELEMENTS.length) {
            return null;
        }
        return ELEMENTS[intValue];
    }

    /**
     * Returns an unmodifiable list containing the set of available alias
     * dereferencing policies indexed on their integer value as defined in RFC
     * 4511 section 4.5.1.
     *
     * @return An unmodifiable list containing the set of available alias
     *         dereferencing policies.
     */
    public static List<DereferenceAliasesPolicy> values() {
        return IMMUTABLE_ELEMENTS;
    }

    /**
     * Creates and registers a new alias dereferencing policy with the
     * application.
     *
     * @param intValue
     *            The integer value of the alias dereferencing policy as defined
     *            in RFC 4511 section 4.5.1.
     * @param name
     *            The name of the alias dereferencing policy.
     * @return The new alias dereferencing policy.
     */
    private static DereferenceAliasesPolicy register(final int intValue, final String name) {
        final DereferenceAliasesPolicy t = new DereferenceAliasesPolicy(intValue, name);
        ELEMENTS[intValue] = t;
        return t;
    }

    private final int intValue;

    private final String name;

    /** Prevent direct instantiation. */
    private DereferenceAliasesPolicy(final int intValue, final String name) {
        this.intValue = intValue;
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof DereferenceAliasesPolicy) {
            return this.intValue == ((DereferenceAliasesPolicy) obj).intValue;
        } else {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return intValue;
    }

    /**
     * Returns the integer value of this alias dereferencing policy as defined
     * in RFC 4511 section 4.5.1.
     *
     * @return The integer value of this alias dereferencing policy.
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Returns the string representation of this alias dereferencing policy.
     *
     * @return The string representation of this alias dereferencing policy.
     */
    @Override
    public String toString() {
        return name;
    }
}
