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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
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
