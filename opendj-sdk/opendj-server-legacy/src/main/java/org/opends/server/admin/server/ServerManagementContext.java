/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.admin.server;

import static org.opends.messages.AdminMessages.*;
import static org.opends.server.admin.PropertyException.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.Reference;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DN;

/**
 * Server management connection context.
 */
public final class ServerManagementContext {

  /**
   * A default behavior visitor used for retrieving the default values
   * of a property.
   *
   * @param <T>
   *          The type of the property.
   */
  private class DefaultValueFinder<T> implements
      DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

    /**
     * Any exception that occurred whilst retrieving inherited default values.
     */
    private PropertyException exception;

    /**
     * Optional new configuration entry which does not yet exist in
     * the configuration back-end.
     */
    private final ConfigEntry newConfigEntry;

    /** The path of the managed object containing the next property. */
    private ManagedObjectPath<?, ?> nextPath;

    /** The next property whose default values were required. */
    private PropertyDefinition<T> nextProperty;



    /** Private constructor. */
    private DefaultValueFinder(ConfigEntry newConfigEntry) {
      this.newConfigEntry = newConfigEntry;
    }



    /** {@inheritDoc} */
    @Override
    public Collection<T> visitAbsoluteInherited(
        AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (PropertyException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /** {@inheritDoc} */
    @Override
    public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
      return Collections.emptySet();
    }



    /** {@inheritDoc} */
    @Override
    public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> d,
        Void p) {
      Collection<String> stringValues = d.getDefaultValues();
      List<T> values = new ArrayList<>(stringValues.size());

      for (String stringValue : stringValues) {
        try {
          values.add(nextProperty.decodeValue(stringValue));
        } catch (PropertyException e) {
          exception = defaultBehaviorException(nextProperty, e);
          break;
        }
      }

      return values;
    }



    /** {@inheritDoc} */
    @Override
    public Collection<T> visitRelativeInherited(
        RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(nextPath), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (PropertyException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /** {@inheritDoc} */
    @Override
    public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
        Void p) {
      return Collections.emptySet();
    }



    /** Find the default values for the next path/property. */
    private Collection<T> find(ManagedObjectPath<?, ?> p,
        PropertyDefinition<T> pd) throws PropertyException {
      nextPath = p;
      nextProperty = pd;

      Collection<T> values = nextProperty.getDefaultBehaviorProvider().accept(
          this, null);

      if (exception != null) {
        throw exception;
      }

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        throw PropertyException.defaultBehaviorException(pd,
            PropertyException.propertyIsSingleValuedException(pd));
      }

      return values;
    }



