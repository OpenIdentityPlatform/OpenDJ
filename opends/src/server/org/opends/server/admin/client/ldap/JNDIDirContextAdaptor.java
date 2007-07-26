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



import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.server.admin.client.AuthenticationException;
import org.opends.server.admin.client.AuthenticationNotSupportedException;
import org.opends.server.admin.client.CommunicationException;



/**
 * An LDAP connection adaptor which maps LDAP requests onto an
 * underlying JNDI connection context.
 */
public final class JNDIDirContextAdaptor extends LDAPConnection {

  /**
   * Adapts the provided JNDI <code>DirContext</code>.
   *
   * @param dirContext
   *          The JNDI connection.
   * @return Returns a new JNDI connection adaptor.
   */
  public static JNDIDirContextAdaptor adapt(DirContext dirContext) {
    return new JNDIDirContextAdaptor(dirContext);
  }



  /**
   * Creates a new JNDI connection adaptor by performing a simple bind
   * operation to the specified LDAP server.
   *
   * @param host
   *          The host.
   * @param port
   *          The port.
   * @param name
   *          The LDAP bind DN.
   * @param password
   *          The LDAP bind password.
   * @return Returns a new JNDI connection adaptor.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   * @throws AuthenticationNotSupportedException
   *           If the server does not support simple authentication.
   * @throws AuthenticationException
   *           If authentication failed for some reason, usually due
   *           to invalid credentials.
   */
  public static JNDIDirContextAdaptor simpleBind(String host, int port,
      String name, String password) throws CommunicationException,
      AuthenticationNotSupportedException, AuthenticationException {
    Hashtable<String, Object> env = new Hashtable<String, Object>();
    env
        .put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port);
    env.put(Context.SECURITY_PRINCIPAL, name);
    env.put(Context.SECURITY_CREDENTIALS, password);

    DirContext ctx;
    try {
      ctx = new InitialLdapContext(env, null);
    } catch (javax.naming.CommunicationException e) {
      throw new CommunicationException(e);
    } catch (javax.naming.AuthenticationException e) {
      throw new AuthenticationException(e);
    } catch (javax.naming.AuthenticationNotSupportedException e) {
      throw new AuthenticationNotSupportedException(e);
    } catch (NamingException e) {
      // Assume some kind of communication problem.
      throw new CommunicationException(e);
    }

    return new JNDIDirContextAdaptor(ctx);
  }

  // The JNDI connection context.
  private final DirContext dirContext;



  // Create a new JNDI connection adaptor using the provider JNDI
  // DirContext.
  private JNDIDirContextAdaptor(DirContext dirContext) {
    this.dirContext = dirContext;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void createEntry(LdapName dn, Attributes attributes)
      throws NamingException {
    dirContext.createSubcontext(dn, attributes);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteSubtree(LdapName dn) throws NamingException {
    // Delete the children first.
    for (LdapName child : listEntries(dn, null)) {
      deleteSubtree(child);
    }

    // Delete the named entry.
    dirContext.destroySubcontext(dn);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean entryExists(LdapName dn) throws NamingException {
    String filter = "(objectClass=*)";
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.OBJECT_SCOPE);

    try {
      NamingEnumeration<SearchResult> results = dirContext.search(dn, filter,
          controls);
      if (results.hasMore()) {
        return true;
      }
    } catch (NameNotFoundException e) {
      // Fall through - entry not found.
    }
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<LdapName> listEntries(LdapName dn, String filter)
      throws NamingException {
    if (filter == null) {
      filter = "(objectClass=*)";
    }

    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

    List<LdapName> children = new LinkedList<LdapName>();
    NamingEnumeration<SearchResult> results = dirContext.search(dn, filter,
        controls);
    while (results.hasMore()) {
      SearchResult sr = results.next();
      LdapName child = new LdapName(dn.getRdns());
      child.add(new Rdn(sr.getName()));
      children.add(child);
    }

    return children;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void modifyEntry(LdapName dn, Attributes mods) throws NamingException {
    ModificationItem[] modList = new ModificationItem[mods.size()];
    NamingEnumeration<? extends Attribute> ne = mods.getAll();
    for (int i = 0; ne.hasMore(); i++) {
      ModificationItem modItem = new ModificationItem(
          DirContext.REPLACE_ATTRIBUTE, ne.next());
      modList[i] = modItem;
    }
    dirContext.modifyAttributes(dn, modList);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Attributes readEntry(LdapName dn, Collection<String> attrIds)
      throws NamingException {
    String[] attrIdList = attrIds.toArray(new String[attrIds.size()]);
    return dirContext.getAttributes(dn, attrIdList);
  }

}
