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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

/**
 * The policy which should be used in order to read an entry before it is
 * deleted, or after it is added or modified.
 */
public enum ReadOnUpdatePolicy {
    /**
     * The LDAP entry will not be read when an update is performed. More
     * specifically, the REST resource will not be returned as part of a create,
     * delete, patch, or update request.
     */
    DISABLED,

    /**
     * The LDAP entry will be read atomically using the RFC 4527 read-entry
     * controls. More specifically, the REST resource will be returned as part
     * of a create, delete, patch, or update request, and it will reflect the
     * state of the resource at the time the update was performed. This policy
     * requires that the LDAP server supports RFC 4527.
     */
    CONTROLS,

    /**
     * The LDAP entry will be read non-atomically using an LDAP search when an
     * update is performed. More specifically, the REST resource will be
     * returned as part of a create, delete, patch, or update request, but it
     * may not reflect the state of the resource at the time the update was
     * performed.
     */
    SEARCH;
}