    /** Get an inherited property value. */
    @SuppressWarnings("unchecked")
    private Collection<T> getInheritedProperty(ManagedObjectPath target,
        AbstractManagedObjectDefinition<?, ?> d, String propertyName)
        throws PropertyException {
      // First check that the requested type of managed object
      // corresponds to the path.
      AbstractManagedObjectDefinition<?, ?> actual = target
          .getManagedObjectDefinition();
      if (!d.isParentOf(actual)) {
        throw PropertyException.defaultBehaviorException(
            nextProperty, new DefinitionDecodingException(actual,
                Reason.WRONG_TYPE_INFORMATION));
      }

      // Save the current property in case of recursion.
      PropertyDefinition<T> pd1 = nextProperty;

      try {
        // Get the actual managed object definition.
        DN dn = DNBuilder.create(target);
        ConfigEntry configEntry;
        if (newConfigEntry != null && newConfigEntry.getDN().equals(dn)) {
          configEntry = newConfigEntry;
        } else {
          configEntry = getManagedObjectConfigEntry(dn);
        }

        DefinitionResolver resolver = new MyDefinitionResolver(configEntry);
        ManagedObjectDefinition<?, ?> mod = d
            .resolveManagedObjectDefinition(resolver);

        PropertyDefinition<T> pd2;
        try {
          PropertyDefinition<?> pdTmp = mod.getPropertyDefinition(propertyName);
          pd2 = pd1.getClass().cast(pdTmp);
        } catch (IllegalArgumentException e) {
          throw new PropertyNotFoundException(propertyName);
        } catch (ClassCastException e) {
          // FIXME: would be nice to throw a better exception here.
          throw new PropertyNotFoundException(propertyName);
        }

        List<ByteString> values = getAttribute(mod, pd2, configEntry);
        if (values.isEmpty()) {
          // Recursively retrieve this property's default values.
          Collection<T> tmp = find(target, pd2);
          Collection<T> pvalues = new ArrayList<>(tmp.size());
          for (T value : tmp) {
            pd1.validateValue(value);
            pvalues.add(value);
          }
          return pvalues;
        } else {
          Collection<T> pvalues = new ArrayList<>(values.size());
          for (ByteString value : values) {
            pvalues.add(ValueDecoder.decode(pd1, value));
          }
          return pvalues;
        }
      } catch (DefinitionDecodingException | PropertyNotFoundException | PropertyException | ConfigException e) {
        throw PropertyException.defaultBehaviorException(pd1, e);
      }
    }
  }



  /**
   * A definition resolver that determines the managed object
   * definition from the object classes of a ConfigEntry.
   */
  private class MyDefinitionResolver implements DefinitionResolver {

    /** The config entry. */
    private final ConfigEntry entry;



    /** Private constructor. */
    private MyDefinitionResolver(ConfigEntry entry) {
      this.entry = entry;
    }



    /** {@inheritDoc} */
    @Override
    public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
      String oc = LDAPProfile.getInstance().getObjectClass(d);
      return entry.hasObjectClass(oc);
    }
  }



  /**
   * A visitor which is used to decode property LDAP values.
   */
  private static final class ValueDecoder extends
      PropertyDefinitionVisitor<Object, String> {

    /**
     * Decodes the provided property LDAP value.
     *
     * @param <PD>
     *          The type of the property.
     * @param pd
     *          The property definition.
     * @param value
     *          The LDAP string representation.
     * @return Returns the decoded LDAP value.
     * @throws PropertyException
     *           If the property value could not be decoded because it
     *           was invalid.
     */
    public static <PD> PD decode(PropertyDefinition<PD> pd,
        ByteString value) throws PropertyException {
      return pd.castValue(pd.accept(new ValueDecoder(), value.toString()));
    }



    /** Prevent instantiation. */
    private ValueDecoder() {
      // No implementation required.
    }



    /** {@inheritDoc} */
    @Override
    public <C extends ConfigurationClient, S extends Configuration>
    Object visitAggregation(AggregationPropertyDefinition<C, S> d, String p) {
      // Aggregations values are stored as full DNs in LDAP, but
      // just their common name is exposed in the admin framework.
      try {
        Reference<C, S> reference = Reference.parseDN(d.getParentPath(), d
            .getRelationDefinition(), p);
        return reference.getName();
      } catch (IllegalArgumentException e) {
        throw PropertyException.illegalPropertyValueException(d, p);
      }
    }



    /** {@inheritDoc} */
    @Override
    public <T> Object visitUnknown(PropertyDefinition<T> d, String p)
        throws PropertyException {
      // By default the property definition's decoder will do.
      return d.decodeValue(p);
    }
  }



  /** Singleton instance. */
  private static final ServerManagementContext INSTANCE = new ServerManagementContext();

  /** The root server managed object. */
  private static final ServerManagedObject<RootCfg> ROOT = new ServerManagedObject<>(
      ManagedObjectPath.emptyPath(), RootCfgDefn.getInstance(), Collections
          .<PropertyDefinition<?>, SortedSet<?>> emptyMap(), null);
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /**
   * Get the single server-side management context.
   *
   * @return Returns the single server-side management context.
   */
  public static ServerManagementContext getInstance() {
    return INSTANCE;
  }



  /** Private constructor. */
  private ServerManagementContext() {
    // No implementation required.
  }



  /**
   * Gets the named managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param path
   *          The path of the managed object.
   * @return Returns the named managed object.
   * @throws ConfigException
   *           If the named managed object could not be found or if it
   *           could not be decoded.
   */
  @SuppressWarnings("unchecked")
  public <C extends ConfigurationClient, S extends Configuration>
  ServerManagedObject<? extends S> getManagedObject(
      ManagedObjectPath<C, S> path) throws ConfigException {
    // Be careful to handle the root configuration.
    if (path.isEmpty()) {
      return (ServerManagedObject<S>) getRootConfigurationManagedObject();
    }

    // Get the configuration entry.
    DN targetDN = DNBuilder.create(path);
    ConfigEntry configEntry = getManagedObjectConfigEntry(targetDN);
    try {
      ServerManagedObject<? extends S> managedObject;
      managedObject = decode(path, configEntry);

      // Enforce any constraints.
      managedObject.ensureIsUsable();

      return managedObject;
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(targetDN, e);
    } catch (ServerManagedObjectDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(e);
    } catch (ConstraintViolationException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(e);
    }
  }



  /**
   * Gets the effective value of a property in the named managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ConfigException
   *           If the named managed object could not be found or if it
   *           could not be decoded.
   */
  public <C extends ConfigurationClient, S extends Configuration, PD>
  PD getPropertyValue(ManagedObjectPath<C, S> path,
      PropertyDefinition<PD> pd) throws IllegalArgumentException,
      ConfigException, PropertyException {
    SortedSet<PD> values = getPropertyValues(path, pd);
    if (values.isEmpty()) {
      return null;
    } else {
      return values.first();
    }
  }



  /**
   * Gets the effective values of a property in the named managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set
   *         if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ConfigException
   *           If the named managed object could not be found or if it
   *           could not be decoded.
   */
  @SuppressWarnings("unchecked")
  public <C extends ConfigurationClient, S extends Configuration, PD>
  SortedSet<PD> getPropertyValues(ManagedObjectPath<C, S> path,
      PropertyDefinition<PD> pd) throws IllegalArgumentException,
      ConfigException, PropertyException {
    // Check that the requested property is from the definition
    // associated with the path.
    AbstractManagedObjectDefinition<C, S> d = path.getManagedObjectDefinition();
    PropertyDefinition<?> tmp = d.getPropertyDefinition(pd.getName());
    if (tmp != pd) {
      throw new IllegalArgumentException("The property " + pd.getName()
          + " is not associated with a " + d.getName());
    }

    // Determine the exact type of managed object referenced by the
    // path.
    DN dn = DNBuilder.create(path);
    ConfigEntry configEntry = getManagedObjectConfigEntry(dn);

    DefinitionResolver resolver = new MyDefinitionResolver(configEntry);
    ManagedObjectDefinition<? extends C, ? extends S> mod;

    try {
      mod = d.resolveManagedObjectDefinition(resolver);
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(dn, e);
    }

    // Make sure we use the correct property definition, the
    // provided one might have been overridden in the resolved
    // definition.
    pd = (PropertyDefinition<PD>) mod.getPropertyDefinition(pd.getName());

    List<ByteString> values = getAttribute(mod, pd, configEntry);
    return decodeProperty(path.asSubType(mod), pd, values, null);
  }



  /**
   * Get the root configuration manager associated with this
   * management context.
   *
   * @return Returns the root configuration manager associated with
   *         this management context.
   */
  public RootCfg getRootConfiguration() {
    return getRootConfigurationManagedObject().getConfiguration();
  }



  /**
   * Get the root configuration server managed object associated with
   * this management context.
   *
   * @return Returns the root configuration server managed object
   *         associated with this management context.
   */
  public ServerManagedObject<RootCfg> getRootConfigurationManagedObject() {
    return ROOT;
  }



  /**
   * Lists the child managed objects of the named parent managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   */
  public <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd)
      throws IllegalArgumentException {
    validateRelationDefinition(parent, rd);

    // Get the target entry.
    DN targetDN = DNBuilder.create(parent, rd);
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(targetDN);
    } catch (ConfigException e) {
      return new String[0];
    }

    if (configEntry == null) {
      return new String[0];
    }

    // Retrieve the children.
    Set<DN> children = configEntry.getChildren().keySet();
    ArrayList<String> names = new ArrayList<>(children.size());
    for (DN child : children) {
      // Assume that RDNs are single-valued and can be trimmed.
      ByteString av = child.rdn().getAttributeValue(0);
      names.add(av.toString().trim());
    }

    return names.toArray(new String[names.size()]);
  }



  /**
   * Lists the child managed objects of the named parent managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The set relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   */
  public <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd)
      throws IllegalArgumentException {
    validateRelationDefinition(parent, rd);

    // Get the target entry.
    DN targetDN = DNBuilder.create(parent, rd);
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(targetDN);
    } catch (ConfigException e) {
      return new String[0];
    }

    if (configEntry == null) {
      return new String[0];
    }

    // Retrieve the children.
    Set<DN> children = configEntry.getChildren().keySet();
    ArrayList<String> names = new ArrayList<>(children.size());
    for (DN child : children) {
      // Assume that RDNs are single-valued and can be trimmed.
      ByteString av = child.rdn().getAttributeValue(0);
      names.add(av.toString().trim());
    }

    return names.toArray(new String[names.size()]);
  }



  /**
   * Determines whether or not the named managed object exists.
   *
   * @param path
   *          The path of the named managed object.
   * @return Returns <code>true</code> if the named managed object
   *         exists, <code>false</code> otherwise.
   */
  public boolean managedObjectExists(ManagedObjectPath<?, ?> path) {
    // Get the configuration entry.
    DN targetDN = DNBuilder.create(path);
    try {
      return (getManagedObjectConfigEntry(targetDN) != null);
    } catch (ConfigException e) {
      // Assume it doesn't exist.
      return false;
    }
  }



  /**
   * Decodes a configuration entry into the required type of server
   * managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param path
   *          The location of the server managed object.
   * @param configEntry
   *          The configuration entry that should be decoded.
   * @return Returns the new server-side managed object from the
   *         provided definition and configuration entry.
   * @throws DefinitionDecodingException
   *           If the managed object's type could not be determined.
   * @throws ServerManagedObjectDecodingException
   *           If one or more of the managed object's properties could
   *           not be decoded.
   */
  <C extends ConfigurationClient, S extends Configuration>
  ServerManagedObject<? extends S> decode(
      ManagedObjectPath<C, S> path, ConfigEntry configEntry)
      throws DefinitionDecodingException, ServerManagedObjectDecodingException {
    return decode(path, configEntry, null);
  }



  /**
   * Decodes a configuration entry into the required type of server
   * managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param path
   *          The location of the server managed object.
   * @param configEntry
   *          The configuration entry that should be decoded.
   * @param newConfigEntry
   *          Optional new configuration that does not exist yet in
   *          the configuration back-end. This will be used for
   *          resolving inherited default values.
   * @return Returns the new server-side managed object from the
   *         provided definition and configuration entry.
   * @throws DefinitionDecodingException
   *           If the managed object's type could not be determined.
   * @throws ServerManagedObjectDecodingException
   *           If one or more of the managed object's properties could
   *           not be decoded.
   */
  <C extends ConfigurationClient, S extends Configuration>
  ServerManagedObject<? extends S> decode(
      ManagedObjectPath<C, S> path, ConfigEntry configEntry,
      ConfigEntry newConfigEntry) throws DefinitionDecodingException,
      ServerManagedObjectDecodingException {
    // First determine the correct definition to use for the entry.
    // This could either be the provided definition, or one of its
    // sub-definitions.
    DefinitionResolver resolver = new MyDefinitionResolver(configEntry);
    AbstractManagedObjectDefinition<C, S> d = path.getManagedObjectDefinition();
    ManagedObjectDefinition<? extends C, ? extends S> mod = d
        .resolveManagedObjectDefinition(resolver);

    // Build the managed object's properties.
    List<PropertyException> exceptions = new LinkedList<>();
    Map<PropertyDefinition<?>, SortedSet<?>> properties = new HashMap<>();
    for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
      List<ByteString> values = getAttribute(mod, pd, configEntry);
      try {
        SortedSet<?> pvalues = decodeProperty(path, pd, values, newConfigEntry);
        properties.put(pd, pvalues);
      } catch (PropertyException e) {
        exceptions.add(e);
      }
    }

    // If there were no decoding problems then return the managed
    // object, otherwise throw an operations exception.
    ServerManagedObject<? extends S> mo = decodeAux(path, mod, properties, configEntry);
    if (!exceptions.isEmpty()) {
      throw new ServerManagedObjectDecodingException(mo, exceptions);
    }
    return mo;
  }



  /** Decode helper method required to avoid generics warning. */
  private <C extends ConfigurationClient, S extends Configuration>
  ServerManagedObject<S> decodeAux(
      ManagedObjectPath<? super C, ? super S> path,
      ManagedObjectDefinition<C, S> d,
      Map<PropertyDefinition<?>, SortedSet<?>> properties,
      ConfigEntry configEntry) {
    ManagedObjectPath<C, S> newPath = path.asSubType(d);
    return new ServerManagedObject<>(newPath, d, properties, configEntry);
  }



  /** Create a property using the provided string values. */
  private <T> SortedSet<T> decodeProperty(ManagedObjectPath<?, ?> path,
      PropertyDefinition<T> pd, List<ByteString> values,
      ConfigEntry newConfigEntry) throws PropertyException {
    PropertyException exception = null;
    SortedSet<T> pvalues = new TreeSet<>(pd);

    if (!values.isEmpty()) {
      // The property has values defined for it.
      for (ByteString value : values) {
        try {
          pvalues.add(ValueDecoder.decode(pd, value));
        } catch (PropertyException e) {
          exception = e;
        }
      }
    } else {
      // No values defined so get the defaults.
      try {
        pvalues.addAll(getDefaultValues(path, pd, newConfigEntry));
      } catch (PropertyException e) {
        exception = e;
      }
    }

    if (pvalues.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
      // This exception takes precedence over previous exceptions.
      exception = PropertyException.propertyIsSingleValuedException(pd);
      T value = pvalues.first();
      pvalues.clear();
      pvalues.add(value);
    }

    if (pvalues.isEmpty()
        && pd.hasOption(PropertyOption.MANDATORY)
        // The values maybe empty because of a previous exception.
        && exception == null) {
      exception = PropertyException.propertyIsMandatoryException(pd);
    }

    if (exception != null) {
      throw exception;
    }
    return pvalues;
  }



  /** Gets the attribute associated with a property from a ConfigEntry. */
  private List<ByteString> getAttribute(ManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd, ConfigEntry configEntry) {
    // TODO: we create a default attribute type if it is
    // undefined. We should log a warning here if this is the case
    // since the attribute should have been defined.
    String attrID = LDAPProfile.getInstance().getAttributeName(d, pd);
    AttributeType type = DirectoryServer.getAttributeTypeOrDefault(attrID);
    List<Attribute> attributes = configEntry.getEntry().getAttribute(type, true);

    List<ByteString> results = new LinkedList<>();
    if (attributes != null)
    {
      for (Attribute a : attributes)
      {
        for (ByteString v : a)
        {
          results.add(v);
        }
      }
    }
    return results;
  }



  /** Get the default values for the specified property. */
  private <T> Collection<T> getDefaultValues(ManagedObjectPath<?, ?> p,
      PropertyDefinition<T> pd, ConfigEntry newConfigEntry)
      throws PropertyException {
    DefaultValueFinder<T> v = new DefaultValueFinder<>(newConfigEntry);
    return v.find(p, pd);
  }



  /**
   * Gets a config entry required for a managed object and throws a
   * config exception on failure.
   */
  private ConfigEntry getManagedObjectConfigEntry(
      DN dn) throws ConfigException {
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(dn);
    } catch (ConfigException e) {
      logger.traceException(e);

      LocalizableMessage message = ERR_ADMIN_CANNOT_GET_MANAGED_OBJECT.get(
          dn, stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // The configuration handler is free to return null indicating
    // that the entry does not exist.
    if (configEntry == null) {
      LocalizableMessage message = ERR_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST.get(dn);
      throw new ConfigException(message);
    }

    return configEntry;
  }



  /** Validate that a relation definition belongs to the path. */
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
