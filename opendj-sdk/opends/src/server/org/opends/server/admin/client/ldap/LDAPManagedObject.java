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

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.Property;
import org.opends.server.admin.client.PropertySet;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.RootCfgDefn;



/**
 * A managed object bound to an LDAP connection.
 *
 * @param <T>
 *          The type of client configuration represented by the client
 *          managed object.
 */
final class LDAPManagedObject<T extends ConfigurationClient> implements
    ManagedObject<T> {

  /**
   * A default behavior visitor used for retrieving the default values
   * of a property.
   *
   * @param <T>
   *          The type of the property.
   */
  private static class DefaultValueFinder<T> implements
      DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

    /**
     * Get the default values for the specified property.
     *
     * @param <T>
     *          The type of the property.
     * @param context
     *          The LDAP management context.
     * @param p
     *          The managed object path of the current managed object.
     * @param pd
     *          The property definition.
     * @param isCreate
     *          Indicates whether the managed object has been created
     *          yet.
     * @return Returns the default values for the specified property.
     * @throws DefaultBehaviorException
     *           If the default values could not be retrieved or
     *           decoded properly.
     */
    public static <T> Collection<T> getDefaultValues(
        LDAPManagementContext context, ManagedObjectPath<?, ?> p,
        PropertyDefinition<T> pd, boolean isCreate)
        throws DefaultBehaviorException {
      DefaultValueFinder<T> v = new DefaultValueFinder<T>(context, p, isCreate);
      return v.find(p, pd);
    }

    // The LDAP management context.
    private final LDAPManagementContext context;

    // Any exception that occurred whilst retrieving inherited default
    // values.
    private DefaultBehaviorException exception = null;

    // Indicates whether the managed object has been created yet.
    private final boolean isCreate;

    // The path of the managed object containing the first property.
    private final ManagedObjectPath<?, ?> firstPath;

    // The path of the managed object containing the next property.
    private ManagedObjectPath<?, ?> nextPath = null;

    // The next property whose default values were required.
    private PropertyDefinition<T> nextProperty = null;



    // Private constructor.
    private DefaultValueFinder(LDAPManagementContext context,
        ManagedObjectPath<?, ?> p, boolean isCreate) {
      this.context = context;
      this.firstPath = p;
      this.isCreate = isCreate;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitAbsoluteInherited(
        AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (DefaultBehaviorException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> d,
        Void p) {
      return Collections.emptySet();
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> d,
        Void p) {
      Collection<String> stringValues = d.getDefaultValues();
      List<T> values = new ArrayList<T>(stringValues.size());

      for (String stringValue : stringValues) {
        try {
          values.add(nextProperty.decodeValue(stringValue));
        } catch (IllegalPropertyValueStringException e) {
          exception = new DefaultBehaviorException(nextProperty, e);
          break;
        }
      }

      return values;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitRelativeInherited(
        RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(nextPath), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (DefaultBehaviorException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
        Void p) {
      return Collections.emptySet();
    }



    // Find the default values for the next path/property.
    private Collection<T> find(ManagedObjectPath<?, ?> p,
        PropertyDefinition<T> pd)
        throws DefaultBehaviorException {
      this.nextPath = p;
      this.nextProperty = pd;

      Collection<T> values = nextProperty.getDefaultBehaviorProvider().accept(
          this, null);

      if (exception != null) {
        throw exception;
      }

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        throw new DefaultBehaviorException(pd,
            new PropertyIsSingleValuedException(pd));
      }

      return values;
    }



    // Get an inherited property value.
    @SuppressWarnings("unchecked")
    private Collection<T> getInheritedProperty(ManagedObjectPath target,
        AbstractManagedObjectDefinition<?, ?> d, String propertyName)
        throws DefaultBehaviorException {
      // First check that the requested type of managed object
      // corresponds to the path.
      AbstractManagedObjectDefinition<?, ?> supr = target
          .getManagedObjectDefinition();
      if (!supr.isParentOf(d)) {
        throw new DefaultBehaviorException(nextProperty,
            new DefinitionDecodingException(Reason.WRONG_TYPE_INFORMATION));
      }

      // Save the current property in case of recursion.
      PropertyDefinition<T> pd1 = nextProperty;

      try {
        // If the path relates to the current managed object and the
        // managed object is in the process of being created it won't
        // exist, so we should just use the default values of the
        // referenced property.
        if (isCreate && firstPath.equals(target)) {
          PropertyDefinition<T> pd2;
          try {
            // FIXME: we use the definition taken from the default
            // behavior here when we should really use the exact
            // definition of the component being created.
            PropertyDefinition<?> pdTmp = d.getPropertyDefinition(propertyName);
            pd2 = pd1.getClass().cast(pdTmp);
          } catch (IllegalArgumentException e) {
            throw new PropertyNotFoundException(propertyName);
          } catch (ClassCastException e) {
            // FIXME: would be nice to throw a better exception here.
            throw new PropertyNotFoundException(propertyName);
          }

          // Recursively retrieve this property's default values.
          Collection<T> tmp = find(target, pd2);
          Collection<T> values = new ArrayList<T>(tmp.size());
          for (T value : tmp) {
            pd1.validateValue(value);
            values.add(value);
          }
          return values;
        } else {
          // Get the actual managed object definition.
          LdapName dn = LDAPNameBuilder
              .create(target, context.getLDAPProfile());
          ManagedObjectDefinition<?, ?> mod =
            getEntryDefinition(context, d, dn);

          PropertyDefinition<T> pd2;
          try {
            PropertyDefinition<?> pdTmp = mod
                .getPropertyDefinition(propertyName);
            pd2 = pd1.getClass().cast(pdTmp);
          } catch (IllegalArgumentException e) {
            throw new PropertyNotFoundException(propertyName);
          } catch (ClassCastException e) {
            // FIXME: would be nice to throw a better exception here.
            throw new PropertyNotFoundException(propertyName);
          }

          String attrID = context.getLDAPProfile().getAttributeName(mod, pd2);
          Attributes attributes = context.getLDAPConnection().readEntry(dn,
              Collections.singleton(attrID));
          Attribute attr = attributes.get(attrID);
          if (attr == null || attr.size() == 0) {
            // Recursively retrieve this property's default values.
            Collection<T> tmp = find(target, pd2);
            Collection<T> values = new ArrayList<T>(tmp.size());
            for (T value : tmp) {
              pd1.validateValue(value);
              values.add(value);
            }
            return values;
          } else {
            Collection<T> values = new LinkedList<T>();
            NamingEnumeration<?> ne = attr.getAll();
            while (ne.hasMore()) {
              Object value = ne.next();
              if (value != null) {
                values.add(pd1.decodeValue(value.toString()));
              }
            }
            return values;
          }
        }
      } catch (DefaultBehaviorException e) {
        // Wrap any errors due to recursion.
        throw new DefaultBehaviorException(pd1, e);
      } catch (DefinitionDecodingException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (PropertyNotFoundException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (IllegalPropertyValueException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (IllegalPropertyValueStringException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (NameNotFoundException e) {
        throw new DefaultBehaviorException(pd1,
            new ManagedObjectNotFoundException());
      } catch (NoPermissionException e) {
        throw new DefaultBehaviorException(pd1, new AuthorizationException(e));
      } catch (NamingException e) {
        throw new DefaultBehaviorException(pd1, new CommunicationException(e));
      }
    }
  };



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
    return new LDAPManagedObject<RootCfgClient>(context, RootCfgDefn
        .getInstance(), ManagedObjectPath.emptyPath(), new PropertySet(), true,
        null);
  }



  // Determine the type of managed object associated with the named
  // entry.
  private static <M extends ConfigurationClient, N extends Configuration>
      ManagedObjectDefinition<? extends M, ? extends N> getEntryDefinition(
      final LDAPManagementContext context,
      AbstractManagedObjectDefinition<M, N> d, LdapName dn)
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

  // The LDAP management context used for the ldap connection.
  private final LDAPManagementContext context;

  // The managed object definition associated with this managed
  // object.
  private final ManagedObjectDefinition<T, ? extends Configuration> definition;

  // Indicates whether or not this managed object exists on the server
  // (false means the managed object is new and has not been
  // committed).
  private boolean existsOnServer;

  // Optional naming property definition.
  private final PropertyDefinition<?> namingPropertyDefinition;

  // The path associated with this managed object.
  private ManagedObjectPath<T, ? extends Configuration> path;

  // The managed object's properties.
  private final PropertySet properties;



  // Create an new LDAP managed object with the provided JNDI context.
  private LDAPManagedObject(LDAPManagementContext context,
      ManagedObjectDefinition<T, ? extends Configuration> d,
      ManagedObjectPath<T, ? extends Configuration> path,
      PropertySet properties, boolean existsOnServer,
      PropertyDefinition<?> namingPropertyDefinition) {
    this.definition = d;
    this.context = context;
    this.path = path;
    this.properties = properties;
    this.existsOnServer = existsOnServer;
    this.namingPropertyDefinition = namingPropertyDefinition;
  }



  /**
   * {@inheritDoc}
   */
  public void commit() throws MissingMandatoryPropertiesException,
      ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException,
      ManagedObjectAlreadyExistsException {
    // First make sure all mandatory properties are defined.
    List<PropertyIsMandatoryException> exceptions =
      new LinkedList<PropertyIsMandatoryException>();

    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      Property<?> p = properties.getProperty(pd);
      if (pd.hasOption(PropertyOption.MANDATORY)
          && p.getEffectiveValues().isEmpty()) {
        exceptions.add(new PropertyIsMandatoryException(pd));
      }
    }

    if (!exceptions.isEmpty()) {
      throw new MissingMandatoryPropertiesException(exceptions);
    }

    // Commit the managed object.
    if (existsOnServer) {
      commitExistingManagedObject();
    } else {
      commitNewManagedObject();
    }
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration, CC extends C>
  ManagedObject<CC> createChild(InstantiableRelationDefinition<C, S> r,
      ManagedObjectDefinition<CC, ? extends S> d, String name,
      Collection<DefaultBehaviorException> exceptions)
      throws IllegalManagedObjectNameException, IllegalArgumentException {
    validateRelationDefinition(r);

    // Empty names are not allowed.
    if (name.trim().length() == 0) {
      throw new IllegalManagedObjectNameException(name);
    }

    // If the relation uses a naming property definition then it must
    // be a valid value.
    PropertyDefinition<?> pd = r.getNamingPropertyDefinition();
    if (pd != null) {
      try {
        pd.decodeValue(name);
      } catch (IllegalPropertyValueStringException e) {
        throw new IllegalManagedObjectNameException(name, pd);
      }
    }

    ManagedObjectPath<CC, ? extends S> childPath = path.child(r, d, name);
    return createNewManagedObject(d, childPath, pd, name, exceptions);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration, CC extends C>
  ManagedObject<CC> createChild(OptionalRelationDefinition<C, S> r,
      ManagedObjectDefinition<CC, ? extends S> d,
      Collection<DefaultBehaviorException> exceptions)
      throws IllegalArgumentException {
    validateRelationDefinition(r);
    ManagedObjectPath<CC, ? extends S> childPath = path.child(r, d);
    return createNewManagedObject(d, childPath, null, null, exceptions);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(InstantiableRelationDefinition<C, S> r,
      String name) throws IllegalArgumentException, DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    return readManagedObject(r.getChildDefinition(), path.child(r, name));
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(OptionalRelationDefinition<C, S> r)
      throws IllegalArgumentException, DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    return readManagedObject(r.getChildDefinition(), path.child(r));
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(
      SingletonRelationDefinition<C, S> r) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    return readManagedObject(r.getChildDefinition(), path.child(r));
  }



  /**
   * {@inheritDoc}
   */
  public T getConfiguration() {
    return definition.createClientConfiguration(this);
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObjectDefinition<T, ? extends Configuration>
  getManagedObjectDefinition() {
    return definition;
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObjectPath<T, ? extends Configuration> getManagedObjectPath() {
    return path;
  }



  /**
   * {@inheritDoc}
   */
  public <P> SortedSet<P> getPropertyDefaultValues(PropertyDefinition<P> pd)
      throws IllegalArgumentException {
    Property<P> p = properties.getProperty(pd);
    return p.getDefaultValues();
  }



  /**
   * {@inheritDoc}
   */
  public <P> P getPropertyValue(PropertyDefinition<P> pd)
      throws IllegalArgumentException {
    return properties.getPropertyValue(pd);
  }



  /**
   * {@inheritDoc}
   */
  public <P> SortedSet<P> getPropertyValues(PropertyDefinition<P> pd)
      throws IllegalArgumentException {
    return properties.getPropertyValues(pd);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  boolean hasChild(OptionalRelationDefinition<C, S> r)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();

    ManagedObjectPath<C, S> p = path.child(r);
    LdapName dn = LDAPNameBuilder.create(p, context.getLDAPProfile());
    return entryExists(dn);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  String[] listChildren(InstantiableRelationDefinition<C, S> r)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    return listChildren(r, r.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  String[] listChildren(InstantiableRelationDefinition<C, S> r,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();

    // Get the search base DN.
    LdapName dn = LDAPNameBuilder.create(path, r, context.getLDAPProfile());

    // Retrieve only those entries which are sub-types of the
    // specified definition.
    StringBuilder builder = new StringBuilder();
    builder.append("(objectclass=");
    builder.append(context.getLDAPProfile().getObjectClass(d));
    builder.append(')');
    String filter = builder.toString();

    List<String> children = new ArrayList<String>();
    try {
      for (LdapName child :
          context.getLDAPConnection().listEntries(dn, filter)) {
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
  public <C extends ConfigurationClient, S extends Configuration>
  void removeChild(InstantiableRelationDefinition<C, S> r, String name)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    ManagedObjectPath<C, S> p = path.child(r, name);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <C extends ConfigurationClient, S extends Configuration>
  void removeChild(OptionalRelationDefinition<C, S> r)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
        OperationRejectedException, ConcurrentModificationException,
        AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    ManagedObjectPath<C, S> p = path.child(r);
    removeManagedObject(p);
  }



  /**
   * {@inheritDoc}
   */
  public <P> void setPropertyValue(PropertyDefinition<P> pd, P value)
      throws IllegalPropertyValueException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    if (value == null) {
      setPropertyValues(pd, Collections.<P> emptySet());
    } else {
      setPropertyValues(pd, Collections.singleton(value));
    }
  }



  /**
   * {@inheritDoc}
   */
  public <P> void setPropertyValues(PropertyDefinition<P> pd,
      Collection<P> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    if (pd.hasOption(PropertyOption.MONITORING)) {
      throw new PropertyIsReadOnlyException(pd);
    }

    if (existsOnServer && pd.hasOption(PropertyOption.READ_ONLY)) {
      throw new PropertyIsReadOnlyException(pd);
    }

    properties.setPropertyValues(pd, values);

    // If this is a naming property then update the name.
    if (pd.equals(namingPropertyDefinition)) {
      // The property must be single-valued and mandatory.
      String newName = pd.encodeValue(values.iterator().next());
      path = path.rename(newName);
    }
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



  // Commit modifications made to this managed object.
  private void commitExistingManagedObject()
      throws ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    // Build the list of modified attributes.
    Attributes mods = new BasicAttributes();
    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      Property<?> p = properties.getProperty(pd);
      if (p.isModified()) {
        String attrID = context.getLDAPProfile().getAttributeName(definition,
            pd);
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

    // The changes were committed successfully so update this managed
    // object's state.
    properties.commit();
  }



  // Commit this new managed object.
  private void commitNewManagedObject() throws AuthorizationException,
      CommunicationException, OperationRejectedException,
      ConcurrentModificationException, ManagedObjectAlreadyExistsException {
    // First make sure that the parent managed object still exists.
    ManagedObjectPath<?, ?> parent = path.parent();
    if (!parent.isEmpty()) {
      LdapName dn = LDAPNameBuilder.create(parent, context.getLDAPProfile());
      if (!entryExists(dn)) {
        throw new ConcurrentModificationException();
      }
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
          .create(parent, ir, context.getLDAPProfile());
      if (!entryExists(dn)) {
        // We need to create the entry.
        Attributes attributes = new BasicAttributes();

        // Create the branch's object class attribute.
        Attribute oc = new BasicAttribute("objectClass");
        for (String objectClass : context.getLDAPProfile()
            .getInstantiableRelationObjectClasses(ir)) {
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
    }

    // Now add the entry representing this new managed object.
    LdapName dn = LDAPNameBuilder.create(path, context.getLDAPProfile());
    Attributes attributes = new BasicAttributes(true);

    // Create the object class attribute.
    Attribute oc = new BasicAttribute("objectclass");
    for (String objectClass : context.getLDAPProfile().getObjectClasses(
        definition)) {
      oc.add(objectClass);
    }
    attributes.put(oc);

    // Create the naming attribute if there is not naming property.
    if (namingPropertyDefinition == null) {
      Rdn rdn = dn.getRdn(dn.size() - 1);
      attributes.put(rdn.getType(), rdn.getValue().toString());
    }

    // Create the remaining attributes.
    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      String attrID = context.getLDAPProfile().getAttributeName(definition, pd);
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

    // The entry was created successfully so update this managed
    // object's state.
    properties.commit();
    existsOnServer = true;
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
    return new LDAPManagedObject<M>(context, d, p.asSubType(d), properties,
        true, pd);
  }



  // Creates a new managed object with no active values, just default
  // values.
  private <M extends ConfigurationClient, P>
      ManagedObject<M> createNewManagedObject(
      ManagedObjectDefinition<M, ?> d, ManagedObjectPath<M, ?> p,
      PropertyDefinition<P> namingPropertyDefinition, String name,
      Collection<DefaultBehaviorException> exceptions) {
    PropertySet childProperties = new PropertySet();
    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      try {
        createProperty(childProperties, p, pd);
      } catch (DefaultBehaviorException e) {
        // Add the exception if requested.
        if (exceptions != null) {
          exceptions.add(e);
        }
      }
    }

    // Set the naming property if there is one.
    if (namingPropertyDefinition != null) {
      P value = namingPropertyDefinition.decodeValue(name);
      childProperties.setPropertyValue(namingPropertyDefinition, value);
    }

    return new LDAPManagedObject<M>(context, d, p, childProperties, false,
        namingPropertyDefinition);
  }



  // Create an empty property.
  private <P> void createProperty(PropertySet properties,
      ManagedObjectPath<?, ?> p, PropertyDefinition<P> pd)
      throws DefaultBehaviorException {
    try {
      Collection<P> defaultValues = DefaultValueFinder.getDefaultValues(
          context, p, pd, true);
      properties.addProperty(pd, defaultValues, Collections.<P> emptySet());
    } catch (DefaultBehaviorException e) {
      // Make sure that we have still created the property.
      properties.addProperty(pd, Collections.<P> emptySet(), Collections
          .<P> emptySet());
      throw e;
    }
  }



  // Create a property using the provided string values.
  private <P> void decodeProperty(PropertySet newProperties,
      ManagedObjectPath<?, ?> p, PropertyDefinition<P> pd, List<String> values)
      throws PropertyException {
    PropertyException exception = null;

    // Get the property's active values.
    Collection<P> activeValues = new ArrayList<P>(values.size());
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
      P value = activeValues.iterator().next();
      activeValues.clear();
      activeValues.add(value);
    }

    if (activeValues.isEmpty() && pd.hasOption(PropertyOption.MANDATORY)) {
      // The active values maybe empty because of a previous exception.
      if (exception == null) {
        exception = new PropertyIsMandatoryException(pd);
      }
    }

    // Get the property's default values.
    Collection<P> defaultValues;
    try {
      defaultValues = DefaultValueFinder.getDefaultValues(context, p, pd,
          false);
    } catch (DefaultBehaviorException e) {
      defaultValues = Collections.emptySet();
      exception = e;
    }

    newProperties.addProperty(pd, defaultValues, activeValues);
    if (exception != null) {
      throw exception;
    }
  }



  // Encode a property into LDAP string values.
  private <P> void encodeProperty(Attribute attribute,
      PropertyDefinition<P> pd, PropertySet properties) {
    Property<P> p = properties.getProperty(pd);
    if (pd.hasOption(PropertyOption.MANDATORY)) {
      // For mandatory properties we fall-back to the default values
      // if defined which can sometimes be the case e.g when a
      // mandatory property is overridden.
      for (P value : p.getEffectiveValues()) {
        attribute.add(pd.encodeValue(value));
      }
    } else {
      for (P value : p.getPendingValues()) {
        attribute.add(pd.encodeValue(value));
      }
    }
  }



  // Makes sure that the entry corresponding to this managed object
  // exists.
  private void ensureThisManagedObjectExists()
      throws ConcurrentModificationException, CommunicationException,
      AuthorizationException {
    if (!path.isEmpty()) {
      LdapName dn = LDAPNameBuilder.create(path, context.getLDAPProfile());
      if (!entryExists(dn)) {
        throw new ConcurrentModificationException();
      }
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



  // Read the entry identified by the path and which is a sub-type of
  // the specified definition.
  private <C extends ConfigurationClient, S extends Configuration>
      ManagedObject<? extends C> readManagedObject(
      AbstractManagedObjectDefinition<C, S> d, ManagedObjectPath<C, S> p)
      throws DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, AuthorizationException,
      CommunicationException {
    try {
      // Read the entry associated with the managed object.
      LdapName dn = LDAPNameBuilder.create(p, context.getLDAPProfile());
      ManagedObjectDefinition<? extends C, ? extends S> mod =
        getEntryDefinition(context, d, dn);

      ArrayList<String> attrIds = new ArrayList<String>();
      for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
        String attrId = context.getLDAPProfile().getAttributeName(mod, pd);
        attrIds.add(attrId);
      }

      Attributes attributes = context.getLDAPConnection()
          .readEntry(dn, attrIds);

      // Build the managed object's properties.
      List<PropertyException> exceptions = new LinkedList<PropertyException>();
      PropertySet newProperties = new PropertySet();
      for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
        String attrID = context.getLDAPProfile().getAttributeName(mod, pd);
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
          decodeProperty(newProperties, p, pd, values);
        } catch (PropertyException e) {
          exceptions.add(e);
        }
      }

      // If there were no decoding problems then return the object,
      // otherwise throw an operations exception.
      ManagedObject<? extends C> mo = createExistingManagedObject(mod, p,
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



  // Remove the named managed object.
  private void removeManagedObject(ManagedObjectPath<?, ?> p)
      throws CommunicationException, AuthorizationException,
      OperationRejectedException, ManagedObjectNotFoundException {
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
    } else {
      throw new ManagedObjectNotFoundException();
    }
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    ManagedObjectDefinition<T, ?> d = getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + d.getName());
    }
  }
}
