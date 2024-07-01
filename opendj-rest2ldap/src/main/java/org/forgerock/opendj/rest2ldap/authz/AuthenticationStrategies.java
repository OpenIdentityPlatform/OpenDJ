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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.Schema;


/**
 * Factory methods of {@link AuthenticationStrategy} allowing to perform authentication against LDAP server through
 * different method.
 */
public final class AuthenticationStrategies {

    private AuthenticationStrategies() {
    }

    /**
     * Creates an {@link AuthenticationStrategy} performing simple BIND authentication against an LDAP server.
     *
     * @param connectionFactory
     *            {@link ConnectionFactory} to the LDAP server used to perform the bind operation.
     * @param bindDNTemplate
     *            Tempalte of the DN to use for the bind operation. The first %s will be replaced by the provided
     *            authentication-id (i.e: uid=%s,dc=example,dc=com)
     * @param schema
     *            {@link Schema} used to validate the DN format.*
     * @return a new simple bind {@link AuthenticationStrategy}
     * @throws NullPointerException
     *             If a parameter is null
     */
    public static AuthenticationStrategy newSimpleBindStrategy(ConnectionFactory connectionFactory,
            String bindDNTemplate, Schema schema) {
        return new SimpleBindStrategy(connectionFactory, bindDNTemplate, schema);
    }

    /**
     * Creates an {@link AuthenticationStrategy} performing authentication against an LDAP server by first performing a
     * lookup of the entry to bind with. This is to find the user DN to bind with from its metadata (i.e: email
     * address).
     *
     * @param searchConnectionFactory
     *            {@link ConnectionFactory} to the LDAP server used to perform the lookup of the entry.
     * @param bindConnectionFactory
     *            {@link ConnectionFactory} to the LDAP server used to perform the bind one the user's DN has been
     *            found. Can be the same than the searchConnectionFactory.
     * @param baseDN
     *            Base DN of the search request performed to find the user's DN.
     * @param searchScope
     *            {@link SearchScope} of the search request performed to find the user's DN.
     * @param filterTemplate
     *            Filter of the search request (i.e: (&(email=%s)(objectClass=inetOrgPerson)) where the first %s will be
     *            replaced by the user's provided authentication-id.
     * @return a new search then bind {@link AuthenticationStrategy}
     * @throws NullPointerException
     *             If a parameter is null
     */
    public static AuthenticationStrategy newSearchThenBindStrategy(ConnectionFactory searchConnectionFactory,
            ConnectionFactory bindConnectionFactory, DN baseDN, SearchScope searchScope, String filterTemplate) {
        return new SearchThenBindStrategy(searchConnectionFactory, bindConnectionFactory, baseDN, searchScope,
                filterTemplate);
    }

    /**
     * Creates an {@link AuthenticationStrategy} performing authentication against an LDAP server using a plain SASL
     * bind request.
     *
     * @param connectionFactory
     *            {@link ConnectionFactory} to the LDAP server to authenticate with.
     * @param authcIdTemplate
     *            Authentication identity template containing a single %s which will be replaced by the authenticating
     *            user's name. (i.e: (u:%s)
     * @param schema
     *            Schema used to perform DN validation.
     * @return a new SASL plain bind {@link AuthenticationStrategy}
     * @throws NullPointerException
     *             If a parameter is null
     */
    public static AuthenticationStrategy newSaslPlainStrategy(ConnectionFactory connectionFactory, Schema schema,
                                                              String authcIdTemplate) {
        return new SaslPlainStrategy(connectionFactory, schema, authcIdTemplate);
    }
}
