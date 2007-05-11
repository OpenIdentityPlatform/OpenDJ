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



import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.InheritedDefaultValueProvider;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.StringPropertyProvider;
import org.opends.server.admin.client.PropertySet;
import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.messages.AdminMessages;
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
public final class ServerManagedObject<S extends Configuration>
    implements PropertyProvider {

  /**
   * Internal inherited default value provider implementation.
   */
  private static class MyInheritedDefaultValueProvider implements
      InheritedDefaultValueProvider {

    // The base path.
    private final ManagedObjectPath<?, ?> path;



    /**
     * Create a new inherited default value provider.
     *
     * @param path
     *          The base path.
     */
    public MyInheritedDefaultValueProvider(ManagedObjectPath path) {
      this.path = path;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<?> getDefaultPropertyValues(
        ManagedObjectPath path, String propertyName)
        throws OperationsException, PropertyNotFoundException {
      // Get the configuration entry.
      DN targetDN = DNBuilder.create(path);
      final ConfigEntry configEntry;
      try {
        configEntry = DirectoryServer.getConfigEntry(targetDN);
      } catch (ConfigException e) {
        throw new ManagedObjectNotFoundException(e);
      }

      if (configEntry == null) {
        throw new ManagedObjectNotFoundException();
      }

      ManagedObjectPath<?, ?> tmp = path;
      ServerManagedObject<?> mo = decode(tmp, tmp
          .getManagedObjectDefinition(), configEntry);
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
  static <S extends Configuration>
  ServerManagedObject<? extends S> decode(
      ManagedObjectPath path,
      AbstractManagedObjectDefinition<?, S> definition,
      final ConfigEntry configEntry)
      throws DefinitionDecodingException,
      ServerManagedObjectDecodingException {
    // First determine the correct definition to use for the entry.
    // This could either be the provided definition, or one of its
    // sub-definitions.
    DefinitionResolver resolver = new DefinitionResolver() {

      public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
        String oc = LDAPProfile.getInstance().getObjectClass(d);
        return configEntry.hasObjectClass(oc);
      }

    };

    final ManagedObjectDefinition<?, ? extends S> mod = definition
        .resolveManagedObjectDefinition(resolver);

    // Use a string-based property provider to pull in the property
    // values.
    StringPropertyProvider provider = new StringPropertyProvider() {

      public Collection<String> getPropertyValues(
          PropertyDefinition<?> d) throws IllegalArgumentException {
        String attrID = LDAPProfile.getInstance().getAttributeName(
            mod, d);
        // TODO: we create a default attribute type if it is
        // undefined. We should log a warning here if this is the case
        // since the attribute should have been defined.
        AttributeType type = DirectoryServer.getAttributeType(attrID, true);
        AttributeValueDecoder<String> decoder =
          new AttributeValueDecoder<String>() {

          public String decode(AttributeValue value)
              throws DirectoryException {
            return value.getStringValue();
          }
        };

        try {
          Collection<String> values = new LinkedList<String>();
          configEntry.getEntry().getAttributeValues(type, decoder,
              values);
          return values;
        } catch (DirectoryException e) {
          // Should not happen.
          throw new RuntimeException(e);
        }
      }

    };

    // Create the new managed object's property set, saving any
    // decoding exceptions.
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        path);
    PropertySet properties = PropertySet.create(mod, provider, i,
        exceptions);
    ServerManagedObject<? extends S> mo = decodeAux(path, mod,
        properties, configEntry);

    // If there were no decoding problems then return the object,
    // otherwise throw an operations exception.
    if (exceptions.isEmpty()) {
      return mo;
    } else {
      throw new ServerManagedObjectDecodingException(mo, exceptions);
    }
  }



  /**
   * Construct a root server managed object.
   *
   * @return Returns a root server managed object.
   */
  static ServerManagedObject<RootCfg> getRootManagedObject() {
    ManagedObjectPath path = ManagedObjectPath.emptyPath();
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    InheritedDefaultValueProvider i = new MyInheritedDefaultValueProvider(
        path);
    PropertySet properties = PropertySet.create(
        RootCfgDefn.getInstance(),
        PropertyProvider.DEFAULT_PROVIDER, i, exceptions);

    // Should never get any exceptions.
    if (!exceptions.isEmpty()) {
      throw new RuntimeException(
          "Got exceptions when creating root managed object");
    }

    return new ServerManagedObject<RootCfg>(path,
        RootCfgDefn.getInstance(), properties,
        null);
  }



  // Decode helper method required to avoid generics warning.
  private static <S extends Configuration> ServerManagedObject<S> decodeAux(
      ManagedObjectPath path, ManagedObjectDefinition<?, S> d,
      PropertySet properties, ConfigEntry configEntry) {
    return new ServerManagedObject<S>(path, d, properties,
        configEntry);
  }

  // The managed object's definition.
  private final ManagedObjectDefinition<?, S> definition;

  // The managed object path identifying this managed object's
  // location.
  private final ManagedObjectPath<?, ?> path;

  // The managed object's properties.
  private final PropertySet properties;

  // The configuration entry associated with this server managed
  // object (null if root).
  private ConfigEntry configEntry;



  // Create an new server side managed object.
  private ServerManagedObject(ManagedObjectPath path,
      ManagedObjectDefinition<?, S> d, PropertySet properties,
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
      ConfigurationAddListener<M> listener)
      throws IllegalArgumentException {
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
      OptionalRelationDefinition<?, M> d,
      ConfigurationAddListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path);
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
        ConfigChangeListenerAdaptor adaptor = (ConfigChangeListenerAdaptor) l;
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
      ConfigurationDeleteListener<M> listener)
      throws IllegalArgumentException {
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
      ConfigurationDeleteListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path);
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

    ManagedObjectPath childPath = path.child(d, name);
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
      OptionalRelationDefinition<?, M> d)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);

    // Get the configuration entry.
    ManagedObjectPath childPath = path.child(d);
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
      SingletonRelationDefinition<?, M> d)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);

    // Get the configuration entry.
    ManagedObjectPath childPath = path.child(d);
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
  public ManagedObjectPath getManagedObjectPath() {
    return path;
  }



  /**
   * Get the current value of the specified property.
   * <p>
   * If the value has been modified then the new value is returned,
   * otherwise the original value will be returned.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's current value, or
   *         <code>null</code> if there is no value(s) associated
   *         with the property and any default behavior is
   *         applicable.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  public <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValue(d);
  }



  /**
   * Get the current values of the specified property.
   * <p>
   * If the property has been modified then the new values are
   * returned, otherwise the original values will be returned.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns a newly allocated set containing a copy of the
   *         property's current values. An empty set indicates that
   *         the property has no values defined and any default
   *         behavior is applicable.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  @SuppressWarnings("unchecked")
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValues(d);
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
      ConfigurationAddListener<M> listener)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d);
    ConfigEntry relationEntry = getListenerConfigEntry(baseDN);
    ConfigAddListener adaptor = new ConfigAddListenerAdaptor<M>(path,
        d, listener);

    if (relationEntry != null) {
      relationEntry.registerAddListener(adaptor);
    } else {
      // The relation entry does not exist yet so register a delayed
      // add listener.
      ConfigAddListener delayedListener = new DelayedConfigAddListener(
          configEntry.getDN(), baseDN, adaptor);
      configEntry.registerAddListener(delayedListener);
    }
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
   */
  public <M extends Configuration> void registerAddListener(
      OptionalRelationDefinition<?, M> d,
      ConfigurationAddListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    ConfigAddListener adaptor = new ConfigAddListenerAdaptor<M>(path,
        d, listener);
    configEntry.registerAddListener(adaptor);
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
    ConfigChangeListener adaptor = new ConfigChangeListenerAdaptor<S>(
        path, definition, listener);
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
      ConfigurationDeleteListener<M> listener)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d);
    ConfigEntry relationEntry = getListenerConfigEntry(baseDN);
    ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<M>(
        path, d, listener);

    if (relationEntry != null) {
      relationEntry.registerDeleteListener(adaptor);
    } else {
      // The relation entry does not exist yet so register a delayed
      // add listener.
      ConfigAddListener delayedListener = new DelayedConfigAddListener(
          configEntry.getDN(), baseDN, adaptor);
      configEntry.registerAddListener(delayedListener);
    }
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
   */
  public <M extends Configuration> void registerDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<M>(
        path, d, listener);
    configEntry.registerDeleteListener(adaptor);
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
  private <M extends Configuration> void deregisterAddListener(
      DN baseDN, ConfigurationAddListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigAddListener l : configEntry.getAddListeners()) {
          if (l instanceof ConfigAddListenerAdaptor) {
            ConfigAddListenerAdaptor adaptor = (ConfigAddListenerAdaptor) l;
            if (adaptor.getConfigurationAddListener() == listener) {
              configEntry.deregisterAddListener(adaptor);
            }
          }
        }
      }
    } catch (ConfigException e) {
      // Ignore the exception since this implies deregistration.
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  // Deregister a delete listener.
  private <M> void deregisterDeleteListener(DN baseDN,
      ConfigurationDeleteListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigDeleteListener l : configEntry
            .getDeleteListeners()) {
          if (l instanceof ConfigDeleteListenerAdaptor) {
            ConfigDeleteListenerAdaptor adaptor =
              (ConfigDeleteListenerAdaptor) l;
            if (adaptor.getConfigurationDeleteListener() == listener) {
              configEntry.deregisterDeleteListener(adaptor);
            }
          }
        }
      }
    } catch (ConfigException e) {
      // Ignore the exception since this implies deregistration.
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  // Get a child managed object.
  private <M extends Configuration> ServerManagedObject<? extends M> getChild(
      ManagedObjectPath childPath, RelationDefinition<?, M> d)
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
  private ConfigEntry getListenerConfigEntry(DN dn)
      throws ConfigException {
    // Attempt to retrieve the listener base entry.
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(dn);
    } catch (ConfigException e) {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = AdminMessages.MSGID_ADMIN_CANNOT_GET_LISTENER_BASE;
      String message = getMessage(msgID, String.valueOf(dn),
          stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }

    return configEntry;
  }



  // Gets a config entry required for a managed object and throws a
  // config exception on failure.
  private ConfigEntry getManagedObjectConfigEntry(DN dn)
      throws ConfigException {
    ConfigEntry configEntry;
    try {
      configEntry = DirectoryServer.getConfigEntry(dn);
    } catch (ConfigException e) {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = AdminMessages.MSGID_ADMIN_CANNOT_GET_MANAGED_OBJECT;
      String message = getMessage(msgID, String.valueOf(dn),
          stackTraceToSingleLineString(e));
      throw new ConfigException(msgID, message, e);
    }

    // The configuration handler is free to return null indicating
    // that the entry does not exist.
    if (configEntry == null) {
      int msgID = AdminMessages.MSGID_ADMIN_MANAGED_OBJECT_DOES_NOT_EXIST;
      String message = getMessage(msgID, String.valueOf(dn));
      throw new ConfigException(msgID, message);
    }

    return configEntry;
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    RelationDefinition tmp = definition.getRelationDefinition(rd
        .getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation "
          + rd.getName() + " is not associated with a "
          + definition.getName());
    }
  }
}
