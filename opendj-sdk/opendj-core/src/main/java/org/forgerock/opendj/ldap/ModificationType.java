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
 * A Modify operation change type as defined in RFC 4511 section 4.6 is used to
 * specify the type of modification being performed on an attribute.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511#section-4.6">RFC 4511 -
 *      Lightweight Directory Access Protocol (LDAP): The Protocol </a>
 * @see <a href="http://tools.ietf.org/html/rfc4525">RFC 4525 - Lightweight
 *      Directory Access Protocol (LDAP) Modify-Increment Extension </a>
 */
public final class ModificationType {

    /**
     * Contains equivalent values for the ModificationType values.
     * This allows easily using ModificationType values with switch statements.
     */
    public static enum Enum {
        //@Checkstyle:off
        /** @see ModificationType#ADD */
        ADD,
        /** @see ModificationType#DELETE */
        DELETE,
        /** @see ModificationType#REPLACE */
        REPLACE,
        /** @see ModificationType#INCREMENT */
        INCREMENT,
        /** Used for unknown modification types. */
        UNKNOWN;
        //@Checkstyle:on
    }

    private static final ModificationType[] ELEMENTS = new ModificationType[4];

    private static final List<ModificationType> IMMUTABLE_ELEMENTS = Collections
            .unmodifiableList(Arrays.asList(ELEMENTS));

    /**
     * Add the values listed in the modification to the attribute, creating the
     * attribute if necessary.
     */
    public static final ModificationType ADD = register(0, "add", Enum.ADD);

    /**
     * Delete the values listed in the modification from the attribute. If no
     * values are listed, or if all current values of the attribute are listed,
     * the entire attribute is removed.
     */
    public static final ModificationType DELETE = register(1, "delete", Enum.DELETE);

    /**
     * Replace all existing values of the attribute with the new values listed
     * in the modification, creating the attribute if it did not already exist.
     * A replace with no listed values will delete the entire attribute if it
     * exists, and it is ignored if the attribute does not exist.
     */
    public static final ModificationType REPLACE = register(2, "replace", Enum.REPLACE);

    /**
     * Increment all existing values of the attribute by the amount specified in
     * the modification value.
     */
    public static final ModificationType INCREMENT = register(3, "increment", Enum.INCREMENT);

    /**
     * Returns the modification change type having the specified integer value
     * as defined in RFC 4511 section 4.6.
     *
     * @param intValue
     *            The integer value of the modification change type.
     * @return The modification change type, or {@code null} if there was no
     *         modification change type associated with {@code intValue}.
     */
    public static ModificationType valueOf(final int intValue) {
        ModificationType result = null;
        if (0 <= intValue && intValue < ELEMENTS.length) {
            result = ELEMENTS[intValue];
        }
        if (result == null) {
            result = new ModificationType(intValue, "unknown(" + intValue + ")", Enum.UNKNOWN);
        }
        return result;
    }

    /**
     * Returns an unmodifiable list containing the set of available modification
     * change types indexed on their integer value as defined in RFC 4511
     * section 4.6.
     *
     * @return An unmodifiable list containing the set of available modification
     *         change types.
     */
    public static List<ModificationType> values() {
        return IMMUTABLE_ELEMENTS;
    }

    /**
     * Creates and registers a new modification change type with the
     * application.
     *
     * @param intValue
     *            The integer value of the modification change type as defined
     *            in RFC 4511 section 4.6.
     * @param name
     *            The name of the modification change type.
     * @param modificationTypeEnum
     *            The enum equivalent for this modification type
     * @return The new modification change type.
     */
    private static ModificationType register(final int intValue, final String name, final Enum modificationTypeEnum) {
        final ModificationType t = new ModificationType(intValue, name, modificationTypeEnum);
        ELEMENTS[intValue] = t;
        return t;
    }

    private final int intValue;

    private final String name;

    private final Enum modificationTypeEnum;

    /** Prevent direct instantiation. */
    private ModificationType(final int intValue, final String name, final Enum modificationTypeEnum) {
        this.intValue = intValue;
        this.name = name;
        this.modificationTypeEnum = modificationTypeEnum;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ModificationType) {
            return this.intValue == ((ModificationType) obj).intValue;
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
     * Returns the integer value of this modification change type as defined in
     * RFC 4511 section 4.6.
     *
     * @return The integer value of this modification change type.
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Returns the enum equivalent for this modification type.
     *
     * @return The enum equivalent for this modification type when a known mapping exists,
     *         or {@link Enum#UNKNOWN} if this is an unknown modification type.
     */
    public Enum asEnum() {
        return this.modificationTypeEnum;
    }

    /**
     * Returns the string representation of this modification change type.
     *
     * @return The string representation of this modification change type.
     */
    @Override
    public String toString() {
        return name;
    }
}
