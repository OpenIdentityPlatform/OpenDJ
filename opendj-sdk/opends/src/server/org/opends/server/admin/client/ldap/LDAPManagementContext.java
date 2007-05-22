/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.ldap;



import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.util.Validator;



/**
 * An LDAP management connection context.
 */
public final class LDAPManagementContext extends ManagementContext {

  /**
   * Create a new LDAP management context using the provided LDAP
   * connection.
   *
   * @param connection
   *          The LDAP connectin.
   * @return Returns the new management context.
   */
  public static ManagementContext createFromContext(LDAPConnection connection) {
    Validator.ensureNotNull(connection);
    return new LDAPManagementContext(connection, LDAPProfile.getInstance());
  }



  /**
   * Create a new LDAP management context using the provided LDAP
   * connection and LDAP profile.
   * <p>
   * This constructor is primarily intended for testing purposes so
   * that unit tests can provide mock LDAP connections and LDAP
   * profiles.
   *
   * @param connection
   *          The LDAP connection.
   * @param profile
   *          The LDAP profile which should be used to construct LDAP
   *          requests and decode LDAP responses.
   * @return Returns the new management context.
   */
  public static ManagementContext createFromContext(LDAPConnection connection,
      LDAPProfile profile) {
    Validator.ensureNotNull(connection, profile);
    return new LDAPManagementContext(connection, profile);
  }

  // The LDAP connection.
  private final LDAPConnection connection;

  // The LDAP profile which should be used to construct LDAP requests
  // and decode LDAP responses.
  private final LDAPProfile profile;



  // Private constructor.
  private LDAPManagementContext(LDAPConnection connection,
      LDAPProfile profile) {
    this.connection = connection;
    this.profile = profile;
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
    return LDAPManagedObject.getRootManagedObject(this);
  }



  /**
   * Gets the LDAP connection used for interacting with the server.
   *
   * @return Returns the LDAP connection used for interacting with the
   *         server.
   */
  LDAPConnection getLDAPConnection() {
    return connection;
  }



  /**
   * Gets the LDAP profile which should be used to construct LDAP
   * requests and decode LDAP responses.
   *
   * @return Returns the LDAP profile which should be used to
   *         construct LDAP requests and decode LDAP responses.
   */
  LDAPProfile getLDAPProfile() {
    return profile;
  }
}
