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
 * Copyright 2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

import static com.forgerock.opendj.ldap.config.ConfigMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import org.forgerock.util.Reject;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.forgerock.opendj.config.client.ClientConstraintHandler;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.conditions.Condition;
import org.forgerock.opendj.config.conditions.Conditions;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.config.server.ServerConstraintHandler;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.config.server.ServerManagedObjectChangeListener;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LdapException;

/**
 * Aggregation property definition.
 * <p>
 * An aggregation property names one or more managed objects which are required
 * by the managed object associated with this property. An aggregation property
 * definition takes care to perform referential integrity checks: referenced
 * managed objects cannot be deleted. Nor can an aggregation reference
 * non-existent managed objects. Referential integrity checks are <b>not</b>
 * performed during value validation. Instead they are performed when changes to
 * the managed object are committed.
 * <p>
 * An aggregation property definition can optionally identify two properties:
 * <ul>
 * <li>an <code>enabled</code> property in the aggregated managed object - the
 * property must be a {@link BooleanPropertyDefinition} and indicate whether the
 * aggregated managed object is enabled or not. If specified, the administration
 * framework will prevent the aggregated managed object from being disabled
 * while it is referenced
 * <li>an <code>enabled</code> property in this property's managed object - the
 * property must be a {@link BooleanPropertyDefinition} and indicate whether
 * this property's managed object is enabled or not. If specified, and as long
 * as there is an equivalent <code>enabled</code> property defined for the
 * aggregated managed object, the <code>enabled</code> property in the
 * aggregated managed object will only be checked when this property is true.
 * </ul>
 * In other words, these properties can be used to make sure that referenced
 * managed objects are not disabled while they are referenced.
 *
 * @param <C>
 *            The type of client managed object configuration that this
 *            aggregation property definition refers to.
 * @param <S>
 *            The type of server managed object configuration that this
 *            aggregation property definition refers to.
 */
