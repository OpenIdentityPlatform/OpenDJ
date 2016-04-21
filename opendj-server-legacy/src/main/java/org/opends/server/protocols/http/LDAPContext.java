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
package org.opends.server.protocols.http;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;

/** Context provided by this LDAP server to the embedded {@link org.forgerock.http.HttpApplication}s. */
public final class LDAPContext extends AbstractContext
{
  private final ConnectionFactory ldapConnectionFactory;

  LDAPContext(final Context parent, ConnectionFactory ldapConnectionFactory)
  {
    super(parent, "LDAP context");
    this.ldapConnectionFactory = ldapConnectionFactory;
  }

  /**
   * Get the {@link org.forgerock.opendj.ldap.LDAPConnectionFactory} attached to this context.
   *
   * @return The {@link org.forgerock.opendj.ldap.LDAPConnectionFactory} attached to this context.
   */
  public ConnectionFactory getLdapConnectionFactory()
  {
    return ldapConnectionFactory;
  }
}
