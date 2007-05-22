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

import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.InheritedDefaultValueProvider;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.StringPropertyProvider;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.Property;
import org.opends.server.admin.client.PropertySet;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.RootCfgDefn;



/**
 * A managed object bound to an LDAP connection.
 *
 * @param <C>
 *          The type of client configuration represented by the client
 *          managed object.
 */
final class LDAPManagedObject<C extends ConfigurationClient> implements
    ManagedObject<C> {

  /**
   * Internal inherited default value provider implementation.
   */
  private static class MyInheritedDefaultValueProvider implements
      InheritedDefaultValueProvider {

    // The LDAP management context.
    private final LDAPManagementContext context;

    // The base path.
    private final ManagedObjectPath path;



    /**
     * Create a new inherited default value provider.
     *
     * @param context
     *          The LDAP management context.
     * @param path
     *          The base path.
     */
    public MyInheritedDefaultValueProvider(LDAPManagementContext context,
        ManagedObjectPath path) {
      this.context = context;
      this.path = path;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<?> getDefaultPropertyValues(ManagedObjectPath path,
        String propertyName) throws OperationsException,
        PropertyNotFoundException {
      ManagedObjectPath<?, ?> tmp = path;
      ManagedObject<?> mo;
      try {
        mo = readEntry(context, tmp, tmp.getManagedObjectDefinition());
      } catch (AuthorizationException e) {
        throw new OperationsException(e);
      } catch (CommunicationException e) {
        throw new OperationsException(e);
      }
      ManagedObjectDefinition<?, ?> mod = mo.getManagedObjectDefinition();

      try {
        PropertyDefinition<?> dpd = mod.getPropertyDefinition(propertyName);
        return mo.getPropertyValues(dpd);
      } catch (IllegalArgumentException e) {
        throw new PropertyNotFoundException(propertyName);
      }
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectPath getManagedObjectPath() {
      return path;
    }
  }



  /**
   * Construct a root LDAP managed object associated with the provided
   * LDAP context.
   *
   * @param context
   *          The LDAP management context.
   * @return Returns a root LDAP managed object associated with the
   *         provided LDAP context.
   */
  static ManagedObject<RootCfgClient> getRootManagedObject(
      LDAPManagementContext context) {
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        context, ManagedObjectPath.emptyPath());
    PropertySet properties = PropertySet.create(RootCfgDefn.getInstance(),
        PropertyProvider.DEFAULT_PROVIDER, i, exceptions);

    // Should never get any exceptions.
    if (!exceptions.isEmpty()) {
      throw new RuntimeException(
          "Got exceptions when creating root managed object");
    }

    return new LDAPManagedObject<RootCfgClient>(context, RootCfgDefn
        .getInstance(), ManagedObjectPath.emptyPath(), properties);
  }



  // Create a new child LDAP managed object.
  private static <M extends ConfigurationClient>
      ManagedObject<M> createLDAPManagedObject(
      LDAPManagementContext context, ManagedObjectDefinition<M, ?> d,
      ManagedObjectPath p, PropertySet properties) {
    return new LDAPManagedObject<M>(context, d, p, properties);
  }



  // Determine the type of managed object associated with the named
  // entry.
  private static <M extends ConfigurationClient>
      ManagedObjectDefinition<? extends M, ?> getEntryDefinition(
      final LDAPManagementContext context,
      AbstractManagedObjectDefinition<M, ?> d, LdapName dn)
      throws NamingException, DefinitionDecodingException {
    Attributes attributes = context.getLDAPConnection().readEntry(dn,
        Collections.singleton("objectclass"));
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
        String objectClass = context.getLDAPProfile().getObjectClass(d);
        return objectClasses.contains(objectClass);
      }

    };

    return d.resolveManagedObjectDefinition(resolver);
  }



  // Read the entry identified by the path and which is a sub-type of
  // the specified definition.
  private static <M extends ConfigurationClient>
      ManagedObject<? extends M> readEntry(
      final LDAPManagementContext context, ManagedObjectPath p,
      AbstractManagedObjectDefinition<M, ?> d)
      throws DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, AuthorizationException,
      CommunicationException {
    LdapName dn = LDAPNameBuilder.create(p, context.getLDAPProfile());

    final ManagedObjectDefinition<? extends M, ?> mod;
    final Attributes attributes;
    try {
      mod = getEntryDefinition(context, d, dn);
      ArrayList<String> attrIds = new ArrayList<String>();
      for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
        String attrId = context.getLDAPProfile().getAttributeName(mod, pd);
        attrIds.add(attrId);
      }
      attributes = context.getLDAPConnection().readEntry(dn, attrIds);
    } catch (NameNotFoundException e) {
      throw new ManagedObjectNotFoundException();
    } catch (NoPermissionException e) {
      throw new AuthorizationException(e);
    } catch (NamingException e) {
      throw new CommunicationException(e);
    }

    // Create a provider which uses LDAP string representations.

    // TODO: the exception handling is a bit of a hack here.
    final List<NamingException> nelist = new LinkedList<NamingException>();
    StringPropertyProvider provider = new StringPropertyProvider() {

      public Collection<String> getPropertyValues(PropertyDefinition<?> d)
          throws IllegalArgumentException {
        String attrID = context.getLDAPProfile().getAttributeName(mod, d);
        Attribute attribute = attributes.get(attrID);
        List<String> values = new LinkedList<String>();

        if (attribute != null && attribute.size() != 0) {
          try {
            NamingEnumeration<?> ldapValues = attribute.getAll();
            while (ldapValues.hasMore()) {
              Object obj = ldapValues.next();
              if (obj != null) {
                values.add(obj.toString());
              }
            }
          } catch (NamingException e) {
            nelist.add(e);
          }
        }

        return values;
      }

    };

    // There can only be at most one exception.
    if (!nelist.isEmpty()) {
      try {
        throw nelist.get(0);
      } catch (NameNotFoundException e) {
        throw new ManagedObjectNotFoundException();
      } catch (NoPermissionException e) {
        throw new AuthorizationException(e);
      } catch (NamingException e) {
        throw new CommunicationException(e);
      }
    }

    // Now decode the properties using the provider.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        context, p);
    PropertySet properties = PropertySet.create(mod, provider, i, exceptions);
    ManagedObject<? extends M> mo = createLDAPManagedObject(context, mod, p,
        properties);

    // If there were no decoding problems then return the object,
    // otherwise throw an operations exception.
    if (exceptions.isEmpty()) {
      return mo;
    } else {
      throw new ManagedObjectDecodingException(mo, exceptions);
    }
  }

  // The managed object definition associated with this managed
  // object.
  private final ManagedObjectDefinition<C, ?> definition;

  // The LDAP management context used for the ldap connection.
  private final LDAPManagementContext context;

  // The path associated with this managed object.
  private final ManagedObjectPath<?, ?> path;

  // The managed object's properties.
  private final PropertySet properties;



  // Create an new LDAP managed object with the provided JNDI context.
  private LDAPManagedObject(LDAPManagementContext context,
      ManagedObjectDefinition<C, ?> d, ManagedObjectPath path,
      PropertySet properties) {
    this.definition = d;
    this.context = context;
    this.path = path;
    this.properties = properties;
  }



  /**
   * {@inheritDoc}
   */
  public void commit() throws ConcurrentModificationException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    // Build the list of modified attributes.
    ManagedObjectDefinition<C, ?> d = getManagedObjectDefinition();
    Attributes mods = new BasicAttributes();
    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      Property<?> p = properties.getProperty(pd);
      if (p.isModified()) {
        String attrID = context.getLDAPProfile().getAttributeName(d, pd);
        Attribute attribute = new BasicAttribute(attrID);
        encodeProperty(attribute, pd, properties);
        mods.put(attribute);
      }
    }

    // Perform the LDAP modification if something has changed.
    if (mods.size() > 0) {
      try {
        LdapName dn = LDAPNameBuilder.create(path, context.getLDAPProfile());
        context.getLDAPConnection().modifyEntry(dn, mods);
      } catch (NoPermissionException e) {
        throw new AuthorizationException(e);
      } catch (OperationNotSupportedException e) {
        // Unwilling to perform.
        throw new OperationRejectedException(e);
      } catch (NamingException e) {
        // Just treat it as a communication problem.
        throw new CommunicationException(e);
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      InstantiableRelationDefinition<M, ?> r, ManagedObjectDefinition<N, ?> d,
      String name, PropertyProvider p) throws IllegalArgumentException,
      ManagedObjectDecodingException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);

    ManagedObjectPath childPath = path.child(r, name);

    // First make sure all the properties are valid.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        context, childPath);
    PropertySet properties = PropertySet.create(d, p, i, exceptions);
    if (!exceptions.isEmpty()) {
      ManagedObject<N> mo = new LDAPManagedObject<N>(context, d, childPath,
          properties);
      throw new ManagedObjectDecodingException(mo, exceptions);
    }

    ensureThisManagedObjectExists();

    // TODO: this implementation does not handle relations which
    // comprise of more than one RDN arc (this will probably never
    // be required anyway).
    LdapName dn = LDAPNameBuilder.create(path, r, context.getLDAPProfile());
    if (!entryExists(dn)) {
      // Need to create the child managed object's parent entry i.e.
      // the entry representing the relation itself.
      Attributes attributes = new BasicAttributes();

      // Create the branch's object class attribute.
      Attribute oc = new BasicAttribute("objectClass");
      for (String objectClass : context.getLDAPProfile()
          .getInstantiableRelationObjectClasses(r)) {
        oc.add(objectClass);
      }
      attributes.put(oc);

      // Create the branch's naming attribute.
      Rdn rdn = dn.getRdn(dn.size() - 1);
      attributes.put(rdn.getType(), rdn.getValue().toString());

      // Create the entry.
      try {
        context.getLDAPConnection().createEntry(dn, attributes);
      } catch (OperationNotSupportedException e) {
        // Unwilling to perform.
        throw new OperationRejectedException(e);
      } catch (NamingException e) {
        adaptNamingException(e);
      }
    }

    return createManagedObject(childPath, d, properties);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      OptionalRelationDefinition<M, ?> r, ManagedObjectDefinition<N, ?> d,
      PropertyProvider p) throws IllegalArgumentException,
      ManagedObjectDecodingException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);

    ManagedObjectPath childPath = path.child(r);

    // First make sure all the properties are valid.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        context, childPath);
    PropertySet properties = PropertySet.create(d, p, i, exceptions);
    if (!exceptions.isEmpty()) {
      ManagedObject<N> mo = new LDAPManagedObject<N>(context, d, childPath,
          properties);
      throw new ManagedObjectDecodingException(mo, exceptions);
    }

    ensureThisManagedObjectExists();

    return createManagedObject(childPath, d, properties);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();
    return readEntry(context, path.child(d, name), d.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      OptionalRelationDefinition<M, ?> d) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();
    return readEntry(context, path.child(d), d.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      SingletonRelationDefinition<M, ?> d) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();
    return readEntry(context, path.child(d), d.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public C getConfiguration() {
    return definition.createClientConfiguration(this);
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObjectDefinition<C, ?> getManagedObjectDefinition() {
    return definition;
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObjectPath getManagedObjectPath() {
    return path;
  }



  /**
   * {@inheritDoc}
   */
  public <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValue(d);
  }



  /**
   * {@inheritDoc}
   */
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValues(d);
  }



  /**
   * {@inheritDoc}
   */
  public boolean hasChild(OptionalRelationDefinition<?, ?> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();

    ManagedObjectPath p = path.child(d);
    LdapName dn = LDAPNameBuilder.create(p, context.getLDAPProfile());
    return entryExists(dn);
  }



  /**
   * {@inheritDoc}
   */
  public String[] listChildren(InstantiableRelationDefinition<?, ?> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();

    List<String> children = new ArrayList<String>();
    LdapName dn = LDAPNameBuilder.create(path, d, context.getLDAPProfile());
    try {
      for (LdapName child : context.getLDAPConnection().listEntries(dn)) {
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
  public <M extends ConfigurationClient> void removeChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();
    ManagedObjectPath p = path.child(d, name);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> void removeChild(
      OptionalRelationDefinition<M, ?> d) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(d);
    ensureThisManagedObjectExists();
    ManagedObjectPath p = path.child(d);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    properties.setPropertyValue(d, value);
  }



  /**
   * {@inheritDoc}
   */
  public <T> void setPropertyValues(PropertyDefinition<T> d,
      Collection<T> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    properties.setPropertyValues(d, values);
  }



  // Adapts a naming exception to an appropriate admin client
  // exception.
  private void adaptNamingException(NamingException ne)
      throws CommunicationException, AuthorizationException {
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



  // Creates a new managed object. The parent LDAP entry is assumed to
  // already exist.
  private <N extends ConfigurationClient> ManagedObject<N> createManagedObject(
      ManagedObjectPath path, ManagedObjectDefinition<N, ?> d,
      PropertySet properties) throws ManagedObjectAlreadyExistsException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    LdapName dn = LDAPNameBuilder.create(path, context.getLDAPProfile());
    Attributes attributes = new BasicAttributes(true);

    // Create the child's object class attribute.
    Attribute oc = new BasicAttribute("objectclass");
    for (String objectClass : context.getLDAPProfile().getObjectClasses(d)) {
      oc.add(objectClass);
    }
    attributes.put(oc);

    // Create the child's naming attribute.
    Rdn rdn = dn.getRdn(dn.size() - 1);
    attributes.put(rdn.getType(), rdn.getValue().toString());

    // Create the remaining attributes.
    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      String attrID = context.getLDAPProfile().getAttributeName(d, pd);
      Attribute attribute = new BasicAttribute(attrID);
      encodeProperty(attribute, pd, properties);
      if (attribute.size() != 0) {
        attributes.put(attribute);
      }
    }

    try {
      // Create the entry.
      context.getLDAPConnection().createEntry(dn, attributes);
    } catch (NameAlreadyBoundException e) {
      throw new ManagedObjectAlreadyExistsException();
    } catch (OperationNotSupportedException e) {
      // Unwilling to perform.
      throw new OperationRejectedException(e);
    } catch (NamingException e) {
      adaptNamingException(e);
    }

    return new LDAPManagedObject<N>(context, d, path, properties);
  }



  // Encode a property into LDAP string values.
  private <T> void encodeProperty(Attribute attribute,
      PropertyDefinition<T> pd, PropertySet properties) {
    Property<T> p = properties.getProperty(pd);
    for (T value : p.getPendingValues()) {
      attribute.add(pd.encodeValue(value));
    }
  }



  // Makes sure that the entry corresponding to this managed object
  // exists.
  private void ensureThisManagedObjectExists()
      throws ConcurrentModificationException, CommunicationException,
      AuthorizationException {
    LdapName dn = LDAPNameBuilder.create(path, context.getLDAPProfile());
    if (!entryExists(dn)) {
      throw new ConcurrentModificationException();
    }
  }



  // Determine whether the named entry exists or not.
  private boolean entryExists(LdapName dn) throws CommunicationException,
      AuthorizationException {
    try {
      return context.getLDAPConnection().entryExists(dn);
    } catch (NamingException e) {
      adaptNamingException(e);
    }
    return false;
  }



  // Remove the named managed object.
  private void removeManagedObject(ManagedObjectPath p)
      throws CommunicationException, AuthorizationException,
      OperationRejectedException {
    LdapName dn = LDAPNameBuilder.create(p, context.getLDAPProfile());
    if (entryExists(dn)) {
      // Delete the entry and any subordinate entries.
      try {
        context.getLDAPConnection().deleteSubtree(dn);
      } catch (OperationNotSupportedException e) {
        // Unwilling to perform.
        throw new OperationRejectedException(e);
      } catch (NamingException e) {
        adaptNamingException(e);
      }
    }
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    ManagedObjectDefinition d = getManagedObjectDefinition();
    RelationDefinition tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + d.getName());
    }
  }
}
