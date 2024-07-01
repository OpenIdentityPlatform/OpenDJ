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
 * This package contains {@link org.forgerock.http.Filter} to authenticate and authorize LDAP connections. Authorization
 * filter injects a {@link org.forgerock.services.context.SecurityContext} populated with authorization information like
 * user's id, user's DN or anything else. This {@link org.forgerock.services.context.SecurityContext} can then be used
 * by {@link org.forgerock.opendj.rest2ldap.authz.ProxiedAuthV2Filter} to inject an
 * {@link org.forgerock.opendj.rest2ldap.AuthenticatedConnectionContext} containing the
 * {@link org.forgerock.opendj.ldap.Connection} with user specific privileges.
 */
package org.forgerock.opendj.rest2ldap.authz;
