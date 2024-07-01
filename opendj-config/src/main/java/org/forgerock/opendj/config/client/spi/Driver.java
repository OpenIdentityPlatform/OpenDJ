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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.client.spi;

import static org.forgerock.opendj.config.PropertyException.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AbsoluteInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.AliasDefaultBehaviorProvider;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.DefaultBehaviorProviderVisitor;
import org.forgerock.opendj.config.DefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.DefinitionDecodingException.Reason;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.PropertyNotFoundException;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelativeInheritedDefaultBehaviorProvider;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.UndefinedDefaultBehaviorProvider;
import org.forgerock.opendj.config.client.ClientConstraintHandler;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.client.OperationRejectedException.OperationType;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.server.config.client.RootCfgClient;

/**
 * An abstract management connection context driver which should form the basis
 * of driver implementations.
 */
public abstract class Driver {

    /**
     * A default behavior visitor used for retrieving the default values of a
     * property.
     *
     * @param <T>
     *            The type of the property.
     */
    private final class DefaultValueFinder<T> implements DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

        /** Any exception that occurred whilst retrieving inherited default values. */
        private PropertyException exception;

        /** The path of the managed object containing the first property. */
        private final ManagedObjectPath<?, ?> firstPath;

        /** Indicates whether the managed object has been created yet. */
        private final boolean isCreate;

        /** The path of the managed object containing the next property. */
        private ManagedObjectPath<?, ?> nextPath;

        /** The next property whose default values were required. */
        private PropertyDefinition<T> nextProperty;

        /** Private constructor. */
        private DefaultValueFinder(ManagedObjectPath<?, ?> p, boolean isCreate) {
            this.firstPath = p;
            this.isCreate = isCreate;
        }

        @Override
        public Collection<T> visitAbsoluteInherited(AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
            try {
                return getInheritedProperty(d.getManagedObjectPath(), d.getManagedObjectDefinition(),
                    d.getPropertyName());
            } catch (PropertyException e) {
                exception = e;
                return Collections.emptySet();
            }
        }

