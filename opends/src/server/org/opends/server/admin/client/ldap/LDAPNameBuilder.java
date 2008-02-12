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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.ldap;



import java.util.LinkedList;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.ManagedObjectPathSerializer;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;



/**
 * A strategy for creating <code>LdapName</code>s from managed object paths.
 */
final class LDAPNameBuilder implements ManagedObjectPathSerializer {

  /**
   * Creates a new LDAP name representing the specified managed object
   * path.
   *
   * @param path
   *          The managed object path.
   * @param profile
   *          The LDAP profile which should be used to construct LDAP
   *          names.
   * @return Returns a new LDAP name representing the specified
   *         managed object path.
   */
  public static LdapName create(ManagedObjectPath<?, ?> path,
      LDAPProfile profile) {
    LDAPNameBuilder builder = new LDAPNameBuilder(profile);
    path.serialize(builder);
    return builder.getInstance();
  }



  /**
   * Creates a new LDAP name representing the specified managed object
   * path and instantiable relation.
   *
   * @param path
   *          The managed object path.
   * @param relation
   *          The child instantiable relation.
   * @param profile
   *          The LDAP profile which should be used to construct LDAP
   *          names.
   * @return Returns a new LDAP name representing the specified
   *         managed object path and instantiable relation.
   */
  public static LdapName create(ManagedObjectPath<?, ?> path,
      InstantiableRelationDefinition<?, ?> relation, LDAPProfile profile) {
    LDAPNameBuilder builder = new LDAPNameBuilder(profile);
    path.serialize(builder);
    builder.appendManagedObjectPathElement(relation);
    return builder.getInstance();
  }

  // The list of RDNs in big-endian order.
  private final LinkedList<Rdn> rdns;

  // The LDAP profile.
  private final LDAPProfile profile;



  /**
   * Create a new JNDI LDAP name builder.
   *
   * @param profile
   *          The LDAP profile which should be used to construct LDAP
   *          names.
   */
  public LDAPNameBuilder(LDAPProfile profile) {
    this.rdns = new LinkedList<Rdn>();
    this.profile = profile;
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
      void appendManagedObjectPathElement(
          InstantiableRelationDefinition<? super C, ? super S> r,
          AbstractManagedObjectDefinition<C, S> d, String name) {
    // Add the RDN sequence representing the relation.
    appendManagedObjectPathElement((RelationDefinition<?, ?>) r);

    // Now add the single RDN representing the named instance.
    String type = profile.getInstantiableRelationChildRDNType(r);
    try {
      Rdn rdn = new Rdn(type, name.trim());
      rdns.add(rdn);
    } catch (InvalidNameException e1) {
      // Should not happen.
      throw new RuntimeException(e1);
    }
  }



  /**
   * Appends the RDN sequence representing the provided relation.
   *
   * @param r
   *          The relation definition.
   */
  public void appendManagedObjectPathElement(RelationDefinition<?, ?> r) {
    // Add the RDN sequence representing the relation.
    try {
      LdapName tmp = new LdapName(profile.getRelationRDNSequence(r));
      rdns.addAll(tmp.getRdns());
    } catch (InvalidNameException e1) {
      // Should not happen.
      throw new RuntimeException(e1);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
      void appendManagedObjectPathElement(
          OptionalRelationDefinition<? super C, ? super S> r,
          AbstractManagedObjectDefinition<C, S> d) {
    // Add the RDN sequence representing the relation.
    appendManagedObjectPathElement((RelationDefinition<?, ?>) r);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
      void appendManagedObjectPathElement(
          SingletonRelationDefinition<? super C, ? super S> r,
          AbstractManagedObjectDefinition<C, S> d) {
    // Add the RDN sequence representing the relation.
    appendManagedObjectPathElement((RelationDefinition<?, ?>) r);
  }



  /**
   * Create a new JNDI LDAP name using the current state of this LDAP name
   * builder.
   *
   * @return Returns the new JNDI LDAP name instance.
   */
  public LdapName getInstance() {
    return new LdapName(rdns);
  }
}
