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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

/**
 * A persistent search change type as defined in draft-ietf-ldapext-psearch is
 * used to indicate the type of update operation that caused an entry change
 * notification to occur.
 *
 * @see PersistentSearchRequestControl
 * @see EntryChangeNotificationResponseControl
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-ldapext-psearch">draft-ietf-ldapext-psearch
 *      - Persistent Search: A Simple LDAP Change Notification Mechanism </a>
 */
public enum PersistentSearchChangeType {
    /**
     * Indicates that an Add operation triggered the entry change notification.
     */
    ADD(1, "add"),

    /**
     * Indicates that an Delete operation triggered the entry change
     * notification.
     */
    DELETE(2, "delete"),

    /**
     * Indicates that an Modify operation triggered the entry change
     * notification.
     */
    MODIFY(4, "modify"),

    /**
     * Indicates that an Modify DN operation triggered the entry change
     * notification.
     */
    MODIFY_DN(8, "modifyDN");

    private final String name;
    private final int intValue;

    private PersistentSearchChangeType(final int intValue, final String name) {
        this.name = name;
        this.intValue = intValue;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns the integer value for this change type as defined in the internet
     * draft.
     *
     * @return The integer value for this change type.
     */
    public int intValue() {
        return intValue;
    }

    /**
     * Returns the enum value that would return the provided argument value from its {@link #intValue} method.
     *
     * @param value The value to match.
     * @return The appropriate enum value.
     */
    public static PersistentSearchChangeType valueOf(int value) {
        switch (value) {
        case 1:
            return ADD;
        case 2:
            return DELETE;
        case 4:
            return MODIFY;
        case 8:
            return MODIFY_DN;
        default:
            throw new IllegalArgumentException("Unknown int value: " + value);
        }
    }
}
