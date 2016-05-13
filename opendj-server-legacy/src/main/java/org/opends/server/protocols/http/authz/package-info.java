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
 * Copyright 2016 ForgeRock AS.
 */

/**
 * Contains factories to build the authorization stage of the HTTP request processing. Each HTTP request can go through
 * one or several {@link org.forgerock.opendj.rest2ldap.authz.ConditionalFilters.ConditionalFilter} resulting in the
 * injection of an {@link org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext} which can be used by the
 * {@link org.opends.server.api.HttpEndpoint} to perform LDAP requests against this directory server.
 */
package org.opends.server.protocols.http.authz;

