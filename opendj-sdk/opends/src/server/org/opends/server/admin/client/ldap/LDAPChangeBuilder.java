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



import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.LdapName;

import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.client.Property;



/**
 * An LDAP managed object change builder.
 */
final class LDAPChangeBuilder {

  // The internal representation of the modified list.
  private final List<ModificationItem> modList;

  // The internal JNDI Dir context.
  private final DirContext ctx;

  // The managed object path.
  private final ManagedObjectPath path;

  // The managed object definition.
  private final ManagedObjectDefinition<?, ?> definition;

  // The LDAP profile to use for attribute name resolution.
  private final LDAPProfile profile;



  /**
   * Create a new LDAP based change builder.
   *
   * @param ctx
   *          The LDAP connection context.
   * @param path
   *          The managed object path.
   * @param definition
   *          The managed object definition.
   */
  public LDAPChangeBuilder(DirContext ctx, ManagedObjectPath path,
      ManagedObjectDefinition<?, ?> definition) {
    this.modList = new LinkedList<ModificationItem>();
    this.ctx = ctx;
    this.path = path;
    this.definition = definition;
    this.profile = LDAPProfile.getInstance();
  }



  /**
   * Update this builder with the set of changes which have been made to the
   * provided property.
   *
   * @param <T>
   *          The underlying type of the property.
   * @param property
   *          The property.
   */
  public <T> void addChange(Property<T> property) {
    PropertyDefinition<T> d = property.getPropertyDefinition();
    if (property.wasEmpty()) {
      // Property has been added.
      addPropertyValues(d, property.getPendingValues());
    } else if (property.isEmpty()) {
      // Property has been deleted.
      deletePropertyValues(d, property.getActiveValues());
    } else {
      // Property has been modified.
      modifyPropertyValues(d, property.getActiveValues(), property
          .getPendingValues());
    }
  }



  // A property has been added.
  private <T> void addPropertyValues(
      PropertyDefinition<T> d, Set<T> newValues) {
    String attrID = profile.getAttributeName(definition, d);
    BasicAttribute att = new BasicAttribute(attrID);
    for (T value : newValues) {
      att.add(d.encodeValue(value));
    }
    ModificationItem mod = new ModificationItem(DirContext.ADD_ATTRIBUTE, att);
    modList.add(mod);
  }



  // Property has been modified.
  private <T> void modifyPropertyValues(PropertyDefinition<T> d,
      Set<T> oldValues, Set<T> newValues) {
    // FIXME: be more sensible here. Only remove the values that need removing,
    // and only add the values that need adding.
    String attrID = profile.getAttributeName(definition, d);
    BasicAttribute att = new BasicAttribute(attrID);
    for (T value : newValues) {
      att.add(d.encodeValue(value));
    }
    ModificationItem mod = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
        att);
    modList.add(mod);
  }



  // Property has been deleted.
  private <T> void deletePropertyValues(PropertyDefinition<T> d,
      Set<T> oldValues) {
    String attrID = profile.getAttributeName(definition, d);
    BasicAttribute att = new BasicAttribute(attrID);
    ModificationItem mod = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
        att);
    modList.add(mod);
  }



  /**
   * Commit the changes.
   *
   * @throws OperationsException
   *           If the changes to this managed object could not be committed due
   *           to some underlying communication problem.
   */
  public void commit() throws OperationsException {
    ModificationItem[] mods = modList.toArray(new ModificationItem[modList
        .size()]);
    try {
      LdapName dn = LDAPNameBuilder.create(path);
      ctx.modifyAttributes(dn, mods);
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }
    return;
  }

}
