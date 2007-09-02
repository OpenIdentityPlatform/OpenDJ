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



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.spi.Driver;
import org.opends.server.admin.client.spi.PropertySet;



/**
 * The LDAP management context driver implementation.
 */
final class LDAPDriver extends Driver {

  // The LDAP connection.
  private final LDAPConnection connection;

  // The LDAP profile which should be used to construct LDAP
  // requests and decode LDAP responses.
  private final LDAPProfile profile;



  /**
   * Creates a new LDAP driver using the specified LDAP connection and
   * profile.
   *
   * @param connection
   *          The LDAP connection.
   * @param profile
   *          The LDAP profile.
   */
  public LDAPDriver(LDAPConnection connection, LDAPProfile profile) {
    this.connection = connection;
    this.profile = profile;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      String name) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(parent, rd);

    if (!managedObjectExists(parent)) {
      throw new ManagedObjectNotFoundException();
    }

    return removeManagedObject(parent.child(rd, name));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(parent, rd);

    if (!managedObjectExists(parent)) {
      throw new ManagedObjectNotFoundException();
    }

    return removeManagedObject(parent.child(rd));
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getManagedObject(
      ManagedObjectPath<C, S> path) throws DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    if (!managedObjectExists(path)) {
      throw new ManagedObjectNotFoundException();
    }

    try {
      // Read the entry associated with the managed object.
      LdapName dn = LDAPNameBuilder.create(path, profile);
      AbstractManagedObjectDefinition<C, S> d = path
          .getManagedObjectDefinition();
      ManagedObjectDefinition<? extends C, ? extends S> mod =
        getEntryDefinition(d, dn);

      ArrayList<String> attrIds = new ArrayList<String>();
      for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
        String attrId = profile.getAttributeName(mod, pd);
        attrIds.add(attrId);
      }

      Attributes attributes = connection.readEntry(dn, attrIds);

      // Build the managed object's properties.
      List<PropertyException> exceptions = new LinkedList<PropertyException>();
      PropertySet newProperties = new PropertySet();
      for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
        String attrID = profile.getAttributeName(mod, pd);
        Attribute attribute = attributes.get(attrID);
        List<String> values = new LinkedList<String>();

        if (attribute != null && attribute.size() != 0) {
          NamingEnumeration<?> ldapValues = attribute.getAll();
          while (ldapValues.hasMore()) {
            Object obj = ldapValues.next();
            if (obj != null) {
              values.add(obj.toString());
            }
          }
        }

