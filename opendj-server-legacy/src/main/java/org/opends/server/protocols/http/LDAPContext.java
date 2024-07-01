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

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.services.context.AbstractContext;
import org.forgerock.services.context.Context;
import org.opends.server.types.Entry;

/**
 * Context provided by a Directory Server. It contains a reference to a
 * {@link InternalConnectionFactory} which can be used to perform direct LDAP
 * operation on this Directory Server without the network overhead.
 */
public final class LDAPContext extends AbstractContext
{
  private final InternalConnectionFactory internalConnectionFactory;

  /**
   * Create a new LDAPContext.
   *
   * @param parent
   *          The parent context.
   * @param internalConnectionFactory
   *          Internal connection factory of this LDAP server.
   */
  public LDAPContext(final Context parent, InternalConnectionFactory internalConnectionFactory)
  {
    super(parent, "LDAP context");
    this.internalConnectionFactory = internalConnectionFactory;
  }

  /**
   * Get the {@link InternalConnectionFactory} attached to this context.
   *
   * @return The {@link InternalConnectionFactory} attached to this context.
   */
  public InternalConnectionFactory getInternalConnectionFactory()
  {
    return internalConnectionFactory;
  }

  /**
   * An internal connection factory providing direct connection to this Directory
   * Server without the network overhead.
   */
  public interface InternalConnectionFactory
  {
    /**
     * Get a direct {@link Connection} to this Directory Server.
     *
     * @param userEntry
     *          The returned connection will be authenticated as userEntry.
     * @return A direct {@link Connection} to this Directory Server.
     * @throws LdapException
     *           If a connection cannot be created (i.e: because an administrative limit has been exceeded).
     */
    Connection getAuthenticatedConnection(Entry userEntry) throws LdapException;
  }
}