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

package org.forgerock.opendj.ldap.schema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteSequence;

/**
 * This interface defines the set of methods and structures that must be
 * implemented to define a new attribute syntax.
 */
public interface SyntaxImpl {
    /**
     * Retrieves the default approximate matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default approximate matching rule that will be used for
     *         attributes with this syntax, or {@code null} if approximate
     *         matches will not be allowed for this type by default.
     */
    String getApproximateMatchingRule();

    /**
     * Retrieves the default equality matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default equality matching rule that will be used for
     *         attributes with this syntax, or {@code null} if equality matches
     *         will not be allowed for this type by default.
     */
    String getEqualityMatchingRule();

    /**
     * Retrieves the common name for this attribute syntax.
     *
     * @return The common name for this attribute syntax.
     */
    String getName();

    /**
     * Retrieves the default ordering matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default ordering matching rule that will be used for
     *         attributes with this syntax, or {@code null} if ordering matches
     *         will not be allowed for this type by default.
     */
    String getOrderingMatchingRule();

    /**
     * Retrieves the default substring matching rule that will be used for
     * attributes with this syntax.
     *
     * @return The default substring matching rule that will be used for
     *         attributes with this syntax, or {@code null} if substring matches
     *         will not be allowed for this type by default.
     */
    String getSubstringMatchingRule();

    /**
     * Indicates whether this attribute syntax requires that values must be
     * encoded using the Basic Encoding Rules (BER) used by X.500 directories
     * and always include the {@code binary} attribute description option.
     *
     * @return {@code true} this attribute syntax requires that values must be
     *         BER encoded and always include the {@code binary} attribute
     *         description option, or {@code false} if not.
     * @see <a href="http://tools.ietf.org/html/rfc4522">RFC 4522 - Lightweight
     *      Directory Access Protocol (LDAP): The Binary Encoding Option </a>
     */
    boolean isBEREncodingRequired();

    /**
     * Indicates whether this attribute syntax would likely be a human readable
     * string.
     *
     * @return {@code true} if this attribute syntax would likely be a human
     *         readable string or {@code false} if not.
     */
    boolean isHumanReadable();

    /**
     * Indicates whether the provided value is acceptable for use in an
     * attribute with this syntax. If it is not, then the reason may be appended
     * to the provided buffer.
     *
     * @param schema
     *            The schema in which this syntax is defined.
     * @param value
     *            The value for which to make the determination.
     * @param invalidReason
     *            The buffer to which the invalid reason should be appended.
     * @return {@code true} if the provided value is acceptable for use with
     *         this syntax, or {@code false} if not.
     */
    boolean valueIsAcceptable(Schema schema, ByteSequence value,
            LocalizableMessageBuilder invalidReason);
}
