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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.Constraint;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.DefaultManagedObject;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectAlreadyExistsException;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.RelationDefinitionVisitor;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.config.DefinitionDecodingException.Reason;
import org.forgerock.opendj.config.client.ClientConstraintHandler;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.IllegalManagedObjectNameException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.MissingMandatoryPropertiesException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.client.OperationRejectedException.OperationType;
import org.forgerock.opendj.ldap.LdapException;

/**
 * An abstract managed object implementation.
 *
 * @param <T>
 *            The type of client configuration represented by the client managed
 *            object.
 */
public abstract class AbstractManagedObject<T extends ConfigurationClient> implements ManagedObject<T> {

    /** Creates any default managed objects associated with a relation definition. */
    private final class DefaultManagedObjectFactory implements RelationDefinitionVisitor<Void, Void> {

        /** Possible exceptions. */
        private ManagedObjectAlreadyExistsException moaee;
        private MissingMandatoryPropertiesException mmpe;
        private ConcurrentModificationException cme;
        private OperationRejectedException ore;
        private LdapException ere;

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitInstantiable(
            InstantiableRelationDefinition<C, S> rd, Void p) {
            for (String name : rd.getDefaultManagedObjectNames()) {
                DefaultManagedObject<? extends C, ? extends S> dmo = rd.getDefaultManagedObject(name);
                ManagedObjectDefinition<? extends C, ? extends S> d = dmo.getManagedObjectDefinition();
                ManagedObject<? extends C> child;
                try {
                    child = createChild(rd, d, name, null);
                } catch (IllegalManagedObjectNameException e) {
                    // This should not happen.
                    throw new RuntimeException(e);
                }
                createDefaultManagedObject(d, child, dmo);
            }
            return null;
        }

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitOptional(
            OptionalRelationDefinition<C, S> rd, Void p) {
            if (rd.getDefaultManagedObject() != null) {
                DefaultManagedObject<? extends C, ? extends S> dmo = rd.getDefaultManagedObject();
                ManagedObjectDefinition<? extends C, ? extends S> d = dmo.getManagedObjectDefinition();
                ManagedObject<? extends C> child = createChild(rd, d, null);
                createDefaultManagedObject(d, child, dmo);
            }
            return null;
        }

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitSingleton(
            SingletonRelationDefinition<C, S> rd, Void p) {
            // Do nothing - not possible to create singletons
            // dynamically.
            return null;
        }

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Void visitSet(SetRelationDefinition<C, S> rd,
            Void p) {
            for (String name : rd.getDefaultManagedObjectNames()) {
                DefaultManagedObject<? extends C, ? extends S> dmo = rd.getDefaultManagedObject(name);
                ManagedObjectDefinition<? extends C, ? extends S> d = dmo.getManagedObjectDefinition();
                ManagedObject<? extends C> child = createChild(rd, d, null);
                createDefaultManagedObject(d, child, dmo);
            }
            return null;
        }

        /** Create the child managed object. */
        private void createDefaultManagedObject(ManagedObjectDefinition<?, ?> d, ManagedObject<?> child,
            DefaultManagedObject<?, ?> dmo) {
            for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
                setPropertyValues(child, pd, dmo);
            }

            try {
                child.commit();
            } catch (ManagedObjectAlreadyExistsException e) {
                moaee = e;
            } catch (MissingMandatoryPropertiesException e) {
                mmpe = e;
            } catch (ConcurrentModificationException e) {
                cme = e;
            } catch (OperationRejectedException e) {
                ore = e;
            } catch (LdapException e) {
                ere = e;
            }
        }

        /**
         * Creates the default managed objects associated with the provided
         * relation definition.
         *
         * @param rd
         *            The relation definition.
         */
        private void createDefaultManagedObjects(RelationDefinition<?, ?> rd) throws LdapException,
            ConcurrentModificationException, MissingMandatoryPropertiesException,
            ManagedObjectAlreadyExistsException, OperationRejectedException {
            rd.accept(this, null);

            if (ere != null) {
                throw ere;
            } else if (cme != null) {
                throw cme;
            } else if (mmpe != null) {
                throw mmpe;
            } else if (moaee != null) {
                throw moaee;
            } else if (ore != null) {
                throw ore;
            }
        }