        @Override
        public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
            return Collections.emptySet();
        }

        @Override
        public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> d, Void p) {
            Collection<String> stringValues = d.getDefaultValues();
            List<T> values = new ArrayList<>(stringValues.size());

            for (String stringValue : stringValues) {
                try {
                    values.add(nextProperty.decodeValue(stringValue));
                } catch (PropertyException e) {
                    exception = PropertyException.defaultBehaviorException(nextProperty, e);
                    break;
                }
            }

            return values;
        }

        @Override
        public Collection<T> visitRelativeInherited(RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
            try {
                return getInheritedProperty(d.getManagedObjectPath(nextPath), d.getManagedObjectDefinition(),
                    d.getPropertyName());
            } catch (PropertyException e) {
                exception = e;
                return Collections.emptySet();
            }
        }

        @Override
        public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d, Void p) {
            return Collections.emptySet();
        }

        /** Find the default values for the next path/property. */
        private Collection<T> find(ManagedObjectPath<?, ?> p, PropertyDefinition<T> pd) {
            this.nextPath = p;
            this.nextProperty = pd;

            Collection<T> values = nextProperty.getDefaultBehaviorProvider().accept(this, null);

            if (exception != null) {
                throw exception;
            }

            if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
                throw defaultBehaviorException(pd, propertyIsSingleValuedException(pd));
            }

            return values;
        }

        /** Get an inherited property value. */
        @SuppressWarnings("unchecked")
        private Collection<T> getInheritedProperty(ManagedObjectPath<?, ?> target,
                AbstractManagedObjectDefinition<?, ?> definition, String propertyName) {
            // First check that the requested type of managed object
            // corresponds to the path.
            AbstractManagedObjectDefinition<?, ?> actual = target.getManagedObjectDefinition();
            if (!definition.isParentOf(actual)) {
                throw PropertyException.defaultBehaviorException(nextProperty,
                        new DefinitionDecodingException(actual, Reason.WRONG_TYPE_INFORMATION));
            }

            // Save the current property in case of recursion.
            PropertyDefinition<T> pd1 = nextProperty;

            try {
                // Determine the requested property definition.
                PropertyDefinition<T> pd2;
                try {
                    // FIXME: we use the definition taken from the default
                    // behavior here when we should really use the exact
                    // definition of the component being created.
                    PropertyDefinition<?> pdTmp = definition.getPropertyDefinition(propertyName);
                    pd2 = pd1.getClass().cast(pdTmp);
                } catch (IllegalArgumentException | ClassCastException e) {
                    throw new PropertyNotFoundException(propertyName);
                }

                // If the path relates to the current managed object and the
                // managed object is in the process of being created it won't
                // exist, so we should just use the default values of the
                // referenced property.
                if (isCreate && firstPath.equals(target)) {
                    // Recursively retrieve this property's default values.
                    Collection<T> tmp = find(target, pd2);
                    Collection<T> values = new ArrayList<>(tmp.size());
                    for (T value : tmp) {
                        pd1.validateValue(value);
                        values.add(value);
                    }
                    return values;
                } else {
                    // FIXME: issue 2481 - this is broken if the referenced property
                    // inherits its defaults from the newly created managed object.
                    return getPropertyValues(target, pd2);
                }
            } catch (PropertyException | DefinitionDecodingException | PropertyNotFoundException
                    | LdapException | ManagedObjectNotFoundException e) {
                throw PropertyException.defaultBehaviorException(pd1, e);
            }
        }
    }

    /** Creates a new abstract driver. */
    protected Driver() {
       // Do nothing.
    }

    /** Closes any context associated with this management context driver. */
    public void close() {
        // do nothing by default
    }

    /**
     * Deletes the named instantiable child managed object from the named parent
     * managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @return Returns <code>true</code> if the named instantiable child managed
     *         object was found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
        ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException, LdapException {
        validateRelationDefinition(parent, rd);
        ManagedObjectPath<?, ?> child = parent.child(rd, name);
        return doDeleteManagedObject(child);
    }

    /**
     * Deletes the optional child managed object from the named parent managed
     * object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The optional relation definition.
     * @return Returns <code>true</code> if the optional child managed object
     *         was found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
        ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd) throws ManagedObjectNotFoundException,
        OperationRejectedException, LdapException {
        validateRelationDefinition(parent, rd);
        ManagedObjectPath<?, ?> child = parent.child(rd);
        return doDeleteManagedObject(child);
    }

    /**
     * Deletes the named instantiable child managed object from the named parent
     * managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @return Returns <code>true</code> if the named instantiable child managed
     *         object was found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    public final <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
        ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException, LdapException {
        validateRelationDefinition(parent, rd);
        ManagedObjectPath<?, ?> child = parent.child(rd, name);
        return doDeleteManagedObject(child);
    }

    /**
     * Gets the named managed object. The path is guaranteed to be non-empty, so
     * implementations do not need to worry about handling this special case.
     *
     * @param <C>
     *            The type of client managed object configuration that the path
     *            definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the path
     *            definition refers to.
     * @param path
     *            The non-empty path of the managed object.
     * @return Returns the named managed object.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws LdapException
     *             If any other error occurs.
     */
    // @Checkstyle:ignore
    public abstract <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getManagedObject(
        ManagedObjectPath<C, S> path) throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, LdapException;

    /**
     * Gets the effective values of a property in the named managed object.
     * <p>
     * Implementations MUST NOT not use
     * {@link #getManagedObject(ManagedObjectPath)} to read the referenced
     * managed object in its entirety. Specifically, implementations MUST only
     * attempt to resolve the default values for the requested property and its
     * dependencies (if it uses inherited defaults). This is to avoid infinite
     * recursion where a managed object contains a property which inherits
     * default values from another property in the same managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the path
     *            definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the path
     *            definition refers to.
     * @param <P>
     *            The type of the property to be retrieved.
     * @param path
     *            The path of the managed object containing the property.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's effective values, or an empty set if there
     *         are no values defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with the
     *             referenced managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws PropertyException
     *             If the managed object was found but the requested property
     *             could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws LdapException
     *             If any other error occurs.
     */
    public abstract <C extends ConfigurationClient, S extends Configuration, P> SortedSet<P> getPropertyValues(
        ManagedObjectPath<C, S> path, PropertyDefinition<P> pd) throws DefinitionDecodingException,
        ManagedObjectNotFoundException, LdapException;

    /**
     * Gets the root configuration managed object associated with this
     * management context driver.
     *
     * @return Returns the root configuration managed object associated with
     *         this management context driver.
     */
    public abstract ManagedObject<RootCfgClient> getRootConfigurationManagedObject();

    /**
     * Lists the child managed objects of the named parent managed object which
     * are a sub-type of the specified managed object definition.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @param d
     *            The managed object definition.
     * @return Returns the names of the child managed objects which are a
     *         sub-type of the specified managed object definition.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    public abstract <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
        ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
        AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws ManagedObjectNotFoundException,
        LdapException;

    /**
     * Lists the child managed objects of the named parent managed object which
     * are a sub-type of the specified managed object definition.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The set relation definition.
     * @param d
     *            The managed object definition.
     * @return Returns the names of the child managed objects which are a
     *         sub-type of the specified managed object definition.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    public abstract <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
        ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd,
        AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws ManagedObjectNotFoundException,
        LdapException;

    /**
     * Determines whether the named managed object exists.
     * <p>
     * Implementations should always return <code>true</code> when the provided
     * path is empty.
     *
     * @param path
     *            The path of the named managed object.
     * @return Returns <code>true</code> if the named managed object exists,
     *         <code>false</code> otherwise.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    public abstract boolean managedObjectExists(ManagedObjectPath<?, ?> path) throws ManagedObjectNotFoundException,
        LdapException;

    /**
     * Deletes the named managed object.
     * <p>
     * Implementations do not need check whether the named managed object
     * exists, nor do they need to enforce client constraints.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param path
     *            The path of the managed object to be deleted.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             server-side constraint which cannot be satisfied (for
     *             example, if it is referenced by another managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    protected abstract <C extends ConfigurationClient, S extends Configuration> void deleteManagedObject(
        ManagedObjectPath<C, S> path) throws OperationRejectedException, LdapException;

    /**
     * Gets the default values for the specified property.
     *
     * @param <P>
     *            The type of the property.
     * @param p
     *            The managed object path of the current managed object.
     * @param pd
     *            The property definition.
     * @param isCreate
     *            Indicates whether the managed object has been created yet.
     * @return Returns the default values for the specified property.
     * @throws PropertyException
     *             If the default values could not be retrieved or decoded
     *             properly.
     */
    protected final <P> Collection<P> findDefaultValues(ManagedObjectPath<?, ?> p, PropertyDefinition<P> pd,
        boolean isCreate) {
        DefaultValueFinder<P> v = new DefaultValueFinder<>(p, isCreate);
        return v.find(p, pd);
    }

    /**
     * Gets the management context associated with this driver.
     *
     * @return Returns the management context associated with this driver.
     */
    protected abstract ManagementContext getManagementContext();

    /**
     * Validate that a relation definition belongs to the managed object
     * referenced by the provided path.
     *
     * @param path
     *            The parent managed object path.
     * @param rd
     *            The relation definition.
     * @throws IllegalArgumentException
     *             If the relation definition does not belong to the managed
     *             object definition.
     */
    protected final void validateRelationDefinition(ManagedObjectPath<?, ?> path, RelationDefinition<?, ?> rd) {
        AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
        RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
        if (tmp != rd) {
            throw new IllegalArgumentException("The relation " + rd.getName() + " is not associated with a "
                + d.getName());
        }
    }

    /**
     * Remove a managed object, first ensuring that the parent exists,
     * then ensuring that the child exists, before ensuring that any
     * constraints are satisfied.
     */
    private <C extends ConfigurationClient, S extends Configuration> boolean doDeleteManagedObject(
        ManagedObjectPath<C, S> path) throws ManagedObjectNotFoundException, OperationRejectedException,
        LdapException {
        // First make sure that the parent exists.
        if (!managedObjectExists(path.parent())) {
            throw new ManagedObjectNotFoundException();
        }

        // Make sure that the targeted managed object exists.
        if (!managedObjectExists(path)) {
            return false;
        }

        // The targeted managed object is guaranteed to exist, so enforce
        // any constraints.
        AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
        List<LocalizableMessage> messages = new LinkedList<>();
        if (!isAcceptable(path, d, messages)) {
            throw new OperationRejectedException(OperationType.DELETE, d.getUserFriendlyName(), messages);
        }

        deleteManagedObject(path);
        return true;
    }

    private <C extends ConfigurationClient, S extends Configuration>
    boolean isAcceptable(ManagedObjectPath<C, S> path, AbstractManagedObjectDefinition<?, ?> d,
            List<LocalizableMessage> messages) throws LdapException {
        for (Constraint constraint : d.getAllConstraints()) {
            for (ClientConstraintHandler handler : constraint.getClientConstraintHandlers()) {
                ManagementContext context = getManagementContext();
                if (!handler.isDeleteAcceptable(context, path, messages)) {
                    return false;
                }
            }
        }
        return true;
    }
}