        try {
          decodeProperty(newProperties, path, pd, values);
        } catch (PropertyException e) {
          exceptions.add(e);
        }
      }

      // If there were no decoding problems then return the object,
      // otherwise throw an operations exception.
      ManagedObject<? extends C> mo = createExistingManagedObject(mod, path,
          newProperties);
      if (exceptions.isEmpty()) {
        return mo;
      } else {
        throw new ManagedObjectDecodingException(mo, exceptions);
      }
    } catch (NameNotFoundException e) {
      throw new ManagedObjectNotFoundException();
    } catch (NoPermissionException e) {
      throw new AuthorizationException(e);
    } catch (NamingException e) {
      throw new CommunicationException(e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <PD> SortedSet<PD> getPropertyValues(ManagedObjectPath<?, ?> path,
      PropertyDefinition<PD> pd) throws IllegalArgumentException,
      DefinitionDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, CommunicationException,
      PropertyException {
    if (!managedObjectExists(path)) {
      throw new ManagedObjectNotFoundException();
    }

    try {
      // Read the entry associated with the managed object.
      LdapName dn = LDAPNameBuilder.create(path, profile);
      AbstractManagedObjectDefinition<?, ?> d = path
          .getManagedObjectDefinition();
      ManagedObjectDefinition<?, ?> mod = getEntryDefinition(d, dn);

      String attrID = profile.getAttributeName(mod, pd);
      Attributes attributes = connection.readEntry(dn, Collections
          .singleton(attrID));
      Attribute attribute = attributes.get(attrID);

      SortedSet<PD> values = new TreeSet<PD>(pd);
      if (attribute == null || attribute.size() == 0) {
        // Use the property's default values.
        values.addAll(findDefaultValues(path, pd, false));
      } else {
        // Decode the values.
        NamingEnumeration<?> ldapValues = attribute.getAll();
        while (ldapValues.hasMore()) {
          Object obj = ldapValues.next();
          if (obj != null) {
            PD value = pd.decodeValue(obj.toString());
            values.add(value);
          }
        }
      }

      // Sanity check the returned values.
      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        throw new PropertyIsSingleValuedException(pd);
      }

      if (values.isEmpty() && pd.hasOption(PropertyOption.MANDATORY)) {
        throw new PropertyIsMandatoryException(pd);
      }

      return values;
    } catch (NameNotFoundException e) {
      throw new ManagedObjectNotFoundException();
    } catch (NoPermissionException e) {
      throw new AuthorizationException(e);
    } catch (NamingException e) {
      throw new CommunicationException(e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(parent, rd);

    if (!managedObjectExists(parent)) {
      throw new ManagedObjectNotFoundException();
    }

    // Get the search base DN.
    LdapName dn = LDAPNameBuilder.create(parent, rd, profile);

    // Retrieve only those entries which are sub-types of the
    // specified definition.
    StringBuilder builder = new StringBuilder();
    builder.append("(objectclass=");
    builder.append(profile.getObjectClass(d));
    builder.append(')');
    String filter = builder.toString();

    List<String> children = new ArrayList<String>();
    try {
      for (LdapName child : connection.listEntries(dn, filter)) {
        children.add(child.getRdn(child.size() - 1).getValue().toString());
      }
    } catch (NameNotFoundException e) {
      // Ignore this - it means that the base entry does not exist
      // (which it might not if this managed object has just been
      // created.
    } catch (NamingException e) {
      adaptNamingException(e);
    }

    return children.toArray(new String[children.size()]);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean managedObjectExists(ManagedObjectPath<?, ?> path)
      throws ManagedObjectNotFoundException, AuthorizationException,
      CommunicationException {
    if (path.isEmpty()) {
      return true;
    }

    ManagedObjectPath<?,?> parent = path.parent();
    LdapName dn = LDAPNameBuilder.create(parent, profile);
    if (!entryExists(dn)) {
      throw new ManagedObjectNotFoundException();
    }

    dn = LDAPNameBuilder.create(path, profile);
    return entryExists(dn);
  }



  /**
   * Adapts a naming exception to an appropriate admin client
   * exception.
   *
   * @param ne
   *          The naming exception.
   * @throws CommunicationException
   *           If the naming exception mapped to a communication
   *           exception.
   * @throws AuthorizationException
   *           If the naming exception mapped to an authorization
   *           exception.
   */
  void adaptNamingException(NamingException ne) throws CommunicationException,
      AuthorizationException {
    try {
      throw ne;
    } catch (javax.naming.CommunicationException e) {
      throw new CommunicationException(e);
    } catch (javax.naming.ServiceUnavailableException e) {
      throw new CommunicationException(e);
    } catch (javax.naming.NoPermissionException e) {
      throw new AuthorizationException(e);
    } catch (NamingException e) {
      // Just treat it as a communication problem.
      throw new CommunicationException(e);
    }
  }



  /**
   * Determines whether the named LDAP entry exists.
   *
   * @param dn
   *          The LDAP entry name.
   * @return Returns <code>true</code> if the named LDAP entry
   *         exists.
   * @throws AuthorizationException
   *           If the server refuses to make the determination because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  boolean entryExists(LdapName dn) throws CommunicationException,
      AuthorizationException {
    try {
      return connection.entryExists(dn);
    } catch (NamingException e) {
      adaptNamingException(e);
    }
    return false;
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



  // Create a managed object which already exists on the server.
  private <M extends ConfigurationClient, N extends Configuration>
  ManagedObject<M> createExistingManagedObject(
      ManagedObjectDefinition<M, N> d,
      ManagedObjectPath<? super M, ? super N> p, PropertySet properties) {
    RelationDefinition<?, ?> rd = p.getRelationDefinition();
    PropertyDefinition<?> pd = null;
    if (rd instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<?, ?> ird =
        (InstantiableRelationDefinition<?, ?>) rd;
      pd = ird.getNamingPropertyDefinition();
    }
    return new LDAPManagedObject<M>(this, d, p.asSubType(d), properties, true,
        pd);
  }



  // Create a property using the provided string values.
  private <PD> void decodeProperty(PropertySet newProperties,
      ManagedObjectPath<?, ?> p, PropertyDefinition<PD> pd, List<String> values)
      throws PropertyException {
    PropertyException exception = null;

    // Get the property's active values.
    Collection<PD> activeValues = new ArrayList<PD>(values.size());
    for (String value : values) {
      try {
        activeValues.add(pd.decodeValue(value));
      } catch (IllegalPropertyValueStringException e) {
        exception = e;
      }
    }

    if (activeValues.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
      // This exception takes precedence over previous exceptions.
      exception = new PropertyIsSingleValuedException(pd);
      PD value = activeValues.iterator().next();
      activeValues.clear();
      activeValues.add(value);
    }

    if (activeValues.isEmpty() && pd.hasOption(PropertyOption.MANDATORY)) {
      // The active values maybe empty because of a previous
      // exception.
      if (exception == null) {
        exception = new PropertyIsMandatoryException(pd);
      }
    }

    // Get the property's default values.
    Collection<PD> defaultValues;
    try {
      defaultValues = findDefaultValues(p, pd, false);
    } catch (DefaultBehaviorException e) {
      defaultValues = Collections.emptySet();
      exception = e;
    }

    newProperties.addProperty(pd, defaultValues, activeValues);
    if (exception != null) {
      throw exception;
    }
  }



  // Determine the type of managed object associated with the named
  // entry.
  private <C extends ConfigurationClient, S extends Configuration>
  ManagedObjectDefinition<? extends C, ? extends S> getEntryDefinition(
      AbstractManagedObjectDefinition<C, S> d, LdapName dn)
      throws NamingException, DefinitionDecodingException {
    Attributes attributes = connection.readEntry(dn, Collections
        .singleton("objectclass"));
    Attribute oc = attributes.get("objectclass");

    if (oc == null) {
      // No object classes.
      throw new DefinitionDecodingException(Reason.NO_TYPE_INFORMATION);
    }

    final Set<String> objectClasses = new HashSet<String>();
    NamingEnumeration<?> values = oc.getAll();
    while (values.hasMore()) {
      Object value = values.next();
      if (value != null) {
        objectClasses.add(value.toString().toLowerCase().trim());
      }
    }

    if (objectClasses.isEmpty()) {
      // No object classes.
      throw new DefinitionDecodingException(Reason.NO_TYPE_INFORMATION);
    }

    // Resolve the appropriate sub-type based on the object classes.
    DefinitionResolver resolver = new DefinitionResolver() {

      public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
        String objectClass = profile.getObjectClass(d);
        return objectClasses.contains(objectClass);
      }

    };

    return d.resolveManagedObjectDefinition(resolver);
  }



  // Remove the named managed object.
  private boolean removeManagedObject(ManagedObjectPath<?, ?> path)
      throws CommunicationException, AuthorizationException,
      OperationRejectedException, ManagedObjectNotFoundException {
    if (!managedObjectExists(path)) {
      return false;
    }

    // Delete the entry and any subordinate entries.
    LdapName dn = LDAPNameBuilder.create(path, profile);
    try {
      connection.deleteSubtree(dn);
    } catch (OperationNotSupportedException e) {
      // Unwilling to perform.
      throw new OperationRejectedException(e);
    } catch (NamingException e) {
      adaptNamingException(e);
    }

    return true;
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> rd) throws IllegalArgumentException {
    AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + d.getName());
    }
  }
}