        /** Set property values. */
        private <P> void setPropertyValues(ManagedObject<?> mo, PropertyDefinition<P> pd,
            DefaultManagedObject<?, ?> dmo) {
            mo.setPropertyValues(pd, dmo.getPropertyValues(pd));
        }
    }

    /** The managed object definition associated with this managed object. */
    private final ManagedObjectDefinition<T, ? extends Configuration> definition;

    /**
     * Indicates whether this managed object exists on the server
     * (false means the managed object is new and has not been committed).
     */
    private boolean existsOnServer;

    /** Optional naming property definition. */
    private final PropertyDefinition<?> namingPropertyDefinition;

    /** The path associated with this managed object. */
    private ManagedObjectPath<T, ? extends Configuration> path;

    /** The managed object's properties. */
    private final PropertySet properties;

    /**
     * Creates a new abstract managed object.
     *
     * @param d
     *            The managed object's definition.
     * @param path
     *            The managed object's path.
     * @param properties
     *            The managed object's properties.
     * @param existsOnServer
     *            Indicates whether the managed object exists on the server
     *            (false means the managed object is new and has not been committed).
     * @param namingPropertyDefinition
     *            Optional naming property definition.
     */
    protected AbstractManagedObject(ManagedObjectDefinition<T, ? extends Configuration> d,
        ManagedObjectPath<T, ? extends Configuration> path, PropertySet properties, boolean existsOnServer,
        PropertyDefinition<?> namingPropertyDefinition) {
        this.definition = d;
        this.path = path;
        this.properties = properties;
        this.existsOnServer = existsOnServer;
        this.namingPropertyDefinition = namingPropertyDefinition;
    }

    @Override
    public final void commit() throws ManagedObjectAlreadyExistsException, MissingMandatoryPropertiesException,
        ConcurrentModificationException, OperationRejectedException, LdapException {
        // First make sure all mandatory properties are defined.
        List<PropertyException> exceptions = new LinkedList<>();

        for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
            Property<?> p = getProperty(pd);
            if (pd.hasOption(PropertyOption.MANDATORY) && p.getEffectiveValues().isEmpty()) {
                exceptions.add(PropertyException.propertyIsMandatoryException(pd));
            }
        }

        if (!exceptions.isEmpty()) {
            throw new MissingMandatoryPropertiesException(definition.getUserFriendlyName(), exceptions,
                !existsOnServer);
        }

        // Now enforce any constraints.
        List<LocalizableMessage> messages = new LinkedList<>();
        boolean isAcceptable = true;
        ManagementContext context = getDriver().getManagementContext();

        for (Constraint constraint : definition.getAllConstraints()) {
            for (ClientConstraintHandler handler : constraint.getClientConstraintHandlers()) {
                if (existsOnServer) {
                    if (!handler.isModifyAcceptable(context, this, messages)) {
                        isAcceptable = false;
                    }
                } else {
                    if (!handler.isAddAcceptable(context, this, messages)) {
                        isAcceptable = false;
                    }
                }
            }
            if (!isAcceptable) {
                break;
            }
        }

        if (!isAcceptable) {
            if (existsOnServer) {
                throw new OperationRejectedException(OperationType.MODIFY, definition.getUserFriendlyName(), messages);
            } else {
                throw new OperationRejectedException(OperationType.CREATE, definition.getUserFriendlyName(), messages);
            }
        }

        // Commit the managed object.
        if (existsOnServer) {
            modifyExistingManagedObject();
        } else {
            addNewManagedObject();
        }

        // Make all pending property values active.
        properties.commit();

        // If the managed object was created make sure that any default
        // subordinate managed objects are also created.
        if (!existsOnServer) {
            DefaultManagedObjectFactory factory = new DefaultManagedObjectFactory();
            for (RelationDefinition<?, ?> rd : definition.getAllRelationDefinitions()) {
                factory.createDefaultManagedObjects(rd);
            }

            existsOnServer = true;
        }
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
        InstantiableRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d, String name,
        Collection<PropertyException> exceptions) throws IllegalManagedObjectNameException {
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
            } catch (PropertyException e) {
                throw new IllegalManagedObjectNameException(name, pd);
            }
        }

        ManagedObjectPath<C1, ? extends S> childPath = path.child(r, d, name);
        return createNewManagedObject(d, childPath, pd, name, exceptions);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
        OptionalRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d,
        Collection<PropertyException> exceptions) {
        validateRelationDefinition(r);
        ManagedObjectPath<C1, ? extends S> childPath = path.child(r, d);
        return createNewManagedObject(d, childPath, null, null, exceptions);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
        SetRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d,
        Collection<PropertyException> exceptions) {
        validateRelationDefinition(r);

        ManagedObjectPath<C1, ? extends S> childPath = path.child(r, d);
        return createNewManagedObject(d, childPath, null, null, exceptions);
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
        InstantiableRelationDefinition<C, S> r, String name) throws DefinitionDecodingException,
        ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
        LdapException {
        validateRelationDefinition(r);
        ensureThisManagedObjectExists();
        Driver ctx = getDriver();
        return ctx.getManagedObject(path.child(r, name));
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
        OptionalRelationDefinition<C, S> r) throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        ensureThisManagedObjectExists();
        Driver ctx = getDriver();
        return ctx.getManagedObject(path.child(r));
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
        SingletonRelationDefinition<C, S> r) throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        ensureThisManagedObjectExists();
        Driver ctx = getDriver();
        return ctx.getManagedObject(path.child(r));
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
        SetRelationDefinition<C, S> r, String name) throws DefinitionDecodingException,
        ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
        LdapException {
        validateRelationDefinition(r);
        ensureThisManagedObjectExists();
        Driver ctx = getDriver();

        AbstractManagedObjectDefinition<C, S> d = r.getChildDefinition();
        AbstractManagedObjectDefinition<? extends C, ? extends S> cd;

        try {
            cd = d.getChild(name);
        } catch (IllegalArgumentException e) {
            // Unrecognized definition name - report this as a decoding
            // exception.
            throw new DefinitionDecodingException(d, Reason.WRONG_TYPE_INFORMATION);
        }

        return ctx.getManagedObject(path.child(r, cd));
    }

    @Override
    public final T getConfiguration() {
        return definition.createClientConfiguration(this);
    }

    @Override
    public final ManagedObjectDefinition<T, ? extends Configuration> getManagedObjectDefinition() {
        return definition;
    }

    @Override
    public final ManagedObjectPath<T, ? extends Configuration> getManagedObjectPath() {
        return path;
    }

    @Override
    public final <P> SortedSet<P> getPropertyDefaultValues(PropertyDefinition<P> pd) {
        return new TreeSet<>(getProperty(pd).getDefaultValues());
    }

    @Override
    public final <P> P getPropertyValue(PropertyDefinition<P> pd) {
        Set<P> values = getProperty(pd).getEffectiveValues();
        if (!values.isEmpty()) {
            return values.iterator().next();
        }
        return null;
    }

    @Override
    public final <P> SortedSet<P> getPropertyValues(PropertyDefinition<P> pd) {
        return new TreeSet<>(getProperty(pd).getEffectiveValues());
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> boolean hasChild(
        OptionalRelationDefinition<C, S> r) throws ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        try {
            return ctx.managedObjectExists(path.child(r));
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public final boolean isPropertyPresent(PropertyDefinition<?> pd) {
        return !getProperty(pd).isEmpty();
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
        InstantiableRelationDefinition<C, S> r) throws ConcurrentModificationException, LdapException {
        return listChildren(r, r.getChildDefinition());
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
        InstantiableRelationDefinition<C, S> r, AbstractManagedObjectDefinition<? extends C, ? extends S> d)
            throws ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        try {
            return ctx.listManagedObjects(path, r, d);
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
        SetRelationDefinition<C, S> r) throws ConcurrentModificationException, LdapException {
        return listChildren(r, r.getChildDefinition());
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
        SetRelationDefinition<C, S> r, AbstractManagedObjectDefinition<? extends C, ? extends S> d)
            throws ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        try {
            return ctx.listManagedObjects(path, r, d);
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> void removeChild(
        InstantiableRelationDefinition<C, S> r, String name) throws ManagedObjectNotFoundException,
        OperationRejectedException, ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        boolean found;

        try {
            found = ctx.deleteManagedObject(path, r, name);
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }

        if (!found) {
            throw new ManagedObjectNotFoundException();
        }
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> void removeChild(
        OptionalRelationDefinition<C, S> r) throws ManagedObjectNotFoundException, OperationRejectedException,
        ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        boolean found;

        try {
            found = ctx.deleteManagedObject(path, r);
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }

        if (!found) {
            throw new ManagedObjectNotFoundException();
        }
    }

    @Override
    public final <C extends ConfigurationClient, S extends Configuration> void removeChild(
        SetRelationDefinition<C, S> r, String name) throws ManagedObjectNotFoundException,
        OperationRejectedException, ConcurrentModificationException, LdapException {
        validateRelationDefinition(r);
        Driver ctx = getDriver();
        boolean found;

        try {
            found = ctx.deleteManagedObject(path, r, name);
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }

        if (!found) {
            throw new ManagedObjectNotFoundException();
        }
    }

    @Override
    public final <P> void setPropertyValue(PropertyDefinition<P> pd, P value) {
        if (value != null) {
            setPropertyValues(pd, Collections.singleton(value));
        } else {
            setPropertyValues(pd, Collections.<P> emptySet());
        }
    }

    @Override
    public final <P> void setPropertyValues(PropertyDefinition<P> pd, Collection<P> values) {
        if (pd.hasOption(PropertyOption.MONITORING)) {
            throw PropertyException.propertyIsReadOnlyException(pd);
        }

        if (existsOnServer && pd.hasOption(PropertyOption.READ_ONLY)) {
            throw PropertyException.propertyIsReadOnlyException(pd);
        }

        properties.setPropertyValues(pd, values);

        // If this is a naming property then update the name.
        if (pd.equals(namingPropertyDefinition)) {
            // The property must be single-valued and mandatory.
            String newName = pd.encodeValue(values.iterator().next());
            path = path.rename(newName);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("{ TYPE=");
        builder.append(definition.getName());
        builder.append(", PATH=\"");
        builder.append(path);
        builder.append('\"');
        for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
            builder.append(", ");
            builder.append(pd.getName());
            builder.append('=');
            builder.append(getPropertyValues(pd));
        }
        builder.append(" }");

        return builder.toString();
    }

    /**
     * Adds this new managed object.
     *
     * @throws ManagedObjectAlreadyExistsException
     *             If the managed object cannot be added to the server because
     *             it already exists.
     * @throws ConcurrentModificationException
     *             If the managed object's parent has been removed by another
     *             client.
     * @throws OperationRejectedException
     *             If the managed object cannot be added due to some client-side
     *             or server-side constraint which cannot be satisfied.
     * @throws LdapException
     *             If any other error occurs.
     */
    protected abstract void addNewManagedObject() throws LdapException, OperationRejectedException,
        ConcurrentModificationException, ManagedObjectAlreadyExistsException;

    /**
     * Gets the management context driver associated with this managed object.
     *
     * @return Returns the management context driver associated with this
     *         managed object.
     */
    protected abstract Driver getDriver();

    /**
     * Gets the naming property definition associated with this managed object.
     *
     * @return Returns the naming property definition associated with this
     *         managed object, or <code>null</code> if this managed object does
     *         not have a naming property.
     */
    protected final PropertyDefinition<?> getNamingPropertyDefinition() {
        return namingPropertyDefinition;
    }

    /**
     * Gets the property associated with the specified property definition.
     *
     * @param <P>
     *            The underlying type of the property.
     * @param pd
     *            The Property definition.
     * @return Returns the property associated with the specified property
     *         definition.
     * @throws IllegalArgumentException
     *             If this property provider does not recognize the requested
     *             property definition.
     */
    protected final <P> Property<P> getProperty(PropertyDefinition<P> pd) {
        return properties.getProperty(pd);
    }

    /**
     * Applies changes made to this managed object.
     *
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws OperationRejectedException
     *             If the managed object cannot be added due to some client-side
     *             or server-side constraint which cannot be satisfied.
     * @throws LdapException
     *             If any other error occurs.
     */
    protected abstract void modifyExistingManagedObject() throws ConcurrentModificationException,
        OperationRejectedException, LdapException;

    /**
     * Creates a new managed object.
     *
     * @param <M>
     *            The type of client configuration represented by the client
     *            managed object.
     * @param d
     *            The managed object's definition.
     * @param path
     *            The managed object's path.
     * @param properties
     *            The managed object's properties.
     * @param existsOnServer
     *            Indicates whether the managed object exists on the server
     *            (false means the managed object is new and has not been committed).
     * @param namingPropertyDefinition
     *            Optional naming property definition.
     * @return Returns the new managed object.
     */
    protected abstract <M extends ConfigurationClient> ManagedObject<M> newInstance(ManagedObjectDefinition<M, ?> d,
        ManagedObjectPath<M, ?> path, PropertySet properties, boolean existsOnServer,
        PropertyDefinition<?> namingPropertyDefinition);

    /** Creates a new managed object with no active values, just default values. */
    private <M extends ConfigurationClient, P> ManagedObject<M> createNewManagedObject(
        ManagedObjectDefinition<M, ?> d, ManagedObjectPath<M, ?> p, PropertyDefinition<P> namingPropertyDefinition,
        String name, Collection<PropertyException> exceptions) {
        PropertySet childProperties = new PropertySet();
        for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
            try {
                createProperty(childProperties, p, pd);
            } catch (PropertyException e) {
                // Add the exception if requested.
                if (exceptions != null) {
                    exceptions.add(e);
                }
            }
        }

        // Set the naming property if there is one.
        if (namingPropertyDefinition != null) {
            P value = namingPropertyDefinition.decodeValue(name);
            childProperties.setPropertyValues(namingPropertyDefinition, Collections.singleton(value));
        }

        return newInstance(d, p, childProperties, false, namingPropertyDefinition);
    }

    /** Create an empty property. */
    private <P> void createProperty(PropertySet properties, ManagedObjectPath<?, ?> p, PropertyDefinition<P> pd) {
        try {
            Driver context = getDriver();
            Collection<P> defaultValues = context.findDefaultValues(p, pd, true);
            properties.addProperty(pd, defaultValues, Collections.<P> emptySet());
        } catch (PropertyException e) {
            // Make sure that we have still created the property.
            properties.addProperty(pd, Collections.<P> emptySet(), Collections.<P> emptySet());
            throw e;
        }
    }

    /** Makes sure that this managed object exists. */
    private void ensureThisManagedObjectExists() throws ConcurrentModificationException, LdapException {
        if (!path.isEmpty()) {
            Driver ctx = getDriver();

            try {
                if (!ctx.managedObjectExists(path)) {
                    throw new ConcurrentModificationException();
                }
            } catch (ManagedObjectNotFoundException e) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /** Validate that a relation definition belongs to this managed object. */
    private void validateRelationDefinition(RelationDefinition<?, ?> rd) {
        ManagedObjectDefinition<T, ?> d = getManagedObjectDefinition();
        RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
        if (tmp != rd) {
            throw new IllegalArgumentException("The relation " + rd.getName() + " is not associated with a "
                + d.getName());
        }
    }

}