public final class AggregationPropertyDefinition<C extends ConfigurationClient, S extends Configuration> extends
    PropertyDefinition<String> {

    /**
     * An interface for incrementally constructing aggregation property
     * definitions.
     *
     * @param <C>
     *            The type of client managed object configuration that this
     *            aggregation property definition refers to.
     * @param <S>
     *            The type of server managed object configuration that this
     *            aggregation property definition refers to.
     */
    public static final class Builder<C extends ConfigurationClient, S extends Configuration> extends
        AbstractBuilder<String, AggregationPropertyDefinition<C, S>> {

        /**
         * The string representation of the managed object path specifying
         * the parent of the aggregated managed objects.
         */
        private String parentPathString;

        /**
         * The name of a relation in the parent managed object which
         * contains the aggregated managed objects.
         */
        private String rdName;

        /** The condition which is used to determine if a referenced managed object is enabled. */
        private Condition targetIsEnabledCondition = Conditions.TRUE;

        /**
         * The condition which is used to determine whether
         * referenced managed objects need to be enabled.
         */
        private Condition targetNeedsEnablingCondition = Conditions.TRUE;

        /** Private constructor. */
        private Builder(AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
            super(d, propertyName);
        }

        /**
         * Sets the name of the managed object which is the parent of the
         * aggregated managed objects.
         * <p>
         * This must be defined before the property definition can be built.
         *
         * @param pathString
         *            The string representation of the managed object path
         *            specifying the parent of the aggregated managed objects.
         */
        public final void setParentPath(String pathString) {
            this.parentPathString = pathString;
        }

        /**
         * Sets the relation in the parent managed object which contains the
         * aggregated managed objects.
         * <p>
         * This must be defined before the property definition can be built.
         *
         * @param rdName
         *            The name of a relation in the parent managed object which
         *            contains the aggregated managed objects.
         */
        public final void setRelationDefinition(String rdName) {
            this.rdName = rdName;
        }

        /**
         * Sets the condition which is used to determine if a referenced managed
         * object is enabled. By default referenced managed objects are assumed
         * to always be enabled.
         *
         * @param condition
         *            The condition which is used to determine if a referenced
         *            managed object is enabled.
         */
        public final void setTargetIsEnabledCondition(Condition condition) {
            this.targetIsEnabledCondition = condition;
        }

        /**
         * Sets the condition which is used to determine whether
         * referenced managed objects need to be enabled. By default referenced
         * managed objects must always be enabled.
         *
         * @param condition
         *            The condition which is used to determine whether
         *            referenced managed objects need to be enabled.
         */
        public final void setTargetNeedsEnablingCondition(Condition condition) {
            this.targetNeedsEnablingCondition = condition;
        }

        @Override
        protected AggregationPropertyDefinition<C, S> buildInstance(AbstractManagedObjectDefinition<?, ?> d,
            String propertyName, EnumSet<PropertyOption> options, AdministratorAction adminAction,
            DefaultBehaviorProvider<String> defaultBehavior) {
            // Make sure that the parent path has been defined.
            if (parentPathString == null) {
                throw new IllegalStateException("Parent path undefined");
            }

            // Make sure that the relation definition has been defined.
            if (rdName == null) {
                throw new IllegalStateException("Relation definition undefined");
            }

            return new AggregationPropertyDefinition<>(d, propertyName, options, adminAction, defaultBehavior,
                parentPathString, rdName, targetNeedsEnablingCondition, targetIsEnabledCondition);
        }

    }

    /** A change listener which prevents the named component from being disabled. */
    private final class ReferentialIntegrityChangeListener implements ServerManagedObjectChangeListener<S> {

        /**
         * The error message which should be returned if an attempt is
         * made to disable the referenced component.
         */
        private final LocalizableMessage message;

        /** The path of the referenced component. */
        private final ManagedObjectPath<C, S> path;

        /** Creates a new referential integrity delete listener. */
        private ReferentialIntegrityChangeListener(ManagedObjectPath<C, S> path, LocalizableMessage message) {
            this.path = path;
            this.message = message;
        }

        @Override
        public ConfigChangeResult applyConfigurationChange(ServerManagedObject<? extends S> mo) {
            try {
                if (targetIsEnabledCondition.evaluate(mo)) {
                    return new ConfigChangeResult();
                }
            } catch (ConfigException e) {
                // This should not happen - ignore it and throw an exception
                // anyway below.
            }

            // This should not happen - the previous call-back should have
            // trapped this.
            throw new IllegalStateException("Attempting to disable a referenced "
                + relationDefinition.getChildDefinition().getUserFriendlyName());
        }

        @Override
        public boolean isConfigurationChangeAcceptable(ServerManagedObject<? extends S> mo,
            List<LocalizableMessage> unacceptableReasons) {
            // Always prevent the referenced component from being
            // disabled.
            try {
                if (!targetIsEnabledCondition.evaluate(mo)) {
                    unacceptableReasons.add(message);
                    return false;
                } else {
                    return true;
                }
            } catch (ConfigException e) {
                // The condition could not be evaluated.
                debugLogger.trace("Unable to perform post add", e);
                LocalizableMessage message =
                    ERR_REFINT_UNABLE_TO_EVALUATE_TARGET_CONDITION.get(mo.getManagedObjectDefinition()
                        .getUserFriendlyName(), mo.getDN(), getExceptionMessage(e));
                LocalizedLogger logger =
                    LocalizedLogger.getLocalizedLogger(ERR_REFINT_UNABLE_TO_EVALUATE_TARGET_CONDITION.resourceName());
                logger.error(message);
                unacceptableReasons.add(message);
                return false;
            }
        }

        /** Gets the path associated with this listener. */
        private ManagedObjectPath<C, S> getManagedObjectPath() {
            return path;
        }

    }

    /** A delete listener which prevents the named component from being deleted. */
    private final class ReferentialIntegrityDeleteListener implements ConfigurationDeleteListener<S> {

        /** The DN of the referenced configuration entry. */
        private final DN dn;

        /**
         * The error message which should be returned if an attempt is
         * made to delete the referenced component.
         */
        private final LocalizableMessage message;

        /** Creates a new referential integrity delete listener. */
        private ReferentialIntegrityDeleteListener(DN dn, LocalizableMessage message) {
            this.dn = dn;
            this.message = message;
        }

        @Override
        public ConfigChangeResult applyConfigurationDelete(S configuration) {
            // This should not happen - the
            // isConfigurationDeleteAcceptable() call-back should have
            // trapped this.
            if (configuration.dn().equals(dn)) {
                // This should not happen - the
                // isConfigurationDeleteAcceptable() call-back should have
                // trapped this.
                throw new IllegalStateException("Attempting to delete a referenced "
                    + relationDefinition.getChildDefinition().getUserFriendlyName());
            } else {
                return new ConfigChangeResult();
            }
        }

        @Override
        public boolean isConfigurationDeleteAcceptable(S configuration, List<LocalizableMessage> unacceptableReasons) {
            if (configuration.dn().equals(dn)) {
                // Always prevent deletion of the referenced component.
                unacceptableReasons.add(message);
                return false;
            }
            return true;
        }

    }

    /** The server-side constraint handler implementation. */
    private class ServerHandler extends ServerConstraintHandler {

        @Override
        public boolean isUsable(ServerManagedObject<?> managedObject,
            Collection<LocalizableMessage> unacceptableReasons) throws ConfigException {
            SortedSet<String> names = managedObject.getPropertyValues(AggregationPropertyDefinition.this);
            ServerManagementContext context = managedObject.getServerContext();
            LocalizableMessage thisUFN = managedObject.getManagedObjectDefinition().getUserFriendlyName();
            String thisDN = managedObject.getDN().toString();
            LocalizableMessage thatUFN = getRelationDefinition().getUserFriendlyName();

            boolean isUsable = true;
            boolean needsEnabling = targetNeedsEnablingCondition.evaluate(managedObject);
            for (String name : names) {
                ManagedObjectPath<C, S> path = getChildPath(name);
                String thatDN = path.toDN().toString();

                if (!context.managedObjectExists(path)) {
                    LocalizableMessage msg =
                        ERR_SERVER_REFINT_DANGLING_REFERENCE.get(name, getName(), thisUFN, thisDN, thatUFN, thatDN);
                    unacceptableReasons.add(msg);
                    isUsable = false;
                } else if (needsEnabling) {
                    // Check that the referenced component is enabled if
                    // required.
                    ServerManagedObject<? extends S> ref = context.getManagedObject(path);
                    if (!targetIsEnabledCondition.evaluate(ref)) {
                        LocalizableMessage msg =
                            ERR_SERVER_REFINT_TARGET_DISABLED.get(name, getName(), thisUFN, thisDN, thatUFN, thatDN);
                        unacceptableReasons.add(msg);
                        isUsable = false;
                    }
                }
            }

            return isUsable;
        }

        @Override
        public void performPostAdd(ServerManagedObject<?> managedObject) throws ConfigException {
            // First make sure existing listeners associated with this
            // managed object are removed. This is required in order to
            // prevent multiple change listener registrations from
            // occurring, for example if this call-back is invoked multiple
            // times after the same add event.
            performPostDelete(managedObject);

            // Add change and delete listeners against all referenced
            // components.
            LocalizableMessage thisUFN = managedObject.getManagedObjectDefinition().getUserFriendlyName();
            String thisDN = managedObject.getDN().toString();
            LocalizableMessage thatUFN = getRelationDefinition().getUserFriendlyName();

            // Referenced managed objects will only need a change listener
            // if they have can be disabled.
            boolean needsChangeListeners = targetNeedsEnablingCondition.evaluate(managedObject);

            // Delete listeners need to be registered against the parent
            // entry of the referenced components.
            ServerManagementContext context = managedObject.getServerContext();
            ManagedObjectPath<?, ?> parentPath = getParentPath();
            ServerManagedObject<?> parent = context.getManagedObject(parentPath);

            // Create entries in the listener tables.
            List<ReferentialIntegrityDeleteListener> dlist = new LinkedList<>();
            deleteListeners.put(managedObject.getDN(), dlist);

            List<ReferentialIntegrityChangeListener> clist = new LinkedList<>();
            changeListeners.put(managedObject.getDN(), clist);

            for (String name : managedObject.getPropertyValues(AggregationPropertyDefinition.this)) {
                ManagedObjectPath<C, S> path = getChildPath(name);
                DN dn = path.toDN();
                String thatDN = dn.toString();

                // Register the delete listener.
                LocalizableMessage msg =
                    ERR_SERVER_REFINT_CANNOT_DELETE.get(thatUFN, thatDN, getName(), thisUFN, thisDN);
                ReferentialIntegrityDeleteListener dl = new ReferentialIntegrityDeleteListener(dn, msg);
                parent.registerDeleteListener(getRelationDefinition(), dl);
                dlist.add(dl);

                // Register the change listener if required.
                if (needsChangeListeners) {
                    ServerManagedObject<? extends S> ref = context.getManagedObject(path);
                    msg = ERR_SERVER_REFINT_CANNOT_DISABLE.get(thatUFN, thatDN, getName(), thisUFN, thisDN);
                    ReferentialIntegrityChangeListener cl = new ReferentialIntegrityChangeListener(path, msg);
                    ref.registerChangeListener(cl);
                    clist.add(cl);
                }
            }
        }

        @Override
        public void performPostDelete(ServerManagedObject<?> managedObject) throws ConfigException {
            // Remove any registered delete and change listeners.
            ServerManagementContext context = managedObject.getServerContext();
            DN dn = managedObject.getDN();

            // Delete listeners need to be deregistered against the parent
            // entry of the referenced components.
            ManagedObjectPath<?, ?> parentPath = getParentPath();
            ServerManagedObject<?> parent = context.getManagedObject(parentPath);
            if (deleteListeners.containsKey(dn)) {
                for (ReferentialIntegrityDeleteListener dl : deleteListeners.get(dn)) {
                    parent.deregisterDeleteListener(getRelationDefinition(), dl);
                }
                deleteListeners.remove(dn);
            }

            // Change listeners need to be deregistered from their
            // associated referenced component.
            if (changeListeners.containsKey(dn)) {
                for (ReferentialIntegrityChangeListener cl : changeListeners.get(dn)) {
                    ManagedObjectPath<C, S> path = cl.getManagedObjectPath();
                    ServerManagedObject<? extends S> ref = context.getManagedObject(path);
                    ref.deregisterChangeListener(cl);
                }
                changeListeners.remove(dn);
            }
        }

        @Override
        public void performPostModify(ServerManagedObject<?> managedObject) throws ConfigException {
            // Remove all the constraints associated with this managed
            // object and then re-register them.
            performPostDelete(managedObject);
            performPostAdd(managedObject);
        }
    }

    /**
     * The client-side constraint handler implementation which enforces
     * referential integrity when aggregating managed objects are added or
     * modified.
     */
    private class SourceClientHandler extends ClientConstraintHandler {

        @Override
        public boolean isAddAcceptable(ManagementContext context, ManagedObject<?> managedObject,
            Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            // If all of this managed object's "enabled" properties are true
            // then any referenced managed objects must also be enabled.
            boolean needsEnabling = targetNeedsEnablingCondition.evaluate(context, managedObject);

            // Check the referenced managed objects exist and, if required,
            // are enabled.
            boolean isAcceptable = true;
            LocalizableMessage ufn = getRelationDefinition().getUserFriendlyName();
            for (String name : managedObject.getPropertyValues(AggregationPropertyDefinition.this)) {
                // Retrieve the referenced managed object and make sure it
                // exists.
                ManagedObjectPath<?, ?> path = getChildPath(name);
                ManagedObject<?> ref;
                try {
                    ref = context.getManagedObject(path);
                } catch (DefinitionDecodingException | ManagedObjectDecodingException e) {
                    LocalizableMessage msg =
                        ERR_CLIENT_REFINT_TARGET_INVALID.get(ufn, name, getName(), e.getMessageObject());
                    unacceptableReasons.add(msg);
                    isAcceptable = false;
                    continue;
                } catch (ManagedObjectNotFoundException e) {
                    LocalizableMessage msg = ERR_CLIENT_REFINT_TARGET_DANGLING_REFERENCE.get(ufn, name, getName());
                    unacceptableReasons.add(msg);
                    isAcceptable = false;
                    continue;
                }

                // Make sure the reference managed object is enabled.
                if (needsEnabling
                        && !targetIsEnabledCondition.evaluate(context, ref)) {
                    LocalizableMessage msg = ERR_CLIENT_REFINT_TARGET_DISABLED.get(ufn, name, getName());
                    unacceptableReasons.add(msg);
                    isAcceptable = false;
                }
            }
            return isAcceptable;
        }

        @Override
        public boolean isModifyAcceptable(ManagementContext context, ManagedObject<?> managedObject,
            Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            // The same constraint applies as for adds.
            return isAddAcceptable(context, managedObject, unacceptableReasons);
        }

    }

    /**
     * The client-side constraint handler implementation which enforces
     * referential integrity when aggregated managed objects are deleted or
     * modified.
     */
    private class TargetClientHandler extends ClientConstraintHandler {

        @Override
        public boolean isDeleteAcceptable(ManagementContext context, ManagedObjectPath<?, ?> path,
            Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            // Any references to the deleted managed object should cause a
            // constraint violation.
            boolean isAcceptable = true;
            for (ManagedObject<?> mo : findReferences(context, getManagedObjectDefinition(), path.getName())) {
                final LocalizableMessage uName1 = mo.getManagedObjectDefinition().getUserFriendlyName();
                final LocalizableMessage uName2 = getManagedObjectDefinition().getUserFriendlyName();
                final String moName = mo.getManagedObjectPath().getName();

                final LocalizableMessage msg = moName != null
                    ? ERR_CLIENT_REFINT_CANNOT_DELETE_WITH_NAME.get(getName(), uName1, moName, uName2)
                    : ERR_CLIENT_REFINT_CANNOT_DELETE_WITHOUT_NAME.get(getName(), uName1, uName2);
                unacceptableReasons.add(msg);
                isAcceptable = false;
            }
            return isAcceptable;
        }

        @Override
        public boolean isModifyAcceptable(ManagementContext context, ManagedObject<?> managedObject,
            Collection<LocalizableMessage> unacceptableReasons) throws LdapException {
            // If the modified managed object is disabled and there are some
            // active references then refuse the change.
            if (targetIsEnabledCondition.evaluate(context, managedObject)) {
                return true;
            }

            // The referenced managed object is disabled. Need to check for
            // active references.
            boolean isAcceptable = true;
            for (ManagedObject<?> mo : findReferences(context, getManagedObjectDefinition(), managedObject
                .getManagedObjectPath().getName())) {
                if (targetNeedsEnablingCondition.evaluate(context, mo)) {
                    final LocalizableMessage uName1 = managedObject.getManagedObjectDefinition().getUserFriendlyName();
                    final LocalizableMessage uName2 = mo.getManagedObjectDefinition().getUserFriendlyName();
                    final String moName = mo.getManagedObjectPath().getName();

                    final LocalizableMessage msg = moName != null
                        ? ERR_CLIENT_REFINT_CANNOT_DISABLE_WITH_NAME.get(uName1, getName(), uName2, moName)
                        : ERR_CLIENT_REFINT_CANNOT_DISABLE_WITHOUT_NAME.get(uName1, getName(), uName2);
                    unacceptableReasons.add(msg);
                    isAcceptable = false;
                }
            }
            return isAcceptable;
        }

        /** Find all managed objects which reference the named managed object using this property. */
        private <C1 extends ConfigurationClient> List<ManagedObject<? extends C1>> findReferences(
            ManagementContext context, AbstractManagedObjectDefinition<C1, ?> mod, String name)
                throws LdapException {
            List<ManagedObject<? extends C1>> instances = findInstances(context, mod);

            Iterator<ManagedObject<? extends C1>> i = instances.iterator();
            while (i.hasNext()) {
                ManagedObject<? extends C1> mo = i.next();
                boolean hasReference = false;

                for (String value : mo.getPropertyValues(AggregationPropertyDefinition.this)) {
                    if (compare(value, name) == 0) {
                        hasReference = true;
                        break;
                    }
                }

                if (!hasReference) {
                    i.remove();
                }
            }

            return instances;
        }

        /** Find all instances of a specific type of managed object. */
        @SuppressWarnings("unchecked")
        private <C1 extends ConfigurationClient> List<ManagedObject<? extends C1>> findInstances(
            ManagementContext context, AbstractManagedObjectDefinition<C1, ?> mod) throws LdapException {
            List<ManagedObject<? extends C1>> instances = new LinkedList<>();

            if (mod == RootCfgDefn.getInstance()) {
                instances.add((ManagedObject<? extends C1>) context.getRootConfigurationManagedObject());
            } else {
                for (RelationDefinition<? super C1, ?> rd : mod.getAllReverseRelationDefinitions()) {
                    for (ManagedObject<?> parent : findInstances(context, rd.getParentDefinition())) {
                        try {
                            if (rd instanceof SingletonRelationDefinition) {
                                SingletonRelationDefinition<? super C1, ?> srd =
                                    (SingletonRelationDefinition<? super C1, ?>) rd;
                                ManagedObject<?> mo = parent.getChild(srd);
                                if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                                    instances.add((ManagedObject<? extends C1>) mo);
                                }
                            } else if (rd instanceof OptionalRelationDefinition) {
                                OptionalRelationDefinition<? super C1, ?> ord =
                                    (OptionalRelationDefinition<? super C1, ?>) rd;
                                ManagedObject<?> mo = parent.getChild(ord);
                                if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                                    instances.add((ManagedObject<? extends C1>) mo);
                                }
                            } else if (rd instanceof InstantiableRelationDefinition) {
                                InstantiableRelationDefinition<? super C1, ?> ird =
                                    (InstantiableRelationDefinition<? super C1, ?>) rd;

                                for (String name : parent.listChildren(ird)) {
                                    ManagedObject<?> mo = parent.getChild(ird, name);
                                    if (mo.getManagedObjectDefinition().isChildOf(mod)) {
                                        instances.add((ManagedObject<? extends C1>) mo);
                                    }
                                }
                            }
                        } catch (OperationsException e) {
                            // Ignore all operations exceptions.
                        }
                    }
                }
            }

            return instances;
        }
    }

    /**
     * Creates an aggregation property definition builder.
     *
     * @param <C>
     *            The type of client managed object configuration that this
     *            aggregation property definition refers to.
     * @param <S>
     *            The type of server managed object configuration that this
     *            aggregation property definition refers to.
     * @param d
     *            The managed object definition associated with this property
     *            definition.
     * @param propertyName
     *            The property name.
     * @return Returns the new aggregation property definition builder.
     */
    public static <C extends ConfigurationClient, S extends Configuration> Builder<C, S> createBuilder(
        AbstractManagedObjectDefinition<?, ?> d, String propertyName) {
        return new Builder<>(d, propertyName);
    }

    private static final Logger debugLogger = LoggerFactory.getLogger(AggregationPropertyDefinition.class);

    /** The active server-side referential integrity change listeners associated with this property. */
    private final Map<DN, List<ReferentialIntegrityChangeListener>> changeListeners = new HashMap<>();

    /** The active server-side referential integrity delete listeners associated with this property. */
    private final Map<DN, List<ReferentialIntegrityDeleteListener>> deleteListeners = new HashMap<>();

    /** The name of the managed object which is the parent of the aggregated managed objects. */
    private ManagedObjectPath<?, ?> parentPath;

    /**
     * The string representation of the managed object path specifying
     * the parent of the aggregated managed objects.
     */
    private final String parentPathString;

    /**
     * The name of a relation in the parent managed object which
     * contains the aggregated managed objects.
     */
    private final String rdName;

    /** The relation in the parent managed object which contains the aggregated managed objects. */
    private InstantiableRelationDefinition<C, S> relationDefinition;

    /** The source constraint. */
    private final Constraint sourceConstraint;

    /** The condition which is used to determine if a referenced managed object is enabled. */
    private final Condition targetIsEnabledCondition;

    /**
     * The condition which is used to determine whether
     * referenced managed objects need to be enabled.
     */
    private final Condition targetNeedsEnablingCondition;

    /** Private constructor. */
    private AggregationPropertyDefinition(AbstractManagedObjectDefinition<?, ?> d, String propertyName,
        EnumSet<PropertyOption> options, AdministratorAction adminAction,
        DefaultBehaviorProvider<String> defaultBehavior, String parentPathString, String rdName,
        Condition targetNeedsEnablingCondition, Condition targetIsEnabledCondition) {
        super(d, String.class, propertyName, options, adminAction, defaultBehavior);

        this.parentPathString = parentPathString;
        this.rdName = rdName;
        this.targetNeedsEnablingCondition = targetNeedsEnablingCondition;
        this.targetIsEnabledCondition = targetIsEnabledCondition;
        this.sourceConstraint = new Constraint() {

            @Override
            public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
                ClientConstraintHandler handler = new SourceClientHandler();
                return Collections.singleton(handler);
            }

            @Override
            public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
                ServerConstraintHandler handler = new ServerHandler();
                return Collections.singleton(handler);
            }
        };
    }

    @Override
    public <R, P> R accept(PropertyDefinitionVisitor<R, P> v, P p) {
        return v.visitAggregation(this, p);
    }

    @Override
    public <R, P> R accept(PropertyValueVisitor<R, P> v, String value, P p) {
        return v.visitAggregation(this, value, p);
    }

    @Override
    public String decodeValue(String value) {
        Reject.ifNull(value);

        try {
            validateValue(value);
            return value;
        } catch (PropertyException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    /**
     * Constructs a DN for a referenced managed object having the provided name.
     * This method is implemented by first calling {@link #getChildPath(String)}
     * and then invoking {@code ManagedObjectPath.toDN()} on the returned path.
     *
     * @param name
     *            The name of the child managed object.
     * @return Returns a DN for a referenced managed object having the provided
     *         name.
     */
    public final DN getChildDN(String name) {
        return getChildPath(name).toDN();
    }

    /**
     * Constructs a managed object path for a referenced managed object having
     * the provided name.
     *
     * @param name
     *            The name of the child managed object.
     * @return Returns a managed object path for a referenced managed object
     *         having the provided name.
     */
    public final ManagedObjectPath<C, S> getChildPath(String name) {
        return parentPath.child(relationDefinition, name);
    }

    /**
     * Gets the name of the managed object which is the parent of the aggregated
     * managed objects.
     *
     * @return Returns the name of the managed object which is the parent of the
     *         aggregated managed objects.
     */
    public final ManagedObjectPath<?, ?> getParentPath() {
        return parentPath;
    }

    /**
     * Gets the relation in the parent managed object which contains the
     * aggregated managed objects.
     *
     * @return Returns the relation in the parent managed object which contains
     *         the aggregated managed objects.
     */
    public final InstantiableRelationDefinition<C, S> getRelationDefinition() {
        return relationDefinition;
    }

    /**
     * Gets the constraint which should be enforced on the aggregating managed
     * object.
     *
     * @return Returns the constraint which should be enforced on the
     *         aggregating managed object.
     */
    public final Constraint getSourceConstraint() {
        return sourceConstraint;
    }

    /**
     * Gets the optional constraint synopsis of this aggregation property
     * definition in the default locale. The constraint synopsis describes when
     * and how referenced managed objects must be enabled. When there are no
     * constraints between the source managed object and the objects it
     * references through this aggregation, <code>null</code> is returned.
     *
     * @return Returns the optional constraint synopsis of this aggregation
     *         property definition in the default locale, or <code>null</code>
     *         if there is no constraint synopsis.
     */
    public final LocalizableMessage getSourceConstraintSynopsis() {
        return getSourceConstraintSynopsis(Locale.getDefault());
    }

    /**
     * Gets the optional constraint synopsis of this aggregation property
     * definition in the specified locale.The constraint synopsis describes when
     * and how referenced managed objects must be enabled. When there are no
     * constraints between the source managed object and the objects it
     * references through this aggregation, <code>null</code> is returned.
     *
     * @param locale
     *            The locale.
     * @return Returns the optional constraint synopsis of this aggregation
     *         property definition in the specified locale, or <code>null</code>
     *         if there is no constraint synopsis.
     */
    public final LocalizableMessage getSourceConstraintSynopsis(Locale locale) {
        ManagedObjectDefinitionI18NResource resource = ManagedObjectDefinitionI18NResource.getInstance();
        String property = "property." + getName() + ".syntax.aggregation.constraint-synopsis";
        try {
            return resource.getMessage(getManagedObjectDefinition(), property, locale);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * Gets the condition which is used to determine if a referenced managed
     * object is enabled.
     *
     * @return Returns the condition which is used to determine if a referenced
     *         managed object is enabled.
     */
    public final Condition getTargetIsEnabledCondition() {
        return targetIsEnabledCondition;
    }

    /**
     * Gets the condition which is used to determine whether referenced
     * managed objects need to be enabled.
     *
     * @return Returns the condition which is used to determine whether
     *         referenced managed objects need to be enabled.
     */
    public final Condition getTargetNeedsEnablingCondition() {
        return targetNeedsEnablingCondition;
    }

    @Override
    public String normalizeValue(String value) {
        try {
            Reference<C, S> reference = Reference.parseName(parentPath, relationDefinition, value);
            return reference.getNormalizedName();
        } catch (IllegalArgumentException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    @Override
    public void toString(StringBuilder builder) {
        super.toString(builder);

        builder.append(" parentPath=");
        builder.append(parentPath);

        builder.append(" relationDefinition=");
        builder.append(relationDefinition.getName());

        builder.append(" targetNeedsEnablingCondition=");
        builder.append(targetNeedsEnablingCondition);

        builder.append(" targetIsEnabledCondition=");
        builder.append(targetIsEnabledCondition);
    }

    @Override
    public void validateValue(String value) {
        try {
            Reference.parseName(parentPath, relationDefinition, value);
        } catch (IllegalArgumentException e) {
            throw PropertyException.illegalPropertyValueException(this, value);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public void initialize() throws Exception {
        // Decode the path.
        parentPath = ManagedObjectPath.valueOf(parentPathString);

        // Decode the relation definition.
        AbstractManagedObjectDefinition<?, ?> parent = parentPath.getManagedObjectDefinition();
        RelationDefinition<?, ?> rd = parent.getRelationDefinition(rdName);
        relationDefinition = (InstantiableRelationDefinition<C, S>) rd;

        // Now decode the conditions.
        targetNeedsEnablingCondition.initialize(getManagedObjectDefinition());
        targetIsEnabledCondition.initialize(rd.getChildDefinition());

        // Register a client-side constraint with the referenced
        // definition. This will be used to enforce referential integrity
        // for actions performed against referenced managed objects.
        Constraint constraint = new Constraint() {

            @Override
            public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
                ClientConstraintHandler handler = new TargetClientHandler();
                return Collections.singleton(handler);
            }

            @Override
            public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
                return Collections.emptyList();
            }
        };

        rd.getChildDefinition().registerConstraint(constraint);
    }

}
