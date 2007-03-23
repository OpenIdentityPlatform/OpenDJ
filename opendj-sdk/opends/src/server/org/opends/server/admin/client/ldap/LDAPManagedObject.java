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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.InheritedDefaultValueProvider;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
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
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
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
final class LDAPManagedObject<C extends ConfigurationClient>
    implements ManagedObject<C> {

  /**
   * Internal inherited default value provider implementation.
   */
  private static class MyInheritedDefaultValueProvider implements
      InheritedDefaultValueProvider {

    // The base path.
    private final ManagedObjectPath path;

    // The LDAP connection context.
    private final DirContext dirContext;



    /**
     * Create a new inherited default value provider.
     *
     * @param dirContext
     *          The LDAP connection context.
     * @param path
     *          The base path.
     */
    public MyInheritedDefaultValueProvider(DirContext dirContext,
        ManagedObjectPath path) {
      this.dirContext = dirContext;
      this.path = path;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<?> getDefaultPropertyValues(
        ManagedObjectPath path, String propertyName)
        throws OperationsException, PropertyNotFoundException {
      ManagedObject<?> mo = readEntry(dirContext, path, path
          .getManagedObjectDefinition());
      ManagedObjectDefinition<?, ?> mod = mo
          .getManagedObjectDefinition();

      try {
        PropertyDefinition<?> dpd = mod
            .getPropertyDefinition(propertyName);
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
   * @param dirContext
   *          The LDAP directory context.
   * @return Returns a root LDAP managed object associated with the
   *         provided LDAP context.
   */
  static ManagedObject<RootCfgClient> getRootManagedObject(
      DirContext dirContext) {
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        dirContext, ManagedObjectPath.emptyPath());
    PropertySet properties = PropertySet.create(RootCfgDefn
        .getInstance(), PropertyProvider.DEFAULT_PROVIDER, i,
        exceptions);

    // Should never get any exceptions.
    if (!exceptions.isEmpty()) {
      throw new RuntimeException(
          "Got exceptions when creating root managed object");
    }

    return new LDAPManagedObject<RootCfgClient>(dirContext,
        RootCfgDefn.getInstance(), ManagedObjectPath.emptyPath(),
        properties);
  }



  // Create a new child LDAP managed object.
  private static <M extends ConfigurationClient>
    ManagedObject<M> createLDAPManagedObject(
      DirContext dirContext, ManagedObjectDefinition<M, ?> d,
      ManagedObjectPath p, PropertySet properties) {
    return new LDAPManagedObject<M>(dirContext, d, p, properties);
  }



  // Get an array containing the list of LDAP attributes names
  // associated with a
  // managed object definition.
  private static String[] getAttributeNames(
      ManagedObjectDefinition<?, ?> d) {
    ArrayList<String> attrIds = new ArrayList<String>();

    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      String attrId = LDAPProfile.getInstance().getAttributeName(d,
          pd);
      attrIds.add(attrId);
    }

    return attrIds.toArray(new String[attrIds.size()]);
  }



  // Determine the type of managed object associated with the named
  // entry.
  private static <M extends ConfigurationClient>
      ManagedObjectDefinition<? extends M, ?> getEntryDefinition(
      DirContext dirContext, AbstractManagedObjectDefinition<M, ?> d,
      LdapName dn) throws OperationsException {
    try {
      String[] attrIds = new String[] { "objectclass" };
      Attributes attributes = dirContext.getAttributes(dn, attrIds);
      Attribute oc = attributes.get("objectclass");

      if (oc == null) {
        // No object classes.
        throw new DefinitionDecodingException(
            Reason.NO_TYPE_INFORMATION);
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
        throw new DefinitionDecodingException(
            Reason.NO_TYPE_INFORMATION);
      }

      // Resolve the appropriate sub-type based on the object classes.
      DefinitionResolver resolver = new DefinitionResolver() {

        public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
          String objectClass = LDAPProfile.getInstance()
              .getObjectClass(d);
          return objectClasses.contains(objectClass);
        }

      };

      return d.resolveManagedObjectDefinition(resolver);
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }
  }



  // Read the entry identified by the path and which is a sub-type of
  // the
  // specified definition.
  private static <M extends ConfigurationClient>
      ManagedObject<? extends M> readEntry(
      DirContext dirContext, ManagedObjectPath p,
      AbstractManagedObjectDefinition<M, ?> d)
      throws OperationsException {
    LdapName dn = LDAPNameBuilder.create(p);
    final ManagedObjectDefinition<? extends M, ?> mod = getEntryDefinition(
        dirContext, d, dn);
    String[] attrIds = getAttributeNames(mod);

    final Attributes attributes;
    try {
      attributes = dirContext.getAttributes(dn, attrIds);
    } catch (NamingException e) {
      OperationsExceptionFactory factory = new OperationsExceptionFactory();
      throw factory.createException(e);
    }

    // Create a provider which uses LDAP string representations.

    // TODO: the exception handling is a bit of a hack here.
    final List<OperationsException> oelist =
      new LinkedList<OperationsException>();
    StringPropertyProvider provider = new StringPropertyProvider() {

      public Collection<String> getPropertyValues(
          PropertyDefinition<?> d) throws IllegalArgumentException {
        String attrID = LDAPProfile.getInstance().getAttributeName(
            mod, d);
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
            OperationsExceptionFactory oef = new OperationsExceptionFactory();
            oelist.add(oef.createException(e));
          }
        }

        return values;
      }

    };

    // There can only be at most one operations exception.
    if (!oelist.isEmpty()) {
      throw oelist.get(0);
    }

    // Now decode the properties using the provider.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        dirContext, p);
    PropertySet properties = PropertySet.create(mod, provider, i,
        exceptions);
    ManagedObject<? extends M> mo = createLDAPManagedObject(
        dirContext, mod, p, properties);

    // If there were no decoding problems then return the object,
    // otherwise
    // throw an operations exception.
    if (exceptions.isEmpty()) {
      return mo;
    } else {
      throw new ManagedObjectDecodingException(mo, exceptions);
    }
  }

  // The JNDI context used for the ldap connection.
  private final DirContext dirContext;

  // The path associated with this managed object.
  private final ManagedObjectPath path;

  // LDAP profile associated with this connection.
  private final LDAPProfile profile;

  // The managed object definition associated with this managed
  // object.
  private final ManagedObjectDefinition<C, ?> definition;

  // The managed object's properties.
  private final PropertySet properties;



  // Create an new LDAP managed object with the provided JNDI context.
  private LDAPManagedObject(DirContext dirContext,
      ManagedObjectDefinition<C, ?> d, ManagedObjectPath path,
      PropertySet properties) {
    this.definition = d;
    this.dirContext = dirContext;
    this.path = path;
    this.profile = LDAPProfile.getInstance();
    this.properties = properties;
  }



  /**
   * {@inheritDoc}
   */
  public void commit() throws OperationsException {
    ManagedObjectDefinition<C, ?> d = getManagedObjectDefinition();
    LDAPChangeBuilder builder = new LDAPChangeBuilder(dirContext,
        path, d);
    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      // FIXME: should throw an error when there are missing mandatory
      // properties.
      Property<?> p = properties.getProperty(pd);
      if (p.isModified()) {
        builder.addChange(p);
      }
    }
    builder.commit();
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      InstantiableRelationDefinition<M, ?> r,
      ManagedObjectDefinition<N, ?> d, String name, PropertyProvider p)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(r);

    ManagedObjectPath childPath = path.child(r, name);

    // First make sure all the properties are valid.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        dirContext, childPath);
    PropertySet properties = PropertySet.create(d, p, i, exceptions);
    if (!exceptions.isEmpty()) {
      ManagedObject<N> mo = new LDAPManagedObject<N>(dirContext, d,
          childPath, properties);
      throw new ManagedObjectDecodingException(mo, exceptions);
    }

    try {
      // TODO: this implementation does not handle relations which
      // comprise of more than one RDN arc (this will probably never
      // be required anyway).
      LdapName dn = LDAPNameBuilder.create(path, r);

      if (!entryExists(dn)) {
        // Need to create the child managed object's parent entry i.e.
        // the entry representing the relation itself.
        Attributes attributes = new BasicAttributes();

        // Create the branch's object class attribute.
        Attribute oc = new BasicAttribute("objectClass");
        for (String objectClass : profile
            .getInstantiableRelationObjectClasses(r)) {
          oc.add(objectClass);
        }
        attributes.put(oc);

        // Create the branch's naming attribute.
        Rdn rdn = dn.getRdn(dn.size() - 1);
        attributes.put(rdn.getType(), rdn.getValue().toString());

        // Create the entry.
        dirContext.createSubcontext(dn, attributes);
      }
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }

    return createManagedObject(childPath, d, properties);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      OptionalRelationDefinition<M, ?> r,
      ManagedObjectDefinition<N, ?> d, PropertyProvider p)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(r);

    ManagedObjectPath childPath = path.child(r);

    // First make sure all the properties are valid.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        dirContext, childPath);
    PropertySet properties = PropertySet.create(d, p, i, exceptions);
    if (!exceptions.isEmpty()) {
      ManagedObject<N> mo = new LDAPManagedObject<N>(dirContext, d,
          childPath, properties);
      throw new ManagedObjectDecodingException(mo, exceptions);
    }

    return createManagedObject(childPath, d, properties);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);
    return readEntry(dirContext, path.child(d, name), d
        .getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      OptionalRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);
    return readEntry(dirContext, path.child(d), d
        .getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      SingletonRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);
    return readEntry(dirContext, path.child(d), d
        .getChildDefinition());
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
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);

    ManagedObjectPath p = path.child(d);
    LdapName dn = LDAPNameBuilder.create(p);
    try {
      return entryExists(dn);
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public String[] listChildren(InstantiableRelationDefinition<?, ?> d)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);

    LdapName dn = LDAPNameBuilder.create(path, d);

    String filter = profile.getFilter(d.getChildDefinition());
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

    try {
      NamingEnumeration<SearchResult> results;
      results = dirContext.search(dn, filter, controls);

      String rtype = profile.getInstantiableRelationChildRDNType(d);
      List<String> names = new ArrayList<String>();
      while (results.hasMore()) {
        SearchResult sr = results.next();
        Attributes attributes = sr.getAttributes();
        Attribute cn = attributes.get(rtype);
        if (cn != null && cn.get() != null) {
          names.add(cn.get().toString());
        }
      }

      return names.toArray(new String[names.size()]);
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> void removeChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);

    ManagedObjectPath p = path.child(d, name);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <M extends ConfigurationClient> void removeChild(
      OptionalRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    validateRelationDefinition(d);

    ManagedObjectPath p = path.child(d);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException,
      PropertyIsReadOnlyException, PropertyIsMandatoryException,
      IllegalArgumentException {
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



  // Creates a new managed object. The parent LDAP entry is assumed to
  // already exist.
  private <N extends ConfigurationClient> ManagedObject<N> createManagedObject(
      ManagedObjectPath path, ManagedObjectDefinition<N, ?> d,
      PropertySet properties) throws OperationsException {
    LdapName dn = LDAPNameBuilder.create(path);
    Attributes attributes = new BasicAttributes();

    // Create the child's object class attribute.
    Attribute oc = new BasicAttribute("objectClass");
    for (String objectClass : profile.getObjectClasses(d)) {
      oc.add(objectClass);
    }
    attributes.put(oc);

    // Create the child's naming attribute.
    Rdn rdn = dn.getRdn(dn.size() - 1);
    attributes.put(rdn.getType(), rdn.getValue().toString());

    // Create the remaining attributes.
    for (PropertyDefinition<?> pd : d.getPropertyDefinitions()) {
      String attrID = profile.getAttributeName(d, pd);
      Attribute attribute = new BasicAttribute(attrID);
      encodeProperty(attribute, pd, properties);
      if (attribute.size() != 0) {
        attributes.put(attribute);
      }
    }

    try {
      // Create the entry.
      dirContext.createSubcontext(dn, attributes);
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }

    return new LDAPManagedObject<N>(dirContext, d, path, properties);
  }



  // Recursively delete a subtree of entries.
  private void destroySubtree(LdapName dn) throws NamingException {
    // List the child entries.
    String filter = "(objectClass=*)";
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);

    NamingEnumeration<SearchResult> results;
    results = dirContext.search(dn, filter, controls);

    // Recursively delete child entries.
    while (results.hasMore()) {
      SearchResult sr = results.next();
      LdapName child = new LdapName(dn.getRdns());
      child.add(new Rdn(sr.getName()));
      destroySubtree(child);
    }

    // Delete the specified entry.
    dirContext.destroySubcontext(dn);
  }



  // Encode a property into LDAP string values.
  private <T> void encodeProperty(Attribute attribute,
      PropertyDefinition<T> pd, PropertySet properties) {
    for (T value : properties.getPropertyValues(pd)) {
      attribute.add(pd.encodeValue(value));
    }
  }



  // Determine whether the named entry exists or not.
  private boolean entryExists(LdapName dn) throws NamingException {
    String filter = "(objectClass=*)";
    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.OBJECT_SCOPE);

    try {
      NamingEnumeration<SearchResult> results = dirContext.search(dn,
          filter, controls);
      if (results.hasMore()) {
        // TODO: should really verify that the relation entry has
        // the correct object classes, etc. It definitely has the
        // correct name, which is ok.
        return true;
      }
    } catch (NameNotFoundException e) {
      // Fall through - parent not found.
    }

    return false;
  }



  // Remove the named managed object.
  private void removeManagedObject(ManagedObjectPath p)
      throws OperationsException {
    try {
      LdapName dn = LDAPNameBuilder.create(p);
      if (entryExists(dn)) {
        // Delete the entry and any subordinate entries.
        destroySubtree(dn);
      }
    } catch (NamingException e) {
      OperationsExceptionFactory oef = new OperationsExceptionFactory();
      throw oef.createException(e);
    }
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    ManagedObjectDefinition d = getManagedObjectDefinition();
    RelationDefinition tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation "
          + rd.getName() + " is not associated with a " + d.getName());
    }
  }
}
