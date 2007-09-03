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



import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.spi.AbstractManagedObject;
import org.opends.server.admin.client.spi.Driver;
import org.opends.server.admin.client.spi.Property;
import org.opends.server.admin.client.spi.PropertySet;



/**
 * A managed object bound to an LDAP connection.
 *
 * @param <T>
 *          The type of client configuration represented by the client
 *          managed object.
 */
final class LDAPManagedObject<T extends ConfigurationClient> extends
    AbstractManagedObject<T> {

  // The LDAP management driver associated with this managed object.
  private final LDAPDriver driver;



  /**
   * Creates a new LDAP managed object instance.
   *
   * @param driver
   *          The underlying LDAP management driver.
   * @param d
   *          The managed object's definition.
   * @param path
   *          The managed object's path.
   * @param properties
   *          The managed object's properties.
   * @param existsOnServer
   *          Indicates whether or not the managed object already
   *          exists.
   * @param namingPropertyDefinition
   *          The managed object's naming property definition if there
   *          is one.
   */
  LDAPManagedObject(LDAPDriver driver,
      ManagedObjectDefinition<T, ? extends Configuration> d,
      ManagedObjectPath<T, ? extends Configuration> path,
      PropertySet properties, boolean existsOnServer,
      PropertyDefinition<?> namingPropertyDefinition) {
    super(d, path, properties, existsOnServer, namingPropertyDefinition);
    this.driver = driver;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void addNewManagedObject() throws AuthorizationException,
      CommunicationException, OperationRejectedException,
      ConcurrentModificationException, ManagedObjectAlreadyExistsException {
    // First make sure that the parent managed object still exists.
    ManagedObjectPath<?, ?> path = getManagedObjectPath();
    ManagedObjectPath<?, ?> parent = path.parent();

    try {
      if (!driver.managedObjectExists(parent)) {
        throw new ConcurrentModificationException();
      }
    } catch (ManagedObjectNotFoundException e) {
      throw new ConcurrentModificationException();
    }

    // We may need to create the parent "relation" entry if this is a
    // child of an instantiable relation.
    RelationDefinition<?, ?> r = path.getRelationDefinition();
    if (r instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<?, ?> ir =
        (InstantiableRelationDefinition<?, ?>) r;

      // TODO: this implementation does not handle relations which
      // comprise of more than one RDN arc (this will probably never
      // be required anyway).
      LdapName dn = LDAPNameBuilder
          .create(parent, ir, driver.getLDAPProfile());
      if (!driver.entryExists(dn)) {
        // We need to create the entry.
        Attributes attributes = new BasicAttributes();

        // Create the branch's object class attribute.
        Attribute oc = new BasicAttribute("objectClass");
        for (String objectClass : driver.getLDAPProfile()
            .getInstantiableRelationObjectClasses(ir)) {
          oc.add(objectClass);
        }
        attributes.put(oc);

        // Create the branch's naming attribute.
        Rdn rdn = dn.getRdn(dn.size() - 1);
        attributes.put(rdn.getType(), rdn.getValue().toString());

        // Create the entry.
        try {
          driver.getLDAPConnection().createEntry(dn, attributes);
        } catch (OperationNotSupportedException e) {
          // Unwilling to perform.
          if (e.getMessage() != null) {
            throw new OperationRejectedException();
          } else {
            Message m = Message.raw("%s", e.getMessage());
            throw new OperationRejectedException(m);
          }
        } catch (NamingException e) {
          driver.adaptNamingException(e);
        }
      }
    }

    // Now add the entry representing this new managed object.
    LdapName dn = LDAPNameBuilder.create(path, driver.getLDAPProfile());
    Attributes attributes = new BasicAttributes(true);

    // Create the object class attribute.
    Attribute oc = new BasicAttribute("objectclass");
    ManagedObjectDefinition<?, ?> definition = getManagedObjectDefinition();
    for (String objectClass : driver.getLDAPProfile().getObjectClasses(
        definition)) {
      oc.add(objectClass);
    }
    attributes.put(oc);

    // Create the naming attribute if there is not naming property.
    PropertyDefinition<?> npd = getNamingPropertyDefinition();
    if (npd == null) {
      Rdn rdn = dn.getRdn(dn.size() - 1);
      attributes.put(rdn.getType(), rdn.getValue().toString());
    }

    // Create the remaining attributes.
    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      String attrID = driver.getLDAPProfile().getAttributeName(definition, pd);
      Attribute attribute = new BasicAttribute(attrID);
      encodeProperty(attribute, pd);
      if (attribute.size() != 0) {
        attributes.put(attribute);
      }
    }

    try {
      // Create the entry.
      driver.getLDAPConnection().createEntry(dn, attributes);
    } catch (NameAlreadyBoundException e) {
      throw new ManagedObjectAlreadyExistsException();
    } catch (OperationNotSupportedException e) {
      // Unwilling to perform.
      if (e.getMessage() != null) {
        throw new OperationRejectedException();
      } else {
        Message m = Message.raw("%s", e.getMessage());
        throw new OperationRejectedException(m);
      }
    } catch (NamingException e) {
      driver.adaptNamingException(e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected Driver getDriver() {
    return driver;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected void modifyExistingManagedObject()
      throws ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    // Build the list of modified attributes.
    Attributes mods = new BasicAttributes();
    ManagedObjectDefinition<?, ?> definition = getManagedObjectDefinition();
    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      Property<?> p = getProperty(pd);
      if (p.isModified()) {
        String attrID = driver.getLDAPProfile().getAttributeName(definition,
            pd);
        Attribute attribute = new BasicAttribute(attrID);
        encodeProperty(attribute, pd);
        mods.put(attribute);
      }
    }

    // Perform the LDAP modification if something has changed.
    if (mods.size() > 0) {
      try {
        ManagedObjectPath<?, ?> path = getManagedObjectPath();
        LdapName dn = LDAPNameBuilder.create(path, driver.getLDAPProfile());
        driver.getLDAPConnection().modifyEntry(dn, mods);
      } catch (NoPermissionException e) {
        throw new AuthorizationException(e);
      } catch (OperationNotSupportedException e) {
        // Unwilling to perform.
        if (e.getMessage() != null) {
          throw new OperationRejectedException();
        } else {
          Message m = Message.raw("%s", e.getMessage());
          throw new OperationRejectedException(m);
        }
      } catch (NamingException e) {
        // Just treat it as a communication problem.
        throw new CommunicationException(e);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected <M extends ConfigurationClient> ManagedObject<M> newInstance(
      ManagedObjectDefinition<M, ?> d, ManagedObjectPath<M, ?> path,
      PropertySet properties, boolean existsOnServer,
      PropertyDefinition<?> namingPropertyDefinition) {
    return new LDAPManagedObject<M>(driver, d, path, properties,
        existsOnServer, namingPropertyDefinition);
  }



  // Encode a property into LDAP string values.
  private <PD> void encodeProperty(Attribute attribute,
      PropertyDefinition<PD> pd) {
    Property<PD> p = getProperty(pd);
    if (pd.hasOption(PropertyOption.MANDATORY)) {
      // For mandatory properties we fall-back to the default values
      // if defined which can sometimes be the case e.g when a
      // mandatory property is overridden.
      for (PD value : p.getEffectiveValues()) {
        attribute.add(pd.encodeValue(value));
      }
    } else {
      for (PD value : p.getPendingValues()) {
        attribute.add(pd.encodeValue(value));
      }
    }
  }

}
