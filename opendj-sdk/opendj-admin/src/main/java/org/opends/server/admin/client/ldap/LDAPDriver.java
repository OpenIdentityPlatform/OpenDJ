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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock, AS.
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
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.admin.client.RootCfgClient;
import org.forgerock.opendj.admin.meta.RootCfgDefn;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.DefinitionResolver;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.LDAPProfile;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionVisitor;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.Reference;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.admin.UnknownPropertyDefinitionException;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.OperationRejectedException.OperationType;
import org.opends.server.admin.client.spi.Driver;
import org.opends.server.admin.client.spi.PropertySet;

/**
 * The LDAP management context driver implementation.
 */
final class LDAPDriver extends Driver {

    /**
     * A visitor which is used to decode property LDAP values.
     */
    private static final class ValueDecoder extends PropertyDefinitionVisitor<Object, String> {

        /**
         * Decodes the provided property LDAP value.
         *
         * @param <PD>
         *            The type of the property.
         * @param pd
         *            The property definition.
         * @param value
         *            The LDAP string representation.
         * @return Returns the decoded LDAP value.
         * @throws IllegalPropertyValueStringException
         *             If the property value could not be decoded because it was
         *             invalid.
         */
        public static <PD> PD decode(PropertyDefinition<PD> pd, Object value)
                throws IllegalPropertyValueStringException {
            String s = String.valueOf(value);
            return pd.castValue(pd.accept(new ValueDecoder(), s));
        }

