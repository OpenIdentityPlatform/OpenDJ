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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.server;
import org.opends.messages.Message;



import static org.opends.server.loggers.debug.DebugLogger.*;
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

import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.messages.AdminMessages;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;



/**
 * A server-side managed object.
 *
 * @param <S>
 *          The type of server configuration represented by the server
 *          managed object.
 */
public final class ServerManagedObject<S extends Configuration> implements
    PropertyProvider {

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
     * @param p
     *          The managed object path of the current managed object.
     * @param pd
     *          The property definition.
     * @param newConfigEntry
     *          Optional new configuration entry which does not yet
     *          exist in the configuration back-end.
     * @return Returns the default values for the specified property.
     * @throws DefaultBehaviorException
     *           If the default values could not be retrieved or
     *           decoded properly.
     */
    public static <T> Collection<T> getDefaultValues(ManagedObjectPath<?, ?> p,
        PropertyDefinition<T> pd, ConfigEntry newConfigEntry)
        throws DefaultBehaviorException {
      DefaultValueFinder<T> v = new DefaultValueFinder<T>(newConfigEntry);
      return v.find(p, pd);
    }

    // Any exception that occurred whilst retrieving inherited default
    // values.
    private DefaultBehaviorException exception = null;

    // The path of the managed object containing the next property.
    private ManagedObjectPath<?, ?> nextPath = null;

    // The next property whose default values were required.
    private PropertyDefinition<T> nextProperty = null;

    // Optional new configuration entry which does not yet exist in
    // the configuration back-end.
    private ConfigEntry newConfigEntry;



    // Private constructor.
    private DefaultValueFinder(ConfigEntry newConfigEntry) {
      this.newConfigEntry = newConfigEntry;
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
    public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
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
        PropertyDefinition<T> pd) throws DefaultBehaviorException {
      nextPath = p;
      nextProperty = pd;

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
        throw new DefaultBehaviorException(
            nextProperty, new DefinitionDecodingException(supr,
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

        List<String> stringValues = getAttribute(mod, pd2, configEntry);
        if (stringValues.isEmpty()) {
          // Recursively retrieve this property's default values.
          Collection<T> tmp = find(target, pd2);
          Collection<T> values = new ArrayList<T>(tmp.size());
          for (T value : tmp) {
            pd1.validateValue(value);
            values.add(value);
          }
          return values;
        } else {
          Collection<T> values = new ArrayList<T>(stringValues.size());
          for (String s : stringValues) {
            values.add(pd1.decodeValue(s));
          }
          return values;
        }
      } catch (DefinitionDecodingException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (PropertyNotFoundException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (IllegalPropertyValueException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (IllegalPropertyValueStringException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (ConfigException e) {
        throw new DefaultBehaviorException(pd1, e);
      }
    }
  }



  /**
   * A definition resolver that determines the managed object
   * definition from the object classes of a ConfigEntry.
   */
  private static class MyDefinitionResolver implements DefinitionResolver {

    // The config entry.
    private final ConfigEntry entry;



    // Private constructor.
    private MyDefinitionResolver(ConfigEntry entry) {
      this.entry = entry;
    }



    /**
     * {@inheritDoc}
     */
    public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
      String oc = LDAPProfile.getInstance().getObjectClass(d);
      return entry.hasObjectClass(oc);
    }
  };

  /**
   * The root server managed object.
   */
  private static final ServerManagedObject<RootCfg> ROOT =
    new ServerManagedObject<RootCfg>(
      ManagedObjectPath.emptyPath(), RootCfgDefn.getInstance(), Collections
          .<PropertyDefinition<?>, SortedSet<?>> emptyMap(), null);

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Decodes a configuration entry into the required type of server
   * managed object.
   *
   * @param <S>
   *          The type of server configuration represented by the
   *          decoded server managed object.
   * @param path
   *          The location of the server managed object.
   * @param definition
   *          The required managed object type.
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
  static <S extends Configuration> ServerManagedObject<? extends S> decode(
      ManagedObjectPath<?, ?> path,
      AbstractManagedObjectDefinition<?, S> definition,
      ConfigEntry configEntry) throws DefinitionDecodingException,
      ServerManagedObjectDecodingException {
    return decode(path, definition, configEntry, null);
  }



  /**
   * Decodes a configuration entry into the required type of server
   * managed object.
   *
   * @param <S>
   *          The type of server configuration represented by the
   *          decoded server managed object.
   * @param path
   *          The location of the server managed object.
   * @param definition
   *          The required managed object type.
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
  static <S extends Configuration> ServerManagedObject<? extends S> decode(
      ManagedObjectPath<?, ?> path,
      AbstractManagedObjectDefinition<?, S> definition,
      ConfigEntry configEntry, ConfigEntry newConfigEntry)
      throws DefinitionDecodingException, ServerManagedObjectDecodingException {
    // First determine the correct definition to use for the entry.
    // This could either be the provided definition, or one of its
    // sub-definitions.
    DefinitionResolver resolver = new MyDefinitionResolver(configEntry);
    ManagedObjectDefinition<?, ? extends S> mod = definition
        .resolveManagedObjectDefinition(resolver);

    // Build the managed object's properties.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    Map<PropertyDefinition<?>, SortedSet<?>> properties =
      new HashMap<PropertyDefinition<?>, SortedSet<?>>();
    for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
      List<String> values = getAttribute(mod, pd, configEntry);
      try {
        decodeProperty(properties, path, pd, values, newConfigEntry);
      } catch (PropertyException e) {
        exceptions.add(e);
      }
    }

    // If there were no decoding problems then return the managed
    // object, otherwise throw an operations exception.
    ServerManagedObject<? extends S> mo = decodeAux(path, mod, properties,
        configEntry);
    if (exceptions.isEmpty()) {
      return mo;
    } else {
      throw new ServerManagedObjectDecodingException(mo, exceptions);
    }
  }



  /**
   * Gets the root server managed object.
   *
   * @return Returns the root server managed object.
   */
  static ServerManagedObject<RootCfg> getRootManagedObject() {
    return ROOT;
  }



  // Decode helper method required to avoid generics warning.
  private static <S extends Configuration> ServerManagedObject<S> decodeAux(
      ManagedObjectPath<?, ?> path, ManagedObjectDefinition<?, S> d,
      Map<PropertyDefinition<?>, SortedSet<?>> properties,
      ConfigEntry configEntry) {
    return new ServerManagedObject<S>(path, d, properties, configEntry);
  }



  // Create a property using the provided string values.
  private static <T> void decodeProperty(
      Map<PropertyDefinition<?>, SortedSet<?>> properties,
      ManagedObjectPath<?, ?> path, PropertyDefinition<T> pd,
      List<String> stringValues, ConfigEntry newConfigEntry)
      throws PropertyException {
    PropertyException exception = null;
    SortedSet<T> values = new TreeSet<T>(pd);

    if (!stringValues.isEmpty()) {
      // The property has values defined for it.
      for (String value : stringValues) {
        try {
          values.add(pd.decodeValue(value));
        } catch (IllegalPropertyValueStringException e) {
          exception = e;
        }
      }
    } else {
      // No values defined so get the defaults.
      try {
        values.addAll(DefaultValueFinder.getDefaultValues(path, pd,
            newConfigEntry));
      } catch (DefaultBehaviorException e) {
        exception = e;
      }
    }

    if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
      // This exception takes precedence over previous exceptions.
      exception = new PropertyIsSingleValuedException(pd);
      T value = values.first();
      values.clear();
      values.add(value);
    }

    if (values.isEmpty() && pd.hasOption(PropertyOption.MANDATORY)) {
      // The values maybe empty because of a previous exception.
      if (exception == null) {
        exception = new PropertyIsMandatoryException(pd);
      }
    }

    // TODO: If an exception occurs should we leave the property
    // empty?
    properties.put(pd, values);
    if (exception != null) {
      throw exception;
    }
  }



  // Gets the attribute associated with a property from a ConfigEntry.
  private static List<String> getAttribute(ManagedObjectDefinition<?, ?> d,
      PropertyDefinition<?> pd, ConfigEntry configEntry) {
    // TODO: we create a default attribute type if it is
    // undefined. We should log a warning here if this is the case
    // since the attribute should have been defined.
    String attrID = LDAPProfile.getInstance().getAttributeName(d, pd);
    AttributeType type = DirectoryServer.getAttributeType(attrID, true);
    AttributeValueDecoder<String> decoder =
      new AttributeValueDecoder<String>() {

      public String decode(AttributeValue value) throws DirectoryException {
        return value.getStringValue();
      }
    };

    List<String> values = new LinkedList<String>();
    try {
      configEntry.getEntry().getAttributeValues(type, decoder, values);
    } catch (DirectoryException e) {
      // Should not happen.
      throw new RuntimeException(e);
    }
    return values;
  }



  // Gets a config entry required for a managed object and throws a
  // config exception on failure.
  private static ConfigEntry getManagedObjectConfigEntry(DN dn)
      throws ConfigException {
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(dn);
    } catch (ConfigException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = AdminMessages.ERR_ADMIN_CANNOT_GET_MANAGED_OBJECT.get(
          String.valueOf(dn), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    // The configuration handler is free to return null indicating
    // that the entry does not exist.
    if (configEntry == null) {
      Message message = AdminMessages.ERR_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST.
          get(String.valueOf(dn));
      throw new ConfigException(message);
    }

    return configEntry;
  }

  // The configuration entry associated with this server managed
  // object (null if root).
  private ConfigEntry configEntry;

  // The managed object's definition.
  private final ManagedObjectDefinition<?, S> definition;

  // The managed object path identifying this managed object's
  // location.
  private final ManagedObjectPath<?, ?> path;

  // The managed object's properties.
  private final Map<PropertyDefinition<?>, SortedSet<?>> properties;



  // Create an new server side managed object.
  private ServerManagedObject(ManagedObjectPath<?, ?> path,
      ManagedObjectDefinition<?, S> d,
      Map<PropertyDefinition<?>, SortedSet<?>> properties,
      ConfigEntry configEntry) {
    this.definition = d;
    this.path = path;
    this.properties = properties;
    this.configEntry = configEntry;
  }



  /**
   * Deregisters an existing configuration add listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The configuration add listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   */
  public <M extends Configuration> void deregisterAddListener(
      InstantiableRelationDefinition<?, M> d,
      ConfigurationAddListener<M> listener) throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d);
    deregisterAddListener(baseDN, listener);
  }



  /**
   * Deregisters an existing configuration add listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The configuration add listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   */
  public <M extends Configuration> void deregisterAddListener(
      OptionalRelationDefinition<?, M> d, ConfigurationAddListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d).getParent();
    deregisterAddListener(baseDN, listener);
  }



  /**
   * Deregisters an existing configuration change listener.
   *
   * @param listener
   *          The configuration change listener.
   */
  public void deregisterChangeListener(
      ConfigurationChangeListener<? super S> listener) {
    for (ConfigChangeListener l : configEntry.getChangeListeners()) {
      if (l instanceof ConfigChangeListenerAdaptor) {
        ConfigChangeListenerAdaptor<?> adaptor =
          (ConfigChangeListenerAdaptor<?>) l;
        if (adaptor.getConfigurationChangeListener() == listener) {
          configEntry.deregisterChangeListener(adaptor);
        }
      }
    }
  }



  /**
   * Deregisters an existing configuration delete listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The configuration delete listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   */
  public <M extends Configuration> void deregisterDeleteListener(
      InstantiableRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d);
    deregisterDeleteListener(baseDN, listener);
  }



  /**
   * Deregisters an existing configuration delete listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The configuration delete listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   */
  public <M extends Configuration> void deregisterDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d).getParent();
    deregisterDeleteListener(baseDN, listener);
  }



  /**
   * Retrieve an instantiable child managed object.
   *
   * @param <M>
   *          The requested type of the child server managed object
   *          configuration.
   * @param d
   *          The instantiable relation definition.
   * @param name
   *          The name of the child managed object.
   * @return Returns the instantiable child managed object.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ConfigException
   *           If the child managed object could not be found or if it
   *           could not be decoded.
   */
  public <M extends Configuration> ServerManagedObject<? extends M> getChild(
      InstantiableRelationDefinition<?, M> d, String name)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);
    ManagedObjectPath<?, ?> childPath = path.child(d, name);
    return getChild(childPath, d);
  }



  /**
   * Retrieve an optional child managed object.
   *
   * @param <M>
   *          The requested type of the child server managed object
   *          configuration.
   * @param d
   *          The optional relation definition.
   * @return Returns the optional child managed object.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   * @throws ConfigException
   *           If the child managed object could not be found or if it
   *           could not be decoded.
   */
  public <M extends Configuration> ServerManagedObject<? extends M> getChild(
      OptionalRelationDefinition<?, M> d) throws IllegalArgumentException,
      ConfigException {
    validateRelationDefinition(d);
    ManagedObjectPath<?, ?> childPath = path.child(d);
    return getChild(childPath, d);
  }



  /**
   * Retrieve a singleton child managed object.
   *
   * @param <M>
   *          The requested type of the child server managed object
   *          configuration.
   * @param d
   *          The singleton relation definition.
   * @return Returns the singleton child managed object.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ConfigException
   *           If the child managed object could not be found or if it
   *           could not be decoded.
   */
  public <M extends Configuration> ServerManagedObject<? extends M> getChild(
      SingletonRelationDefinition<?, M> d) throws IllegalArgumentException,
      ConfigException {
    validateRelationDefinition(d);
    ManagedObjectPath<?, ?> childPath = path.child(d);
    return getChild(childPath, d);
  }



  /**
   * Creates a server configuration view of this managed object.
   *
   * @return Returns the server configuration view of this managed
   *         object.
   */
  public S getConfiguration() {
    return definition.createServerConfiguration(this);
  }



  /**
   * Get the DN of the LDAP entry associated with this server managed
   * object.
   *
   * @return Returns the DN of the LDAP entry associated with this
   *         server managed object, or an null DN if this is the root
   *         managed object.
   */
  public DN getDN() {
    if (configEntry != null) {
      return configEntry.getDN();
    } else {
      return DN.nullDN();
    }
  }



  /**
   * Get the definition associated with this server managed object.
   *
   * @return Returns the definition associated with this server
   *         managed object.
   */
  public ManagedObjectDefinition<?, S> getManagedObjectDefinition() {
    return definition;
  }



  /**
   * Get the path of this server managed object.
   *
   * @return Returns the path of this server managed object.
   */
  public ManagedObjectPath<?, ?> getManagedObjectPath() {
    return path;
  }



  /**
   * Get the effective value of the specified property. If the
   * property is multi-valued then just the first value is returned.
   * If the property does not have a value then its default value is
   * returned if it has one, or <code>null</code> indicating that
   * any default behavior is applicable.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> indicating that any default behavior
   *         is applicable.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  public <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    Set<T> values = getPropertyValues(d);
    if (values.isEmpty()) {
      return null;
    } else {
      return values.iterator().next();
    }
  }



  /**
   * Get the effective values of the specified property. If the
   * property does not have any values then its default values are
   * returned if it has any, or an empty set indicating that any
   * default behavior is applicable.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns a newly allocated set containing a copy of the
   *         property's effective values. An empty set indicates that
   *         the property has no default values defined and any
   *         default behavior is applicable.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  @SuppressWarnings("unchecked")
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    if (!properties.containsKey(d)) {
      throw new IllegalArgumentException("Unknown property " + d.getName());
    }
    return new TreeSet<T>((SortedSet<T>) properties.get(d));
  }



  /**
   * Determines whether or not the optional managed object associated
   * with the specified optional relations exists.
   *
   * @param d
   *          The optional relation definition.
   * @return Returns <code>true</code> if the optional managed
   *         object exists, <code>false</code> otherwise.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   */
  public boolean hasChild(OptionalRelationDefinition<?, ?> d)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    // Get the configuration entry.
    DN targetDN = DNBuilder.create(path, d);
    try {
      return (getManagedObjectConfigEntry(targetDN) != null);
    } catch (ConfigException e) {
      // Assume it doesn't exist.
      return false;
    }
  }



  /**
   * Lists the child managed objects associated with the specified
   * instantiable relation.
   *
   * @param d
   *          The instantiable relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   */
  public String[] listChildren(InstantiableRelationDefinition<?, ?> d)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    // Get the target entry.
    DN targetDN = DNBuilder.create(path, d);
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
    ArrayList<String> names = new ArrayList<String>(children.size());
    for (DN child : children) {
      // Assume that RDNs are single-valued and can be trimmed.
      AttributeValue av = child.getRDN().getAttributeValue(0);
      names.add(av.getStringValue().trim());
    }

    return names.toArray(new String[names.size()]);
  }



  /**
   * Register to be notified when new child configurations are added
   * beneath an instantiable relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The configuration add listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           instantiable relation could not be retrieved.
   */
  public <M extends Configuration> void registerAddListener(
      InstantiableRelationDefinition<?, M> d,
      ConfigurationAddListener<M> listener) throws IllegalArgumentException,
      ConfigException {
    validateRelationDefinition(d);
    DN baseDN = DNBuilder.create(path, d);
    ConfigAddListener adaptor = new ConfigAddListenerAdaptor<M>(path, d,
        listener);
    registerAddListener(baseDN, adaptor);
  }



  /**
   * Register to be notified when a new child configurations is added
   * beneath an optional relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The configuration add listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           optional relation could not be retrieved.
   */
  public <M extends Configuration> void registerAddListener(
      OptionalRelationDefinition<?, M> d, ConfigurationAddListener<M> listener)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);
    DN baseDN = DNBuilder.create(path, d).getParent();
    ConfigAddListener adaptor = new ConfigAddListenerAdaptor<M>(path, d,
        listener);
    registerAddListener(baseDN, adaptor);
  }



  /**
   * Register to be notified when this server managed object is
   * changed.
   *
   * @param listener
   *          The configuration change listener.
   */
  public void registerChangeListener(
      ConfigurationChangeListener<? super S> listener) {
    ConfigChangeListener adaptor = new ConfigChangeListenerAdaptor<S>(path,
        definition, listener);
    configEntry.registerChangeListener(adaptor);
  }



  /**
   * Register to be notified when existing child configurations are
   * deleted beneath an instantiable relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The configuration delete listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           instantiable relation could not be retrieved.
   */
  public <M extends Configuration> void registerDeleteListener(
      InstantiableRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException,
      ConfigException {
    validateRelationDefinition(d);
    DN baseDN = DNBuilder.create(path, d);
    ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<M>(path, d,
        listener);
    registerDeleteListener(baseDN, adaptor);
  }



  /**
   * Register to be notified when an existing child configuration is
   * deleted beneath an optional relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The configuration delete listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           optional relation could not be retrieved.
   */
  public <M extends Configuration> void registerDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException,
      ConfigException {
    validateRelationDefinition(d);
    DN baseDN = DNBuilder.create(path, d).getParent();
    ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<M>(path, d,
        listener);
    registerDeleteListener(baseDN, adaptor);
  }



  /**
   * Update the config entry associated with this server managed
   * object. This is only intended to be used by change listener call
   * backs in order to update the managed object with the correct
   * config entry.
   *
   * @param configEntry
   *          The configuration entry.
   */
  void setConfigEntry(ConfigEntry configEntry) {
    this.configEntry = configEntry;
  }



  // Deregister an add listener.
  private <M extends Configuration> void deregisterAddListener(DN baseDN,
      ConfigurationAddListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigAddListener l : configEntry.getAddListeners()) {
          if (l instanceof ConfigAddListenerAdaptor) {
            ConfigAddListenerAdaptor<?> adaptor =
              (ConfigAddListenerAdaptor<?>) l;
            if (adaptor.getConfigurationAddListener() == listener) {
              configEntry.deregisterAddListener(adaptor);
            }
          }
        }
      }
    } catch (ConfigException e) {
      // Ignore the exception since this implies deregistration.
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  // Deregister a delete listener.
  private <M> void deregisterDeleteListener(DN baseDN,
      ConfigurationDeleteListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
          if (l instanceof ConfigDeleteListenerAdaptor) {
            ConfigDeleteListenerAdaptor<?> adaptor =
              (ConfigDeleteListenerAdaptor<?>) l;
            if (adaptor.getConfigurationDeleteListener() == listener) {
              configEntry.deregisterDeleteListener(adaptor);
            }
          }
        }
      }
    } catch (ConfigException e) {
      // Ignore the exception since this implies deregistration.
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  // Get a child managed object.
  private <M extends Configuration> ServerManagedObject<? extends M> getChild(
      ManagedObjectPath<?, ?> childPath, RelationDefinition<?, M> d)
      throws ConfigException {
    // Get the configuration entry.
    DN targetDN = DNBuilder.create(childPath);
    ConfigEntry configEntry = getManagedObjectConfigEntry(targetDN);
    try {
      return decode(childPath, d.getChildDefinition(), configEntry);
    } catch (DefinitionDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(targetDN, e);
    } catch (ServerManagedObjectDecodingException e) {
      throw ConfigExceptionFactory.getInstance()
          .createDecodingExceptionAdaptor(e);
    }
  }



  // Gets a config entry required for a listener and throws a config
  // exception on failure or returns null if the entry does not exist.
  private ConfigEntry getListenerConfigEntry(DN dn) throws ConfigException {
    // Attempt to retrieve the listener base entry.
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(dn);
    } catch (ConfigException e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = AdminMessages.ERR_ADMIN_CANNOT_GET_LISTENER_BASE.get(
          String.valueOf(dn), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }

    return configEntry;
  }



  // Register an instantiable or optional relation add listener.
  private void registerAddListener(DN baseDN, ConfigAddListener adaptor)
      throws IllegalArgumentException, ConfigException {
    ConfigEntry relationEntry = getListenerConfigEntry(baseDN);

    if (relationEntry != null) {
      relationEntry.registerAddListener(adaptor);
    } else {
      // The relation entry does not exist yet so register a delayed
      // add listener.
      ConfigAddListener delayedListener = new DelayedConfigAddListener(baseDN,
          adaptor);
      registerDelayedListener(baseDN, delayedListener);
    }
  }



  // Register a delayed listener with the nearest existing parent
  // entry to the provided base DN.
  private void registerDelayedListener(DN baseDN,
      ConfigAddListener delayedListener) throws ConfigException {
    DN parentDN = baseDN.getParent();
    while (parentDN != null) {
      ConfigEntry relationEntry = getListenerConfigEntry(parentDN);
      if (relationEntry == null) {
        delayedListener = new DelayedConfigAddListener(parentDN,
            delayedListener);
        parentDN = parentDN.getParent();
      } else {
        relationEntry.registerAddListener(delayedListener);
        return;
      }
    }

    // No parent entry could be found.
    Message message = AdminMessages.ERR_ADMIN_UNABLE_TO_REGISTER_LISTENER.get(
        String.valueOf(baseDN));
    throw new ConfigException(message);
  }



  // Register an instantiable or optional relation delete listener.
  private void registerDeleteListener(DN baseDN, ConfigDeleteListener adaptor)
      throws ConfigException {
    ConfigEntry relationEntry = getListenerConfigEntry(baseDN);

    if (relationEntry != null) {
      relationEntry.registerDeleteListener(adaptor);
    } else {
      // The relation entry does not exist yet so register a delayed
      // add listener.
      ConfigAddListener delayedListener = new DelayedConfigAddListener(baseDN,
          adaptor);
      registerDelayedListener(baseDN, delayedListener);
    }
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    RelationDefinition<?, ?> tmp =
      definition.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + definition.getName());
    }
  }
}
