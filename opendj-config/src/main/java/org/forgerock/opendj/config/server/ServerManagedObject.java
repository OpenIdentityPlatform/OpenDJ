/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.server;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyProvider;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A server-side managed object.
 *
 * @param <S>
 *            The type of server configuration represented by the server managed
 *            object.
 */
public final class ServerManagedObject<S extends Configuration> implements PropertyProvider {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagedObject.class);

    /**
     * The DN of configuration entry associated with this server managed object,
     * which is {@code null} for root.
     */
    private DN configDN;

    private final ServerManagementContext serverContext;

    private final ConfigurationRepository configRepository;

    private final ManagedObjectDefinition<?, S> definition;

    /** The managed object path identifying this managed object's location. */
    private final ManagedObjectPath<?, S> path;

    private final Map<PropertyDefinition<?>, SortedSet<?>> properties;

    /**
     * Creates an new server side managed object.
     *
     * @param path
     *            The managed object path.
     * @param definition
     *            The managed object definition.
     * @param properties
     *            The managed object's properties.
     * @param configDN
     *            The configuration entry associated with the managed object.
     * @param context
     *            The server management context.
     */
    ServerManagedObject(final ManagedObjectPath<?, S> path, final ManagedObjectDefinition<?, S> definition,
            final Map<PropertyDefinition<?>, SortedSet<?>> properties, final DN configDN,
            final ServerManagementContext context) {
        this.definition = definition;
        this.path = path;
        this.properties = properties;
        this.configDN = configDN;
        this.serverContext = context;
        this.configRepository = context.getConfigRepository();
    }

    /**
     * Deregisters an existing configuration add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(InstantiableRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(InstantiableRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing configuration add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(OptionalRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d).parent();
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(OptionalRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d).parent();
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing configuration add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(SetRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object add listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     */
    public <M extends Configuration> void deregisterAddListener(SetRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        deregisterAddListener(baseDN, listener);
    }

    /**
     * Deregisters an existing configuration change listener.
     *
     * @param listener
     *            The configuration change listener.
     */
    public void deregisterChangeListener(ConfigurationChangeListener<? super S> listener) {
        for (ConfigChangeListener l : configRepository.getChangeListeners(configDN)) {
            if (l instanceof ConfigChangeListenerAdaptor) {
                ConfigChangeListenerAdaptor<?> adaptor = (ConfigChangeListenerAdaptor<?>) l;
                ServerManagedObjectChangeListener<?> l2 = adaptor.getServerManagedObjectChangeListener();
                if (l2 instanceof ServerManagedObjectChangeListenerAdaptor<?>) {
                    ServerManagedObjectChangeListenerAdaptor<?> adaptor2 =
                        (ServerManagedObjectChangeListenerAdaptor<?>) l2;
                    if (adaptor2.getConfigurationChangeListener() == listener) {
                        adaptor.finalizeChangeListener();
                        configRepository.deregisterChangeListener(configDN, adaptor);
                    }
                }
            }
        }
    }

    /**
     * Deregisters an existing server managed object change listener.
     *
     * @param listener
     *            The server managed object change listener.
     */
    public void deregisterChangeListener(ServerManagedObjectChangeListener<? super S> listener) {
        for (ConfigChangeListener l : configRepository.getChangeListeners(configDN)) {
            if (l instanceof ConfigChangeListenerAdaptor) {
                ConfigChangeListenerAdaptor<?> adaptor = (ConfigChangeListenerAdaptor<?>) l;
                if (adaptor.getServerManagedObjectChangeListener() == listener) {
                    adaptor.finalizeChangeListener();
                    configRepository.deregisterChangeListener(configDN, adaptor);
                }
            }
        }
    }

    /**
     * Deregisters an existing configuration delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(InstantiableRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d);
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The server managed object delete listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(InstantiableRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d);
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Deregisters an existing configuration delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(OptionalRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d).parent();
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The server managed object delete listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(OptionalRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d).parent();
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Deregisters an existing configuration delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(SetRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d);
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Deregisters an existing server managed object delete listener.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The server managed object delete listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     */
    public <M extends Configuration> void deregisterDeleteListener(SetRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) {
        validateRelationDefinition(d);

        DN baseDN = DNBuilder.create(path, d);
        deregisterDeleteListener(baseDN, listener);
    }

    /**
     * Retrieve an instantiable child managed object.
     *
     * @param <M>
     *            The requested type of the child server managed object
     *            configuration.
     * @param d
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object.
     * @return Returns the instantiable child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the child managed object could not be found or if it could
     *             not be decoded.
     */
    public <M extends Configuration> ServerManagedObject<? extends M> getChild(InstantiableRelationDefinition<?, M> d,
            String name) throws ConfigException {
        validateRelationDefinition(d);
        return serverContext.getManagedObject(path.child(d, name));
    }

    /**
     * Retrieve an optional child managed object.
     *
     * @param <M>
     *            The requested type of the child server managed object
     *            configuration.
     * @param d
     *            The optional relation definition.
     * @return Returns the optional child managed object.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     * @throws ConfigException
     *             If the child managed object could not be found or if it could
     *             not be decoded.
     */
    public <M extends Configuration> ServerManagedObject<? extends M> getChild(OptionalRelationDefinition<?, M> d)
            throws ConfigException {
        validateRelationDefinition(d);
        return serverContext.getManagedObject(path.child(d));
    }

    /**
     * Retrieve a set child managed object.
     *
     * @param <M>
     *            The requested type of the child server managed object
     *            configuration.
     * @param d
     *            The set relation definition.
     * @param name
     *            The name of the child managed object.
     * @return Returns the set child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition or if {@code name} specifies a
     *             managed object definition which is not a sub-type of the
     *             relation's child definition.
     * @throws ConfigException
     *             If the child managed object could not be found or if it could
     *             not be decoded.
     */
    public <M extends Configuration> ServerManagedObject<? extends M> getChild(SetRelationDefinition<?, M> d,
            String name) throws ConfigException {
        validateRelationDefinition(d);

        return serverContext.getManagedObject(path.child(d, name));
    }

    /**
     * Retrieve a singleton child managed object.
     *
     * @param <M>
     *            The requested type of the child server managed object
     *            configuration.
     * @param d
     *            The singleton relation definition.
     * @return Returns the singleton child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the child managed object could not be found or if it could
     *             not be decoded.
     */
    public <M extends Configuration> ServerManagedObject<? extends M> getChild(SingletonRelationDefinition<?, M> d)
            throws ConfigException {
        validateRelationDefinition(d);
        return serverContext.getManagedObject(path.child(d));
    }

    /**
     * Returns the server management context used by this object.
     *
     * @return the context
     */
    public ServerManagementContext getServerContext() {
        return serverContext;
    }

    /**
     * Creates a server configuration view of this managed object.
     *
     * @return Returns the server configuration view of this managed object.
     */
    public S getConfiguration() {
        return definition.createServerConfiguration(this);
    }

    /**
     * Get the DN of the LDAP entry associated with this server managed object.
     *
     * @return Returns the DN of the LDAP entry associated with this server
     *         managed object, or an null DN if this is the root managed object.
     */
    public DN getDN() {
        if (configDN != null) {
            return configDN;
        }
        return DN.rootDN();
    }

    /**
     * Get the definition associated with this server managed object.
     *
     * @return Returns the definition associated with this server managed
     *         object.
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
     * Get the effective value of the specified property. If the property is
     * multi-valued then just the first value is returned. If the property does
     * not have a value then its default value is returned if it has one, or
     * <code>null</code> indicating that any default behavior is applicable.
     *
     * @param <T>
     *            The type of the property to be retrieved.
     * @param d
     *            The property to be retrieved.
     * @return Returns the property's effective value, or <code>null</code>
     *         indicating that any default behavior is applicable.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    public <T> T getPropertyValue(PropertyDefinition<T> d) {
        Set<T> values = getPropertyValues(d);
        if (!values.isEmpty()) {
            return values.iterator().next();
        }
        return null;
    }

    /**
     * Get the effective values of the specified property. If the property does
     * not have any values then its default values are returned if it has any,
     * or an empty set indicating that any default behavior is applicable.
     *
     * @param <T>
     *            The type of the property to be retrieved.
     * @param d
     *            The property to be retrieved.
     * @return Returns an unmodifiable set containing the property's effective
     *         values. An empty set indicates that the property has no default
     *         values defined and any default behavior is applicable.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d) {
        if (!properties.containsKey(d)) {
            throw new IllegalArgumentException("Unknown property " + d.getName());
        }
        return Collections.unmodifiableSortedSet((SortedSet<T>) properties.get(d));
    }

    /**
     * Determines whether the optional managed object associated with the
     * specified optional relations exists.
     *
     * @param d
     *            The optional relation definition.
     * @return Returns <code>true</code> if the optional managed object exists,
     *         <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     */
    public boolean hasChild(OptionalRelationDefinition<?, ?> d) {
        validateRelationDefinition(d);
        return serverContext.managedObjectExists(path.child(d));
    }

    /**
     * Lists the child managed objects associated with the specified
     * instantiable relation.
     *
     * @param d
     *            The instantiable relation definition.
     * @return Returns the names of the child managed objects.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     */
    public String[] listChildren(InstantiableRelationDefinition<?, ?> d) {
        validateRelationDefinition(d);
        return serverContext.listManagedObjects(path, d);
    }

    /**
     * Lists the child managed objects associated with the specified set
     * relation.
     *
     * @param d
     *            The set relation definition.
     * @return Returns the names of the child managed objects.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     */
    public String[] listChildren(SetRelationDefinition<?, ?> d) {
        validateRelationDefinition(d);
        return serverContext.listManagedObjects(path, d);
    }

    /**
     * Register to be notified when new child configurations are added beneath
     * an instantiable relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the instantiable
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(InstantiableRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) throws ConfigException {
        registerAddListener(d, new ServerManagedObjectAddListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when new child server managed object are added
     * beneath an instantiable relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the instantiable
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(InstantiableRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        ConfigAddListener adaptor = new ConfigAddListenerAdaptor<>(serverContext, path, d, listener);
        registerAddListener(baseDN, adaptor);
    }

    /**
     * Register to be notified when a new child configurations is added beneath
     * an optional relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the optional
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(OptionalRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) throws ConfigException {
        registerAddListener(d, new ServerManagedObjectAddListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when a new child server managed object is added
     * beneath an optional relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the optional
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(OptionalRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d).parent();
        ConfigAddListener adaptor = new ConfigAddListenerAdaptor<>(serverContext, path, d, listener);
        registerAddListener(baseDN, adaptor);
    }

    /**
     * Register to be notified when new child configurations are added beneath a
     * set relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The configuration add listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the set relation
     *             could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(SetRelationDefinition<?, M> d,
            ConfigurationAddListener<M> listener) throws ConfigException {
        registerAddListener(d, new ServerManagedObjectAddListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when new child server managed object are added
     * beneath a set relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The server managed object add listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the set relation
     *             could not be retrieved.
     */
    public <M extends Configuration> void registerAddListener(SetRelationDefinition<?, M> d,
            ServerManagedObjectAddListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        ConfigAddListener adaptor = new ConfigAddListenerAdaptor<>(serverContext, path, d, listener);
        registerAddListener(baseDN, adaptor);
    }

    /**
     * Register to be notified when this server managed object is changed.
     *
     * @param listener
     *            The configuration change listener.
     */
    public void registerChangeListener(ConfigurationChangeListener<? super S> listener) {
        registerChangeListener(new ServerManagedObjectChangeListenerAdaptor<S>(listener));
    }

    /**
     * Register to be notified when this server managed object is changed.
     *
     * @param listener
     *            The server managed object change listener.
     */
    public void registerChangeListener(ServerManagedObjectChangeListener<? super S> listener) {
        ConfigChangeListener adaptor = new ConfigChangeListenerAdaptor<>(serverContext, path, listener);
        configRepository.registerChangeListener(configDN, adaptor);

        // TODO : go toward this
        // Entry entry;
        // configBackend.registerChangeListener(entry.getName(), adapter));

        // Change listener registration usually signifies that a managed
        // object has been accepted and added to the server configuration
        // during initialization post-add.

        // FIXME: we should prevent multiple invocations in the case where
        // multiple change listeners are registered for the same object.
        for (Constraint constraint : definition.getAllConstraints()) {
            for (ServerConstraintHandler handler : constraint.getServerConstraintHandlers()) {
                try {
                    handler.performPostAdd(this);
                } catch (ConfigException e) {
                    logger.trace("Unable to perform post add", e);
                }
            }
        }
    }

    /**
     * Register to be notified when existing child configurations are deleted
     * beneath an instantiable relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the instantiable
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(InstantiableRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) throws ConfigException {
        registerDeleteListener(d, new ServerManagedObjectDeleteListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when existing child server managed objects are
     * deleted beneath an instantiable relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The instantiable relation definition.
     * @param listener
     *            The server managed objects delete listener.
     * @throws IllegalArgumentException
     *             If the instantiable relation definition is not associated
     *             with this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the instantiable
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(InstantiableRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<>(serverContext, path, d, listener);
        registerDeleteListener(baseDN, adaptor);
    }

    /**
     * Register to be notified when an existing child configuration is deleted
     * beneath an optional relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the optional
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(OptionalRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) throws ConfigException {
        registerDeleteListener(d, new ServerManagedObjectDeleteListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when an existing child server managed object is
     * deleted beneath an optional relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The optional relation definition.
     * @param listener
     *            The server managed object delete listener.
     * @throws IllegalArgumentException
     *             If the optional relation definition is not associated with
     *             this managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the optional
     *             relation could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(OptionalRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d).parent();
        ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<>(serverContext, path, d, listener);
        registerDeleteListener(baseDN, adaptor);
    }

    /**
     * Register to be notified when existing child configurations are deleted
     * beneath a set relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The configuration delete listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the set relation
     *             could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(SetRelationDefinition<?, M> d,
            ConfigurationDeleteListener<M> listener) throws ConfigException {
        registerDeleteListener(d, new ServerManagedObjectDeleteListenerAdaptor<M>(listener));
    }

    /**
     * Register to be notified when existing child server managed objects are
     * deleted beneath a set relation.
     *
     * @param <M>
     *            The type of the child server configuration object.
     * @param d
     *            The set relation definition.
     * @param listener
     *            The server managed objects delete listener.
     * @throws IllegalArgumentException
     *             If the set relation definition is not associated with this
     *             managed object's definition.
     * @throws ConfigException
     *             If the configuration entry associated with the set relation
     *             could not be retrieved.
     */
    public <M extends Configuration> void registerDeleteListener(SetRelationDefinition<?, M> d,
            ServerManagedObjectDeleteListener<M> listener) throws ConfigException {
        validateRelationDefinition(d);
        DN baseDN = DNBuilder.create(path, d);
        ConfigDeleteListener adaptor = new ConfigDeleteListenerAdaptor<>(serverContext, path, d, listener);
        registerDeleteListener(baseDN, adaptor);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("{ TYPE=");
        builder.append(definition.getName());
        builder.append(", DN=\"");
        builder.append(getDN());
        builder.append('\"');
        for (Map.Entry<PropertyDefinition<?>, SortedSet<?>> value : properties.entrySet()) {
            builder.append(", ");
            builder.append(value.getKey().getName());
            builder.append('=');
            builder.append(value.getValue());
        }
        builder.append(" }");

        return builder.toString();
    }

    /**
     * Determines whether this managed object can be used by the server.
     *
     * @throws ConstraintViolationException
     *             If one or more constraints determined that this managed
     *             object cannot be used by the server.
     */
    void ensureIsUsable() throws ConstraintViolationException {
        // Enforce any constraints.
        boolean isUsable = true;
        List<LocalizableMessage> reasons = new LinkedList<>();
        for (Constraint constraint : definition.getAllConstraints()) {
            for (ServerConstraintHandler handler : constraint.getServerConstraintHandlers()) {
                try {
                    if (!handler.isUsable(this, reasons)) {
                        isUsable = false;
                    }
                } catch (ConfigException e) {
                    LocalizableMessage message = ERR_SERVER_CONSTRAINT_EXCEPTION.get(e.getMessageObject());
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
     * Update the config DN associated with this server managed object. This
     * is only intended to be used by change listener call backs in order to
     * update the managed object with the correct config DN.
     *
     * @param configDN
     *            The DN of the underlying configuration entry.
     */
    void setConfigDN(DN configDN) {
        this.configDN = configDN;
    }

    /** Deregister an add listener. */
    private <M extends Configuration> void deregisterAddListener(DN baseDN, ConfigurationAddListener<M> listener) {
        try {
            if (configRepository.hasEntry(baseDN)) {
                for (ConfigAddListener configListener : configRepository.getAddListeners(baseDN)) {
                    if (configListener instanceof ConfigAddListenerAdaptor) {
                        ConfigAddListenerAdaptor<?> adaptor = (ConfigAddListenerAdaptor<?>) configListener;
                        ServerManagedObjectAddListener<?> smoListener = adaptor.getServerManagedObjectAddListener();
                        if (smoListener instanceof ServerManagedObjectAddListenerAdaptor<?>) {
                            ServerManagedObjectAddListenerAdaptor<?> adaptor2 =
                                (ServerManagedObjectAddListenerAdaptor<?>) smoListener;
                            if (adaptor2.getConfigurationAddListener() == listener) {
                                configRepository.deregisterAddListener(baseDN, adaptor);
                            }
                        }
                    }
                }
            } else {
                // The relation entry does not exist so check for and deregister
                // delayed add listener.
                deregisterDelayedAddListener(baseDN, listener);
            }
        } catch (ConfigException e) {
            // Ignore the exception since this implies deregistration.
            logger.trace("Unable to deregister add listener", e);
        }
    }

    /** Deregister an add listener. */
    private <M extends Configuration> void deregisterAddListener(DN baseDN,
        ServerManagedObjectAddListener<M> listener) {
        try {
            if (configRepository.hasEntry(baseDN)) {
                for (ConfigAddListener configListener : configRepository.getAddListeners(baseDN)) {
                    if (configListener instanceof ConfigAddListenerAdaptor) {
                        ConfigAddListenerAdaptor<?> adaptor = (ConfigAddListenerAdaptor<?>) configListener;
                        if (adaptor.getServerManagedObjectAddListener() == listener) {
                            configRepository.deregisterAddListener(baseDN, adaptor);
                        }
                    }
                }
            } else {
                // The relation entry does not exist so check for and deregister
                // delayed add listener.
                deregisterDelayedAddListener(baseDN, listener);
            }
        } catch (ConfigException e) {
            // Ignore the exception since this implies deregistration.
            logger.trace("Unable to deregister add listener", e);
        }
    }

    /**
     * Convenience method to retrieve the initial listener and its intermediate
     * adaptor from the provided configListener.
     *
     * @param <T>
     *            Type of the configuration.
     * @param configListener
     *            Listener from wich to extract the initial listener.
     * @return a pair of (intermediate adaptor, intermediate listener) or
     *         {@code Pair.EMPTY} if listener can't be extracted
     */
    // @Checkstyle:off
    static <T extends Configuration> Pair<ConfigAddListenerAdaptor<T>, ConfigurationAddListener<T>>
        extractInitialListener(ConfigAddListener configListener) {
        // @Checkstyle:on
        Pair<ConfigAddListenerAdaptor<T>, ServerManagedObjectAddListener<T>> pair =
                extractIntermediateListener(configListener);
        if (!pair.equals(Pair.EMPTY) && pair.getSecond() instanceof ServerManagedObjectAddListenerAdaptor) {
            ServerManagedObjectAddListenerAdaptor<T> adaptor2 = (ServerManagedObjectAddListenerAdaptor<T>)
                    pair.getSecond();
            return Pair.of(pair.getFirst(), adaptor2.getConfigurationAddListener());
        }
        return Pair.empty();
    }

    /**
     * Convenience method to retrieve the intermediate listener and its
     * intermediate adaptor from the provided configListener.
     *
     * @param <T>
     *            Type of the configuration.
     * @param configListener
     *            Listener from wich to extract the initial listener.
     * @return a pair of (intermediate adaptor, initial listener) or
     *         {@code Pair.EMPTY} if listener can't be extracted
     */
    @SuppressWarnings("unchecked")
    // @Checkstyle:off
    static <T extends Configuration> Pair<ConfigAddListenerAdaptor<T>, ServerManagedObjectAddListener<T>>
        extractIntermediateListener(ConfigAddListener configListener) {
        // @Checkstyle:on
        if (configListener instanceof ConfigAddListenerAdaptor) {
            ConfigAddListenerAdaptor<T> adaptor = (ConfigAddListenerAdaptor<T>) configListener;
            return Pair.of(adaptor, adaptor.getServerManagedObjectAddListener());
        }
        return Pair.empty();
    }

    /** Deregister a delete listener. */
    private <M extends Configuration> void deregisterDeleteListener(DN baseDN,
        ConfigurationDeleteListener<M> listener) {
        try {
            if (configRepository.hasEntry(baseDN)) {
                for (ConfigDeleteListener l : configRepository.getDeleteListeners(baseDN)) {
                    if (l instanceof ConfigDeleteListenerAdaptor) {
                        ConfigDeleteListenerAdaptor<?> adaptor = (ConfigDeleteListenerAdaptor<?>) l;
                        ServerManagedObjectDeleteListener<?> l2 = adaptor.getServerManagedObjectDeleteListener();
                        if (l2 instanceof ServerManagedObjectDeleteListenerAdaptor<?>) {
                            ServerManagedObjectDeleteListenerAdaptor<?> adaptor2 =
                                (ServerManagedObjectDeleteListenerAdaptor<?>) l2;
                            if (adaptor2.getConfigurationDeleteListener() == listener) {
                                configRepository.deregisterDeleteListener(baseDN, adaptor);
                            }
                        }
                    }
                }
            } else {
                // The relation entry does not exist so check for and deregister
                // delayed add listener.
                deregisterDelayedDeleteListener(baseDN, listener);
            }
        } catch (ConfigException e) {
            // Ignore the exception since this implies deregistration.
            logger.trace("Unable to deregister delete listener", e);
        }
    }

    /** Deregister a delete listener. */
    private <M extends Configuration> void deregisterDeleteListener(DN baseDN,
            ServerManagedObjectDeleteListener<M> listener) {
        try {
            if (configRepository.hasEntry(baseDN)) {
                for (ConfigDeleteListener l : configRepository.getDeleteListeners(baseDN)) {
                    if (l instanceof ConfigDeleteListenerAdaptor) {
                        ConfigDeleteListenerAdaptor<?> adaptor = (ConfigDeleteListenerAdaptor<?>) l;
                        if (adaptor.getServerManagedObjectDeleteListener() == listener) {
                            configRepository.deregisterDeleteListener(baseDN, adaptor);
                        }
                    }
                }
            } else {
                // The relation entry does not exist so check for and deregister
                // delayed add listener.
                deregisterDelayedDeleteListener(baseDN, listener);
            }
        } catch (ConfigException e) {
            // Ignore the exception since this implies deregistration.
            logger.trace("Unable to deregister delete listener", e);
        }
    }

    /** Register an instantiable or optional relation add listener. */
    private void registerAddListener(DN baseDN, ConfigAddListener adaptor) throws
        ConfigException {
        if (configRepository.hasEntry(baseDN)) {
            configRepository.registerAddListener(baseDN, adaptor);
        } else {
            // The relation entry does not exist yet
            // so register a delayed add listener.
            ConfigAddListener delayedListener = new DelayedConfigAddListener(baseDN, adaptor, configRepository);
            registerDelayedListener(baseDN, delayedListener);
        }
    }

    /** Register a delayed listener with the nearest existing parent entry to the provided base DN. */
    private void registerDelayedListener(DN baseDN, ConfigAddListener delayedListener) throws ConfigException {
        DN currentDN = baseDN.parent();
        DN previousDN = currentDN;
        while (currentDN != null) {
            if (!configRepository.hasEntry(currentDN)) {
                delayedListener = new DelayedConfigAddListener(currentDN, delayedListener, configRepository);
                previousDN = currentDN;
                currentDN = currentDN.parent();
            } else {
                configRepository.registerAddListener(previousDN, delayedListener);
                return;
            }
        }

        // No parent entry could be found.
        throw new ConfigException(ERR_ADMIN_UNABLE_TO_REGISTER_LISTENER.get(baseDN));
    }

    /**
     * Deregister a delayed listener with the nearest existing parent
     * entry to the provided base DN.
     */
    private <M extends Configuration> void deregisterDelayedAddListener(DN baseDN,
        ConfigurationAddListener<M> listener) throws ConfigException {
        DN parentDN = baseDN.parent();
        int delayWrappers = 0;
        while (parentDN != null) {
            if (!configRepository.hasEntry(parentDN)) {
                parentDN = parentDN.parent();
                delayWrappers++;
            } else {
                for (ConfigAddListener configListener : configRepository.getAddListeners(parentDN)) {
                    if (configListener instanceof DelayedConfigAddListener) {
                        DelayedConfigAddListener delayListener = (DelayedConfigAddListener) configListener;
                        ConfigAddListener wrappedListener;

                        int i = delayWrappers;
                        for (; i > 0; i--) {
                            wrappedListener = delayListener.getDelayedAddListener();
                            if (wrappedListener instanceof DelayedConfigAddListener) {
                                delayListener = (DelayedConfigAddListener) configListener;
                            } else {
                                break;
                            }
                        }

                        if (i > 0) {
                            // There are not enough level of wrapping
                            // so this can't be the listener we are looking for.
                            continue;
                        }

                        ConfigAddListener delayedListener = delayListener.getDelayedAddListener();

                        if (delayedListener instanceof ConfigAddListenerAdaptor) {
                            ConfigAddListenerAdaptor<?> adaptor = (ConfigAddListenerAdaptor<?>) delayedListener;
                            ServerManagedObjectAddListener<?> l2 = adaptor.getServerManagedObjectAddListener();
                            if (l2 instanceof ServerManagedObjectAddListenerAdaptor<?>) {
                                ServerManagedObjectAddListenerAdaptor<?> adaptor2 =
                                    (ServerManagedObjectAddListenerAdaptor<?>) l2;
                                if (adaptor2.getConfigurationAddListener() == listener) {
                                    configRepository.deregisterAddListener(parentDN, configListener);
                                }
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    /**
     * Deregister a delayed listener with the nearest existing parent
     * entry to the provided base DN.
     */
    private <M extends Configuration> void deregisterDelayedDeleteListener(DN baseDN,
            ConfigurationDeleteListener<M> listener) throws ConfigException {
        DN parentDN = baseDN.parent();
        int delayWrappers = 0;
        while (parentDN != null) {
            if (!configRepository.hasEntry(parentDN)) {
                parentDN = parentDN.parent();
                delayWrappers++;
            } else {
                for (ConfigAddListener l : configRepository.getAddListeners(parentDN)) {
                    if (l instanceof DelayedConfigAddListener) {
                        DelayedConfigAddListener delayListener = (DelayedConfigAddListener) l;
                        ConfigAddListener wrappedListener;

                        int i = delayWrappers;
                        for (; i > 0; i--) {
                            wrappedListener = delayListener.getDelayedAddListener();
                            if (wrappedListener instanceof DelayedConfigAddListener) {
                                delayListener = (DelayedConfigAddListener) l;
                            } else {
                                break;
                            }
                        }

                        if (i > 0) {
                            // There are not enough level of wrapping
                            // so this can't be the listener we are looking for.
                            continue;
                        }

                        ConfigDeleteListener delayedListener = delayListener.getDelayedDeleteListener();

                        if (delayedListener instanceof ConfigDeleteListenerAdaptor) {
                            ConfigDeleteListenerAdaptor<?> adaptor = (ConfigDeleteListenerAdaptor<?>) delayedListener;
                            ServerManagedObjectDeleteListener<?> l2 = adaptor.getServerManagedObjectDeleteListener();
                            if (l2 instanceof ServerManagedObjectDeleteListenerAdaptor<?>) {
                                ServerManagedObjectDeleteListenerAdaptor<?> adaptor2 =
                                    (ServerManagedObjectDeleteListenerAdaptor<?>) l2;
                                if (adaptor2.getConfigurationDeleteListener() == listener) {
                                    configRepository.deregisterAddListener(parentDN, l);
                                }
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    /**
     * Deregister a delayed listener with the nearest existing parent
     * entry to the provided base DN.
     */
    private <M extends Configuration> void deregisterDelayedAddListener(DN baseDN,
            ServerManagedObjectAddListener<M> listener) throws ConfigException {
        DN parentDN = baseDN.parent();
        int delayWrappers = 0;
        while (parentDN != null) {
            if (!configRepository.hasEntry(parentDN)) {
                parentDN = parentDN.parent();
                delayWrappers++;
            } else {
                for (ConfigAddListener configListener : configRepository.getAddListeners(parentDN)) {
                    if (configListener instanceof DelayedConfigAddListener) {
                        DelayedConfigAddListener delayListener = (DelayedConfigAddListener) configListener;
                        ConfigAddListener wrappedListener;

                        int i = delayWrappers;
                        for (; i > 0; i--) {
                            wrappedListener = delayListener.getDelayedAddListener();
                            if (wrappedListener instanceof DelayedConfigAddListener) {
                                delayListener = (DelayedConfigAddListener) configListener;
                            } else {
                                break;
                            }
                        }

                        if (i > 0) {
                            // There are not enough level of wrapping
                            // so this can't be the listener we are looking for.
                            continue;
                        }

                        ConfigAddListener delayedListener = delayListener.getDelayedAddListener();

                        if (delayedListener instanceof ConfigAddListenerAdaptor) {
                            ConfigAddListenerAdaptor<?> adaptor = (ConfigAddListenerAdaptor<?>) delayedListener;
                            if (adaptor.getServerManagedObjectAddListener() == listener) {
                                configRepository.deregisterAddListener(parentDN, configListener);
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    /**
     * Deregister a delayed listener with the nearest existing parent
     * entry to the provided base DN.
     */
    private <M extends Configuration> void deregisterDelayedDeleteListener(DN baseDN,
            ServerManagedObjectDeleteListener<M> listener) throws ConfigException {
        DN parentDN = baseDN.parent();
        int delayWrappers = 0;
        while (parentDN != null) {
            if (!configRepository.hasEntry(parentDN)) {
                parentDN = parentDN.parent();
                delayWrappers++;
            } else {
                for (ConfigAddListener configListener : configRepository.getAddListeners(parentDN)) {
                    if (configListener instanceof DelayedConfigAddListener) {
                        DelayedConfigAddListener delayListener = (DelayedConfigAddListener) configListener;
                        ConfigAddListener wrappedListener;

                        int i = delayWrappers;
                        for (; i > 0; i--) {
                            wrappedListener = delayListener.getDelayedAddListener();
                            if (wrappedListener instanceof DelayedConfigAddListener) {
                                delayListener = (DelayedConfigAddListener) configListener;
                            } else {
                                break;
                            }
                        }

                        if (i > 0) {
                            // There are not enough level of wrapping
                            // so this can't be the listener we are looking for.
                            continue;
                        }

                        ConfigDeleteListener delayedListener = delayListener.getDelayedDeleteListener();

                        if (delayedListener instanceof ConfigDeleteListenerAdaptor) {
                            ConfigDeleteListenerAdaptor<?> adaptor = (ConfigDeleteListenerAdaptor<?>) delayedListener;
                            if (adaptor.getServerManagedObjectDeleteListener() == listener) {
                                configRepository.deregisterAddListener(parentDN, configListener);
                            }
                        }
                    }
                }
                return;
            }
        }
    }

    /** Register an instantiable or optional relation delete listener. */
    private void registerDeleteListener(DN baseDN, ConfigDeleteListener adaptor) throws ConfigException {
        if (configRepository.hasEntry(baseDN)) {
            configRepository.registerDeleteListener(baseDN, adaptor);
        } else {
            // The relation entry does not exist yet
            // so register a delayed add listener.
            ConfigAddListener delayedListener = new DelayedConfigAddListener(baseDN, adaptor, configRepository);
            registerDelayedListener(baseDN, delayedListener);
        }
    }

    /** Validate that a relation definition belongs to this managed object. */
    private void validateRelationDefinition(RelationDefinition<?, ?> rd) {
        RelationDefinition<?, ?> tmp = definition.getRelationDefinition(rd.getName());
        if (tmp != rd) {
            throw new IllegalArgumentException("The relation " + rd.getName() + " is not associated with a "
                    + definition.getName());
        }
    }
}
