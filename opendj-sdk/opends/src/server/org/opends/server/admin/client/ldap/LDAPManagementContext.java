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



import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;

import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.std.client.RootCfgClient;



/**
 * An LDAP management connection context.
 */
public final class LDAPManagementContext extends ManagementContext {

  // The JNDI context used for the ldap connection.
  private final DirContext dirContext;



  /**
   * Create a new LDAP management context using simple authentication.
   * <p>
   * TODO: we will want to support more secure forms of
   * authentication.
   *
   * @param host
   *          The host.
   * @param port
   *          The port.
   * @param name
   *          The LDAP bind DN.
   * @param password
   *          The LDAP bind password.
   * @return Returns the new management context.
   * @throws NamingException
   *           If the LDAP connection could not be established.
   */
  public static ManagementContext createLDAPContext(String host,
      int port, String name, String password) throws NamingException {
    Hashtable<String, Object> env = new Hashtable<String, Object>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
    env.put(Context.SECURITY_PRINCIPAL, name);
    env.put(Context.SECURITY_CREDENTIALS, password);

    DirContext ctx = new InitialLdapContext(env, null);
    return new LDAPManagementContext(ctx);
  }



  // Private constructor.
  private LDAPManagementContext(DirContext dirContext) {
    this.dirContext = dirContext;
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
    return LDAPManagedObject.getRootManagedObject(dirContext);
  }
}
