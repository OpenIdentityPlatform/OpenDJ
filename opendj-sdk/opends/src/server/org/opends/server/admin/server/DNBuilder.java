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

package org.opends.server.admin.server;



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
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RDN;



/**
 * A strategy for creating <code>DN</code>s from managed object paths.
 */
final class DNBuilder implements ManagedObjectPathSerializer {

  /**
   * Creates a new DN representing the specified managed object path.
   *
   * @param path
   *          The managed object path.
   * @return Returns a new DN representing the specified managed object path.
   */
  public static DN create(ManagedObjectPath path) {
    DNBuilder builder = new DNBuilder();
    path.serialize(builder);
    return builder.getInstance();
  }



  /**
   * Creates a new DN representing the specified managed object path
   * and relation.
   *
   * @param path
   *          The managed object path.
   * @param relation
   *          The child relation.
   * @return Returns a new DN representing the specified managed
   *         object path and relation.
   */
  public static DN create(ManagedObjectPath path,
      RelationDefinition<?, ?> relation) {
    DNBuilder builder = new DNBuilder();
    path.serialize(builder);
    builder.appendManagedObjectPathElement(relation);
    return builder.getInstance();
  }

  // The current DN.
  private DN dn;

  // The LDAP profile.
  private final LDAPProfile profile;



  /**
   * Create a new DN builder.
   */
  public DNBuilder() {
    this(LDAPProfile.getInstance());
  }



  /**
   * Create a new DN builder with the provided LDAP profile.
   * <p>
   * This constructor is package private and only intended for testing
   * purposes against a mock LDAP profile.
   *
   * @param profile
   *          The LDAP profile to use when building the DN.
   */
  DNBuilder(LDAPProfile profile) {
    this.dn = DN.nullDN();
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
    appendManagedObjectPathElement((RelationDefinition) r);

    // Now add the single RDN representing the named instance.
    String type = profile.getInstantiableRelationChildRDNType(r);
    AttributeType atype = DirectoryServer.getAttributeType(type.toLowerCase());
    AttributeValue avalue = new AttributeValue(atype, name);
    dn = dn.concat(RDN.create(atype, avalue));
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
      DN localName = DN.decode(profile.getRelationRDNSequence(r));
      dn = dn.concat(localName);
    } catch (DirectoryException e) {
      throw new RuntimeException(e);
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
    appendManagedObjectPathElement((RelationDefinition) r);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
      void appendManagedObjectPathElement(
          SingletonRelationDefinition<? super C, ? super S> r,
          AbstractManagedObjectDefinition<C, S> d) {
    // Add the RDN sequence representing the relation.
    appendManagedObjectPathElement((RelationDefinition) r);
  }



  /**
   * Create a new DN using the current state of this DN builder.
   *
   * @return Returns the new DN instance.
   */
  public DN getInstance() {
    return dn;
  }
}
