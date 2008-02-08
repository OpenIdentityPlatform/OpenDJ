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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin.server;



import static org.opends.messages.AdminMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.opends.messages.AdminMessages;
import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;



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
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The configuration entry associated with this server managed
  // object (null if root).
  private ConfigEntry configEntry;

  // The management context.
  private final ServerManagementContext context = ServerManagementContext
      .getInstance();

  // The managed object's definition.
  private final ManagedObjectDefinition<?, S> definition;

  // The managed object path identifying this managed object's
  // location.
  private final ManagedObjectPath<?, S> path;

  // The managed object's properties.
  private final Map<PropertyDefinition<?>, SortedSet<?>> properties;



  /**
   * Creates an new server side managed object.
   *
   * @param path
   *          The managed object path.
   * @param d
   *          The managed object definition.
   * @param properties
   *          The managed object's properties.
   * @param configEntry
   *          The configuration entry associated with the managed
   *          object.
   */
  ServerManagedObject(ManagedObjectPath<?, S> path,
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
   * Deregisters an existing server managed object add listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The server managed object add listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   */
  public <M extends Configuration> void deregisterAddListener(
      InstantiableRelationDefinition<?, M> d,
      ServerManagedObjectAddListener<M> listener)
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
      OptionalRelationDefinition<?, M> d, ConfigurationAddListener<M> listener)
      throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d).getParent();
    deregisterAddListener(baseDN, listener);
  }



  /**
   * Deregisters an existing server managed object add listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The server managed object add listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   */
  public <M extends Configuration> void deregisterAddListener(
      OptionalRelationDefinition<?, M> d,
      ServerManagedObjectAddListener<M> listener)
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
        ServerManagedObjectChangeListener<?> l2 = adaptor
            .getServerManagedObjectChangeListener();
        if (l2 instanceof ServerManagedObjectChangeListenerAdaptor<?>) {
          ServerManagedObjectChangeListenerAdaptor<?> adaptor2 =
            (ServerManagedObjectChangeListenerAdaptor<?>) l2;
          if (adaptor2.getConfigurationChangeListener() == listener) {
            adaptor.finalizeChangeListener();
            configEntry.deregisterChangeListener(adaptor);
          }
        }
      }
    }
  }



  /**
   * Deregisters an existing server managed object change listener.
   *
   * @param listener
   *          The server managed object change listener.
   */
  public void deregisterChangeListener(
      ServerManagedObjectChangeListener<? super S> listener) {
    for (ConfigChangeListener l : configEntry.getChangeListeners()) {
      if (l instanceof ConfigChangeListenerAdaptor) {
        ConfigChangeListenerAdaptor<?> adaptor =
          (ConfigChangeListenerAdaptor<?>) l;
        if (adaptor.getServerManagedObjectChangeListener() == listener) {
          adaptor.finalizeChangeListener();
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
   * Deregisters an existing server managed object delete listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The server managed object delete listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   */
  public <M extends Configuration> void deregisterDeleteListener(
      InstantiableRelationDefinition<?, M> d,
      ServerManagedObjectDeleteListener<M> listener)
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
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException {
    validateRelationDefinition(d);

    DN baseDN = DNBuilder.create(path, d).getParent();
    deregisterDeleteListener(baseDN, listener);
  }



  /**
   * Deregisters an existing server managed object delete listener.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The server managed object delete listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   */
  public <M extends Configuration> void deregisterDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ServerManagedObjectDeleteListener<M> listener)
      throws IllegalArgumentException {
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
    return context.getManagedObject(path.child(d, name));
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
    return context.getManagedObject(path.child(d));
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
    return context.getManagedObject(path.child(d));
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
  public ManagedObjectPath<?, S> getManagedObjectPath() {
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
   * @return Returns an unmodifiable set containing the property's
   *         effective values. An empty set indicates that the
   *         property has no default values defined and any default
   *         behavior is applicable.
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
    return Collections.unmodifiableSortedSet((SortedSet<T>) properties.get(d));
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
    return context.managedObjectExists(path.child(d));
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
    return context.listManagedObjects(path, d);
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
    registerAddListener(d, new ServerManagedObjectAddListenerAdaptor<M>(
        listener));
  }



  /**
   * Register to be notified when new child server managed object are
   * added beneath an instantiable relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The server managed object add listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           instantiable relation could not be retrieved.
   */
  public <M extends Configuration> void registerAddListener(
      InstantiableRelationDefinition<?, M> d,
      ServerManagedObjectAddListener<M> listener)
      throws IllegalArgumentException, ConfigException {
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
   *           If the configuration entry associated with the optional
   *           relation could not be retrieved.
   */
  public <M extends Configuration> void registerAddListener(
      OptionalRelationDefinition<?, M> d, ConfigurationAddListener<M> listener)
      throws IllegalArgumentException, ConfigException {
    registerAddListener(d, new ServerManagedObjectAddListenerAdaptor<M>(
        listener));
  }



  /**
   * Register to be notified when a new child server managed object is
   * added beneath an optional relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The server managed object add listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the optional
   *           relation could not be retrieved.
   */
  public <M extends Configuration> void registerAddListener(
      OptionalRelationDefinition<?, M> d,
      ServerManagedObjectAddListener<M> listener)
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
    registerChangeListener(new ServerManagedObjectChangeListenerAdaptor<S>(
        listener));
  }



  /**
   * Register to be notified when this server managed object is
   * changed.
   *
   * @param listener
   *          The server managed object change listener.
   */
  public void registerChangeListener(
      ServerManagedObjectChangeListener<? super S> listener) {
    ConfigChangeListener adaptor = new ConfigChangeListenerAdaptor<S>(path,
        listener);
    configEntry.registerChangeListener(adaptor);

    // Change listener registration usually signifies that a managed
    // object has been accepted and added to the server configuration
    // during initialization post-add.

    // FIXME: we should prevent multiple invocations in the case where
    // multiple change listeners are registered for the same object.
    for (Constraint constraint : definition.getAllConstraints()) {
      for (ServerConstraintHandler handler : constraint
          .getServerConstraintHandlers()) {
        try {
          handler.performPostAdd(this);
        } catch (ConfigException e) {
          if (debugEnabled()) {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
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
    registerDeleteListener(d, new ServerManagedObjectDeleteListenerAdaptor<M>(
        listener));
  }



  /**
   * Register to be notified when existing child server managed
   * objects are deleted beneath an instantiable relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The instantiable relation definition.
   * @param listener
   *          The server managed objects delete listener.
   * @throws IllegalArgumentException
   *           If the instantiable relation definition is not
   *           associated with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the
   *           instantiable relation could not be retrieved.
   */
  public <M extends Configuration> void registerDeleteListener(
      InstantiableRelationDefinition<?, M> d,
      ServerManagedObjectDeleteListener<M> listener)
      throws IllegalArgumentException, ConfigException {
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
   *           If the configuration entry associated with the optional
   *           relation could not be retrieved.
   */
  public <M extends Configuration> void registerDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ConfigurationDeleteListener<M> listener) throws IllegalArgumentException,
      ConfigException {
    registerDeleteListener(d, new ServerManagedObjectDeleteListenerAdaptor<M>(
        listener));
  }



  /**
   * Register to be notified when an existing child server managed
   * object is deleted beneath an optional relation.
   *
   * @param <M>
   *          The type of the child server configuration object.
   * @param d
   *          The optional relation definition.
   * @param listener
   *          The server managed object delete listener.
   * @throws IllegalArgumentException
   *           If the optional relation definition is not associated
   *           with this managed object's definition.
   * @throws ConfigException
   *           If the configuration entry associated with the optional
   *           relation could not be retrieved.
   */
  public <M extends Configuration> void registerDeleteListener(
      OptionalRelationDefinition<?, M> d,
      ServerManagedObjectDeleteListener<M> listener)
      throws IllegalArgumentException, ConfigException {
    validateRelationDefinition(d);
    DN baseDN = DNBuilder.create(path, d).getParent();
    ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<M>(path, d,
        listener);
    registerDeleteListener(baseDN, adaptor);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();

    builder.append("{ TYPE=");
    builder.append(definition.getName());
    builder.append(", DN=\"");
    builder.append(getDN());
    builder.append('\"');
    for (Map.Entry<PropertyDefinition<?>, SortedSet<?>> value : properties
        .entrySet()) {
      builder.append(", ");
      builder.append(value.getKey().getName());
      builder.append('=');
      builder.append(value.getValue());
    }
    builder.append(" }");

    return builder.toString();
  }



  /**
   * Determines whether or not this managed object can be used by the
   * server.
   *
   * @throws ConstraintViolationException
   *           If one or more constraints determined that this managed
   *           object cannot be used by the server.
   */
  void ensureIsUsable() throws ConstraintViolationException {
    // Enforce any constraints.
    boolean isUsable = true;
    List<Message> reasons = new LinkedList<Message>();
    for (Constraint constraint : definition.getAllConstraints()) {
      for (ServerConstraintHandler handler : constraint
          .getServerConstraintHandlers()) {
        try {
          if (!handler.isUsable(this, reasons)) {
            isUsable = false;
          }
        } catch (ConfigException e) {
          Message message = ERR_SERVER_CONSTRAINT_EXCEPTION.get(e
              .getMessageObject());
          reasons.add(message);
          isUsable = false;
        }
      }
    }

    if (!isUsable) {
      throw new ConstraintViolationException(this, reasons);
    }
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
            ServerManagedObjectAddListener<?> l2 = adaptor
                .getServerManagedObjectAddListener();
            if (l2 instanceof ServerManagedObjectAddListenerAdaptor<?>) {
              ServerManagedObjectAddListenerAdaptor<?> adaptor2 =
                (ServerManagedObjectAddListenerAdaptor<?>) l2;
              if (adaptor2.getConfigurationAddListener() == listener) {
                configEntry.deregisterAddListener(adaptor);
              }
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



  // Deregister an add listener.
  private <M extends Configuration> void deregisterAddListener(DN baseDN,
      ServerManagedObjectAddListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigAddListener l : configEntry.getAddListeners()) {
          if (l instanceof ConfigAddListenerAdaptor) {
            ConfigAddListenerAdaptor<?> adaptor =
              (ConfigAddListenerAdaptor<?>) l;
            if (adaptor.getServerManagedObjectAddListener() == listener) {
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
  private <M extends Configuration> void deregisterDeleteListener(DN baseDN,
      ConfigurationDeleteListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
          if (l instanceof ConfigDeleteListenerAdaptor) {
            ConfigDeleteListenerAdaptor<?> adaptor =
              (ConfigDeleteListenerAdaptor<?>) l;
            ServerManagedObjectDeleteListener<?> l2 = adaptor
                .getServerManagedObjectDeleteListener();
            if (l2 instanceof ServerManagedObjectDeleteListenerAdaptor<?>) {
              ServerManagedObjectDeleteListenerAdaptor<?> adaptor2 =
                (ServerManagedObjectDeleteListenerAdaptor<?>) l2;
              if (adaptor2.getConfigurationDeleteListener() == listener) {
                configEntry.deregisterDeleteListener(adaptor);
              }
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
  private <M extends Configuration> void deregisterDeleteListener(DN baseDN,
      ServerManagedObjectDeleteListener<M> listener) {
    try {
      ConfigEntry configEntry = getListenerConfigEntry(baseDN);
      if (configEntry != null) {
        for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
          if (l instanceof ConfigDeleteListenerAdaptor) {
            ConfigDeleteListenerAdaptor<?> adaptor =
              (ConfigDeleteListenerAdaptor<?>) l;
            if (adaptor.getServerManagedObjectDeleteListener() == listener) {
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
    Message message = AdminMessages.ERR_ADMIN_UNABLE_TO_REGISTER_LISTENER
        .get(String.valueOf(baseDN));
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
    RelationDefinition<?, ?> tmp = definition.getRelationDefinition(rd
        .getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + definition.getName());
    }
  }
}
