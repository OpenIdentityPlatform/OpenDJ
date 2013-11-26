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

import java.util.List;

/**
 * A visitor of {@code Filter}s, in the style of the visitor design pattern.
 * <p>
 * Classes implementing this interface can query filters in a type-safe manner.
 * When a visitor is passed to a filter's accept method, the corresponding visit
 * method most applicable to that filter is invoked.
 *
 * @param <R>
 *            The return type of this visitor's methods. Use
 *            {@link java.lang.Void} for visitors that do not need to return
 *            results.
 * @param <P>
 *            The type of the additional parameter to this visitor's methods.
 *            Use {@link java.lang.Void} for visitors that do not need an
 *            additional parameter.
 */
public interface FilterVisitor<R, P> {

    /**
     * Visits an {@code and} filter.
     * <p>
     * <b>Implementation note</b>: for the purposes of matching an empty
     * sub-filter list should always evaluate to {@code true} as per RFC 4526.
     *
     * @param p
     *            A visitor specified parameter.
     * @param subFilters
     *            The unmodifiable list of sub-filters.
     * @return Returns a visitor specified result.
     */
    R visitAndFilter(P p, List<Filter> subFilters);

    /**
     * Visits an {@code approximate match} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return Returns a visitor specified result.
     */
    R visitApproxMatchFilter(P p, String attributeDescription, ByteString assertionValue);

    /**
     * Visits an {@code equality match} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return Returns a visitor specified result.
     */
    R visitEqualityMatchFilter(P p, String attributeDescription, ByteString assertionValue);

    /**
     * Visits an {@code extensible} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param matchingRule
     *            The matching rule name, may be {@code null} if
     *            {@code attributeDescription} is specified.
     * @param attributeDescription
     *            The attribute description, may be {@code null} if
     *            {@code matchingRule} is specified.
     * @param assertionValue
     *            The assertion value.
     * @param dnAttributes
     *            Indicates whether DN matching should be performed.
     * @return Returns a visitor specified result.
     */
    R visitExtensibleMatchFilter(P p, String matchingRule, String attributeDescription,
            ByteString assertionValue, boolean dnAttributes);

    /**
     * Visits a {@code greater or equal} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return Returns a visitor specified result.
     */
    R visitGreaterOrEqualFilter(P p, String attributeDescription, ByteString assertionValue);

    /**
     * Visits a {@code less or equal} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @param assertionValue
     *            The assertion value.
     * @return Returns a visitor specified result.
     */
    R visitLessOrEqualFilter(P p, String attributeDescription, ByteString assertionValue);

    /**
     * Visits a {@code not} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param subFilter
     *            The sub-filter.
     * @return Returns a visitor specified result.
     */
    R visitNotFilter(P p, Filter subFilter);

    /**
     * Visits an {@code or} filter.
     * <p>
     * <b>Implementation note</b>: for the purposes of matching an empty
     * sub-filter list should always evaluate to {@code false} as per RFC 4526.
     *
     * @param p
     *            A visitor specified parameter.
     * @param subFilters
     *            The unmodifiable list of sub-filters.
     * @return Returns a visitor specified result.
     */
    R visitOrFilter(P p, List<Filter> subFilters);

    /**
     * Visits a {@code present} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @return Returns a visitor specified result.
     */
    R visitPresentFilter(P p, String attributeDescription);

    /**
     * Visits a {@code substrings} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param attributeDescription
     *            The attribute description.
     * @param initialSubstring
     *            The initial sub-string, may be {@code null}.
     * @param anySubstrings
     *            The unmodifiable list of any sub-strings, may be empty.
     * @param finalSubstring
     *            The final sub-string, may be {@code null}.
     * @return Returns a visitor specified result.
     */
    R visitSubstringsFilter(P p, String attributeDescription, ByteString initialSubstring,
            List<ByteString> anySubstrings, ByteString finalSubstring);

    /**
     * Visits an {@code unrecognized} filter.
     *
     * @param p
     *            A visitor specified parameter.
     * @param filterTag
     *            The ASN.1 tag.
     * @param filterBytes
     *            The filter content.
     * @return Returns a visitor specified result.
     */
    R visitUnrecognizedFilter(P p, byte filterTag, ByteString filterBytes);

}
