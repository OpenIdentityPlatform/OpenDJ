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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import org.forgerock.util.query.QueryFilter;

/** An enumeration of the commons REST query comparison filter types. */
enum FilterType {

    /**
     * Substring filter.
     *
     * @see QueryFilter#contains
     */
    CONTAINS,

    /**
     * Equality filter.
     *
     * @see QueryFilter#equalTo
     */
    EQUAL_TO,

    /**
     * Extended match filter.
     *
     * @see QueryFilter#comparisonFilter
     */
    EXTENDED,

    /**
     * Greater than ordering filter.
     *
     * @see QueryFilter#greaterThan
     */
    GREATER_THAN,

    /**
     * Greater than or equal to ordering filter.
     *
     * @see QueryFilter#greaterThanOrEqualTo
     */
    GREATER_THAN_OR_EQUAL_TO,

    /**
     * Less than ordering filter.
     *
     * @see QueryFilter#lessThan
     */
    LESS_THAN,

    /**
     * Less than or equal to ordering filter.
     *
     * @see QueryFilter#lessThanOrEqualTo
     */
    LESS_THAN_OR_EQUAL_TO,

    /**
     * Presence filter.
     *
     * @see QueryFilter#present
     */
    PRESENT,

    /**
     * Initial sub-string filter.
     *
     * @see QueryFilter#startsWith
     */
    STARTS_WITH
}