        // Prevent instantiation.
        private ValueDecoder() {
            // No implementation required.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> Object visitAggregation(
                AggregationPropertyDefinition<C, S> d, String p) {
            // Aggregations values are stored as full DNs in LDAP, but
            // just their common name is exposed in the admin framework.
            try {
                Reference<C, S> reference = Reference.parseDN(d.getParentPath(), d.getRelationDefinition(), p);
                return reference.getName();
            } catch (IllegalArgumentException e) {
                throw new IllegalPropertyValueStringException(d, p);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <T> Object visitUnknown(PropertyDefinition<T> d, String p) throws UnknownPropertyDefinitionException {
            // By default the property definition's decoder will do.
            return d.decodeValue(p);
        }
    }

    // The LDAP connection.
    private final LDAPConnection connection;

    // The LDAP management context.
    private final LDAPManagementContext context;

    // The LDAP profile which should be used to construct LDAP
    // requests and decode LDAP responses.
    private final LDAPProfile profile;

    /**
     * Creates a new LDAP driver using the specified LDAP connection and
     * profile.
     *
     * @param context
     *            The LDAP management context.
     * @param connection
     *            The LDAP connection.
     * @param profile
     *            The LDAP profile.
     */
    public LDAPDriver(LDAPManagementContext context, LDAPConnection connection, LDAPProfile profile) {
        this.context = context;
        this.connection = connection;
        this.profile = profile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        connection.unbind();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getManagedObject(
            ManagedObjectPath<C, S> path) throws DefinitionDecodingException, ManagedObjectDecodingException,
            ManagedObjectNotFoundException, ErrorResultException {
        if (!managedObjectExists(path)) {
            throw new ManagedObjectNotFoundException();
        }

        try {
            // Read the entry associated with the managed object.
            DN dn = LDAPNameBuilder.create(path, profile);
            AbstractManagedObjectDefinition<C, S> d = path.getManagedObjectDefinition();
            ManagedObjectDefinition<? extends C, ? extends S> mod = getEntryDefinition(d, dn);

            ArrayList<String> attrIds = new ArrayList<String>();
            for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
                String attrId = profile.getAttributeName(mod, pd);
                attrIds.add(attrId);
            }

            SearchResultEntry searchResultEntry = connection.readEntry(dn, attrIds);

            // Build the managed object's properties.
            List<PropertyException> exceptions = new LinkedList<PropertyException>();
            PropertySet newProperties = new PropertySet();
            for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
                String attrID = profile.getAttributeName(mod, pd);
                Attribute attribute = searchResultEntry.getAttribute(attrID);
                try {
                    decodeProperty(newProperties, path, pd, attribute);
                } catch (PropertyException e) {
                    exceptions.add(e);
                }
            }

            // If there were no decoding problems then return the object,
            // otherwise throw an operations exception.
            ManagedObject<? extends C> mo = createExistingManagedObject(mod, path, newProperties);
            if (exceptions.isEmpty()) {
                return mo;
            } else {
                throw new ManagedObjectDecodingException(mo, exceptions);
            }
        } catch (ErrorResultException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                throw new ManagedObjectNotFoundException();
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfigurationClient, S extends Configuration, PD> SortedSet<PD> getPropertyValues(
            ManagedObjectPath<C, S> path, PropertyDefinition<PD> propertyDef) throws IllegalArgumentException,
            DefinitionDecodingException, ManagedObjectNotFoundException, ErrorResultException, PropertyException {
        // Check that the requested property is from the definition
        // associated with the path.
        AbstractManagedObjectDefinition<C, S> d = path.getManagedObjectDefinition();
        PropertyDefinition<?> tmp = d.getPropertyDefinition(propertyDef.getName());
        if (tmp != propertyDef) {
            throw new IllegalArgumentException("The property " + propertyDef.getName() + " is not associated with a "
                    + d.getName());
        }

        if (!managedObjectExists(path)) {
            throw new ManagedObjectNotFoundException();
        }

        try {
            // Read the entry associated with the managed object.
            DN dn = LDAPNameBuilder.create(path, profile);
            ManagedObjectDefinition<? extends C, ? extends S> objectDef = getEntryDefinition(d, dn);

            // Make sure we use the correct property definition, the
            // provided one might have been overridden in the resolved
            // definition.
            propertyDef = (PropertyDefinition<PD>) objectDef.getPropertyDefinition(propertyDef.getName());

            String attrID = profile.getAttributeName(objectDef, propertyDef);
            SearchResultEntry resultEntry = connection.readEntry(dn, Collections.singleton(attrID));
            Attribute attribute = resultEntry.getAttribute(attrID);

            // Decode the values.
            SortedSet<PD> values = new TreeSet<PD>(propertyDef);
            if (attribute != null) {
                for (ByteString byteValue : attribute) {
                    PD value = ValueDecoder.decode(propertyDef, byteValue);
                    values.add(value);
                }
            }

            // Sanity check the returned values.
            if (values.size() > 1 && !propertyDef.hasOption(PropertyOption.MULTI_VALUED)) {
                throw new PropertyIsSingleValuedException(propertyDef);
            }

            if (values.isEmpty() && propertyDef.hasOption(PropertyOption.MANDATORY)) {
                throw new PropertyIsMandatoryException(propertyDef);
            }

            if (values.isEmpty()) {
                // Use the property's default values.
                values.addAll(findDefaultValues(path.asSubType(objectDef), propertyDef, false));
            }

            return values;
        } catch (ErrorResultException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                throw new ManagedObjectNotFoundException();
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
        return new LDAPManagedObject<RootCfgClient>(this, RootCfgDefn.getInstance(), ManagedObjectPath.emptyPath(),
                new PropertySet(), true, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws IllegalArgumentException,
            ManagedObjectNotFoundException, ErrorResultException {
        validateRelationDefinition(parent, rd);

        if (!managedObjectExists(parent)) {
            throw new ManagedObjectNotFoundException();
        }

        // Get the search base DN.
        DN dn = LDAPNameBuilder.create(parent, rd, profile);

        // Retrieve only those entries which are sub-types of the
        // specified definition.
        StringBuilder builder = new StringBuilder();
        builder.append("(objectclass=");
        builder.append(profile.getObjectClass(d));
        builder.append(')');
        String filter = builder.toString();

        List<String> children = new ArrayList<String>();
        try {
            for (DN child : connection.listEntries(dn, filter)) {
                children.add(child.rdn().getFirstAVA().getAttributeValue().toString());
            }
        } catch (ErrorResultException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                // Ignore this
                // It means that the base entry does not exist
                // It might not if this managed object has just been created.
            } else {
                throw e;
            }
        }
        return children.toArray(new String[children.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws IllegalArgumentException,
            ManagedObjectNotFoundException, ErrorResultException {
        validateRelationDefinition(parent, rd);

        if (!managedObjectExists(parent)) {
            throw new ManagedObjectNotFoundException();
        }

        // Get the search base DN.
        DN dn = LDAPNameBuilder.create(parent, rd, profile);

        // Retrieve only those entries which are sub-types of the
        // specified definition.
        StringBuilder builder = new StringBuilder();
        builder.append("(objectclass=");
        builder.append(profile.getObjectClass(d));
        builder.append(')');
        String filter = builder.toString();

        List<String> children = new ArrayList<String>();
        try {
            for (DN child : connection.listEntries(dn, filter)) {
                children.add(child.rdn().getFirstAVA().getAttributeValue().toString());
            }
        } catch (ErrorResultException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                // Ignore this
                // It means that the base entry does not exist
                // It might not if this managed object has just been created.
            } else {
                throw e;
            }
        }

        return children.toArray(new String[children.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean managedObjectExists(ManagedObjectPath<?, ?> path) throws ManagedObjectNotFoundException,
            ErrorResultException {
        if (path.isEmpty()) {
            return true;
        }

        ManagedObjectPath<?, ?> parent = path.parent();
        DN dn = LDAPNameBuilder.create(parent, profile);
        if (!entryExists(dn)) {
            throw new ManagedObjectNotFoundException();
        }

        dn = LDAPNameBuilder.create(path, profile);
        return entryExists(dn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <C extends ConfigurationClient, S extends Configuration> void deleteManagedObject(
            ManagedObjectPath<C, S> path) throws OperationRejectedException, ErrorResultException {
        // Delete the entry and any subordinate entries.
        DN dn = LDAPNameBuilder.create(path, profile);
        try {
            connection.deleteSubtree(dn);
        } catch(ErrorResultException e) {
            if (e.getResult().getResultCode()==ResultCode.UNWILLING_TO_PERFORM) {
                AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
                LocalizableMessage m = LocalizableMessage.raw("%s", e.getMessage());
                throw new OperationRejectedException(OperationType.DELETE, d.getUserFriendlyName(), m);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LDAPManagementContext getManagementContext() {
        return context;
    }

    /**
     * Determines whether the named LDAP entry exists.
     *
     * @param dn
     *            The LDAP entry name.
     * @return Returns <code>true</code> if the named LDAP entry exists.
     * @throws ErrorResultException
     *             if a problem occurs.
     */
    boolean entryExists(DN dn) throws ErrorResultException {
        return connection.entryExists(dn);
    }

    /**
     * Gets the LDAP connection used for interacting with the server.
     *
     * @return Returns the LDAP connection used for interacting with the server.
     */
    LDAPConnection getLDAPConnection() {
        return connection;
    }

    /**
     * Gets the LDAP profile which should be used to construct LDAP requests and
     * decode LDAP responses.
     *
     * @return Returns the LDAP profile which should be used to construct LDAP
     *         requests and decode LDAP responses.
     */
    LDAPProfile getLDAPProfile() {
        return profile;
    }

    // Create a managed object which already exists on the server.
    private <M extends ConfigurationClient, N extends Configuration> ManagedObject<M> createExistingManagedObject(
            ManagedObjectDefinition<M, N> d, ManagedObjectPath<? super M, ? super N> p, PropertySet properties) {
        RelationDefinition<?, ?> rd = p.getRelationDefinition();
        PropertyDefinition<?> pd = null;
        if (rd instanceof InstantiableRelationDefinition) {
            InstantiableRelationDefinition<?, ?> ird = (InstantiableRelationDefinition<?, ?>) rd;
            pd = ird.getNamingPropertyDefinition();
        }
        return new LDAPManagedObject<M>(this, d, p.asSubType(d), properties, true, pd);
    }

    // Create a property using the provided string values.
    private <PD> void decodeProperty(PropertySet newProperties, ManagedObjectPath<?, ?> path,
            PropertyDefinition<PD> propertyDef, Attribute attribute) throws PropertyException {
        PropertyException exception = null;

        // Get the property's active values.
        SortedSet<PD> activeValues = new TreeSet<PD>(propertyDef);
        if (attribute != null) {
            for (ByteString byteValue : attribute) {
                PD value = ValueDecoder.decode(propertyDef, byteValue);
                activeValues.add(value);
            }
        }

        if (activeValues.size() > 1 && !propertyDef.hasOption(PropertyOption.MULTI_VALUED)) {
            // This exception takes precedence over previous exceptions.
            exception = new PropertyIsSingleValuedException(propertyDef);
            PD value = activeValues.first();
            activeValues.clear();
            activeValues.add(value);
        }

        // Get the property's default values.
        Collection<PD> defaultValues;
        try {
            defaultValues = findDefaultValues(path, propertyDef, false);
        } catch (DefaultBehaviorException e) {
            defaultValues = Collections.emptySet();
            exception = e;
        }

        newProperties.addProperty(propertyDef, defaultValues, activeValues);

        if (activeValues.isEmpty() && defaultValues.isEmpty() && propertyDef.hasOption(PropertyOption.MANDATORY)) {
            // The active values maybe empty because of a previous
            // exception.
            if (exception == null) {
                exception = new PropertyIsMandatoryException(propertyDef);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    // Determine the type of managed object associated with the named
    // entry.
    private <C extends ConfigurationClient, S extends Configuration> ManagedObjectDefinition<? extends C, ? extends S> getEntryDefinition(
            AbstractManagedObjectDefinition<C, S> d, DN dn) throws ErrorResultException, DefinitionDecodingException {
        SearchResultEntry searchResultEntry = connection.readEntry(dn, Collections.singleton("objectclass"));
        Attribute objectClassAttr = searchResultEntry.getAttribute("objectclass");

        if (objectClassAttr == null) {
            // No object classes.
            throw new DefinitionDecodingException(d, Reason.NO_TYPE_INFORMATION);
        }

        final Set<String> objectClasses = new HashSet<String>();
        for (ByteString byteValue : objectClassAttr) {
            objectClasses.add(byteValue.toString().toLowerCase().trim());
        }

        if (objectClasses.isEmpty()) {
            // No object classes.
            throw new DefinitionDecodingException(d, Reason.NO_TYPE_INFORMATION);
        }

        // Resolve the appropriate sub-type based on the object classes.
        DefinitionResolver resolver = new DefinitionResolver() {
            public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
                String objectClass = profile.getObjectClass(d);
                return objectClasses.contains(objectClass);
            }
        };

        return d.resolveManagedObjectDefinition(resolver);
    }
}
