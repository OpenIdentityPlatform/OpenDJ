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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */

package org.opends.server.admin.client.ldap;



import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.spi.Driver;
import org.forgerock.util.Reject;



/**
 * An LDAP management connection context.
 */
public final class LDAPManagementContext extends ManagementContext {

  /**
   * Create a new LDAP management context using the provided LDAP
   * connection.
   *
   * @param connection
   *          The LDAP connection.
   * @return Returns the new management context.
   */
  public static ManagementContext createFromContext(LDAPConnection connection) {
    Reject.ifNull(connection);
    return new LDAPManagementContext(connection, LDAPProfile.getInstance());
  }

  /** The LDAP management context driver. */
  private final LDAPDriver driver;



  /** Private constructor. */
  private LDAPManagementContext(LDAPConnection connection,
      LDAPProfile profile) {
    this.driver = new LDAPDriver(this, connection, profile);
  }



  /** {@inheritDoc} */
  @Override
  protected Driver getDriver() {
    return driver;
  }
}
