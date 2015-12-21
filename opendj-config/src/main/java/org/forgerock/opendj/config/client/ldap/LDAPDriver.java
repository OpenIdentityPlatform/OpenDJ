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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.client.ldap;

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
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.DefinitionResolver;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyDefinitionVisitor;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.Reference;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.DefinitionDecodingException.Reason;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagedObjectDecodingException;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.client.OperationRejectedException.OperationType;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.opendj.config.client.spi.PropertySet;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

/** The LDAP management context driver implementation. */
final class LDAPDriver extends Driver {

    /** A visitor which is used to decode property LDAP values. */
    private static final class ValueDecoder extends PropertyDefinitionVisitor<Object, String> {
        /**
         * Decodes the provided property LDAP value.
         *
         * @param <P>
         *            The type of the property.
         * @param pd
         *            The property definition.
         * @param value
         *            The LDAP string representation.
         * @return Returns the decoded LDAP value.
         * @throws PropertyException
         *             If the property value could not be decoded because it was
         *             invalid.
         */
        public static <P> P decode(PropertyDefinition<P> pd, Object value) {
            String s = String.valueOf(value);
            return pd.castValue(pd.accept(new ValueDecoder(), s));
        }

        /** Prevent instantiation. */
        private ValueDecoder() {
            // Do nothing.
        }

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Object visitAggregation(
            AggregationPropertyDefinition<C, S> d, String p) {
            // Aggregations values are stored as full DNs in LDAP, but
            // just their common name is exposed in the admin framework.
            try {
                Reference<C, S> reference = Reference.parseDN(d.getParentPath(), d.getRelationDefinition(), p);
                return reference.getName();
            } catch (IllegalArgumentException e) {
                throw PropertyException.illegalPropertyValueException(d, p);
            }
        }

        @Override
        public <T> Object visitUnknown(PropertyDefinition<T> d, String p) {
            // By default the property definition's decoder will do.
            return d.decodeValue(p);
        }
    }

    private LDAPManagementContext context;

    private final Connection connection;

    /** The LDAP profile which should be used to construct LDAP requests and decode LDAP responses. */
    private final LDAPProfile profile;

    /**
     * Creates a new LDAP driver using the specified LDAP connection and
     * profile.
     *
     * @param connection
     *            The LDAP connection.
     * @param profile
     *            The LDAP profile.
     */
    LDAPDriver(Connection connection, LDAPProfile profile) {
        this.connection = connection;
        this.profile = profile;
    }

    void setManagementContext(LDAPManagementContext context) {
        this.context = context;
    }

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getManagedObject(
        ManagedObjectPath<C, S> path) throws DefinitionDecodingException, ManagedObjectDecodingException,
        ManagedObjectNotFoundException, LdapException {
        if (!managedObjectExists(path)) {
            throw new ManagedObjectNotFoundException();
        }

        try {
            // Read the entry associated with the managed object.
            DN dn = DNBuilder.create(path, profile);
            AbstractManagedObjectDefinition<C, S> d = path.getManagedObjectDefinition();
            ManagedObjectDefinition<? extends C, ? extends S> mod = getEntryDefinition(d, dn);

            ArrayList<String> attrIds = new ArrayList<>();
            for (PropertyDefinition<?> pd : mod.getAllPropertyDefinitions()) {
                attrIds.add(profile.getAttributeName(mod, pd));
            }
            SearchResultEntry searchResultEntry =
                    connection.readEntry(dn, attrIds.toArray(new String[0]));

            // Build the managed object's properties.
            List<PropertyException> exceptions = new LinkedList<>();
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
        } catch (LdapException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                throw new ManagedObjectNotFoundException();
            }
            throw e;
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfigurationClient, S extends Configuration, P> SortedSet<P> getPropertyValues(
        ManagedObjectPath<C, S> path, PropertyDefinition<P> propertyDef) throws DefinitionDecodingException,
        ManagedObjectNotFoundException, LdapException {
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
            DN dn = DNBuilder.create(path, profile);
            ManagedObjectDefinition<? extends C, ? extends S> objectDef = getEntryDefinition(d, dn);

            // Make sure we use the correct property definition, the
            // provided one might have been overridden in the resolved
            // definition.
            propertyDef = (PropertyDefinition<P>) objectDef.getPropertyDefinition(propertyDef.getName());

            String attrID = profile.getAttributeName(objectDef, propertyDef);
            SearchResultEntry resultEntry = connection.readEntry(dn, attrID);
            Attribute attribute = resultEntry.getAttribute(attrID);

            // Decode the values.
            SortedSet<P> values = new TreeSet<>(propertyDef);
            if (attribute != null) {
                for (ByteString byteValue : attribute) {
                    P value = ValueDecoder.decode(propertyDef, byteValue);
                    values.add(value);
                }
            }

            // Sanity check the returned values.
            if (values.size() > 1 && !propertyDef.hasOption(PropertyOption.MULTI_VALUED)) {
                throw PropertyException.propertyIsSingleValuedException(propertyDef);
            }

            if (values.isEmpty() && propertyDef.hasOption(PropertyOption.MANDATORY)) {
                throw PropertyException.propertyIsMandatoryException(propertyDef);
            }

            if (values.isEmpty()) {
                // Use the property's default values.
                values.addAll(findDefaultValues(path.asSubType(objectDef), propertyDef, false));
            }

            return values;
        } catch (LdapException e) {
            if (e.getResult().getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                throw new ManagedObjectNotFoundException();
            }
            throw e;
        }
    }

    @Override
    public ManagedObject<RootCfgClient> getRootConfigurationManagedObject() {
        return new LDAPManagedObject<>(this, RootCfgDefn.getInstance(), ManagedObjectPath.emptyPath(),
            new PropertySet(), true, null);
    }

    @Override
    public <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
        ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
        AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws ManagedObjectNotFoundException,
        LdapException {
        validateRelationDefinition(parent, rd);

        if (!managedObjectExists(parent)) {
            throw new ManagedObjectNotFoundException();
        }

        // Get the search base DN.
        DN dn = DNBuilder.create(parent, rd, profile);

        // Retrieve only those entries which are sub-types of the
        // specified definition.
        Filter filter = Filter.equality("objectClass", profile.getObjectClass(d));
        List<String> children = new ArrayList<>();
        try {
            for (DN child : listEntries(dn, filter)) {
                children.add(child.rdn().getFirstAVA().getAttributeValue().toString());
            }
        } catch (LdapException e) {
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

    @Override
    public <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
        ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd,
        AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws ManagedObjectNotFoundException,
        LdapException {
        validateRelationDefinition(parent, rd);

        if (!managedObjectExists(parent)) {
            throw new ManagedObjectNotFoundException();
        }

        // Get the search base DN.
        DN dn = DNBuilder.create(parent, rd, profile);

        // Retrieve only those entries which are sub-types of the
        // specified definition.
        Filter filter = Filter.equality("objectClass", profile.getObjectClass(d));
        List<String> children = new ArrayList<>();
        try {
            for (DN child : listEntries(dn, filter)) {
                children.add(child.rdn().getFirstAVA().getAttributeValue().toString());
            }
        } catch (LdapException e) {
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

    @Override
    public boolean managedObjectExists(ManagedObjectPath<?, ?> path) throws ManagedObjectNotFoundException,
        LdapException {
        if (path.isEmpty()) {
            return true;
        }

        ManagedObjectPath<?, ?> parent = path.parent();
        DN dn = DNBuilder.create(parent, profile);
        if (!entryExists(dn)) {
            throw new ManagedObjectNotFoundException();
        }

        dn = DNBuilder.create(path, profile);
        return entryExists(dn);
    }

    @Override
    protected <C extends ConfigurationClient, S extends Configuration> void deleteManagedObject(
        ManagedObjectPath<C, S> path) throws OperationRejectedException, LdapException {
        // Delete the entry and any subordinate entries.
        DN dn = DNBuilder.create(path, profile);
        try {
            deleteSubtree(dn);
        } catch (LdapException e) {
            if (e.getResult().getResultCode() == ResultCode.UNWILLING_TO_PERFORM) {
                AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
                LocalizableMessage m = LocalizableMessage.raw("%s", e.getMessage());
                throw new OperationRejectedException(OperationType.DELETE, d.getUserFriendlyName(), m);
            }
            throw e;
        }
    }

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
     * @throws LdapException
     *             if a problem occurs.
     */
    boolean entryExists(DN dn) throws LdapException {
        try {
            // request a non existent attribute name so the search returns as little data as possible
            connection.readEntry(dn, "1.1");
            return true;
        } catch (EntryNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets the LDAP connection used for interacting with the server.
     *
     * @return Returns the LDAP connection used for interacting with the server.
     */
    Connection getLDAPConnection() {
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

    /** Create a managed object which already exists on the server. */
    private <M extends ConfigurationClient, N extends Configuration> ManagedObject<M> createExistingManagedObject(
        ManagedObjectDefinition<M, N> d, ManagedObjectPath<? super M, ? super N> p, PropertySet properties) {
        RelationDefinition<?, ?> rd = p.getRelationDefinition();
        PropertyDefinition<?> pd = null;
        if (rd instanceof InstantiableRelationDefinition) {
            InstantiableRelationDefinition<?, ?> ird = (InstantiableRelationDefinition<?, ?>) rd;
            pd = ird.getNamingPropertyDefinition();
        }
        return new LDAPManagedObject<>(this, d, p.asSubType(d), properties, true, pd);
    }

    /** Create a property using the provided string values. */
    private <P> void decodeProperty(PropertySet newProperties, ManagedObjectPath<?, ?> path,
        PropertyDefinition<P> propertyDef, Attribute attribute) {
        PropertyException exception = null;

        // Get the property's active values.
        SortedSet<P> activeValues = new TreeSet<>(propertyDef);
        if (attribute != null) {
            for (ByteString byteValue : attribute) {
                P value = ValueDecoder.decode(propertyDef, byteValue);
                activeValues.add(value);
            }
        }

        if (activeValues.size() > 1 && !propertyDef.hasOption(PropertyOption.MULTI_VALUED)) {
            // This exception takes precedence over previous exceptions.
            exception = PropertyException.propertyIsSingleValuedException(propertyDef);
            P value = activeValues.first();
            activeValues.clear();
            activeValues.add(value);
        }

        // Get the property's default values.
        Collection<P> defaultValues;
        try {
            defaultValues = findDefaultValues(path, propertyDef, false);
        } catch (PropertyException e) {
            defaultValues = Collections.emptySet();
            exception = e;
        }

        newProperties.addProperty(propertyDef, defaultValues, activeValues);

        if (activeValues.isEmpty()
                && defaultValues.isEmpty()
                && propertyDef.hasOption(PropertyOption.MANDATORY)
                && exception == null) {
            // The active values maybe empty because of a previous exception.
            exception = PropertyException.propertyIsMandatoryException(propertyDef);
        }

        if (exception != null) {
            throw exception;
        }
    }

    // @Checkstyle:off
    /** Determine the type of managed object associated with the named entry. */
    private <C extends ConfigurationClient, S extends Configuration> ManagedObjectDefinition<? extends C, ? extends S>
        getEntryDefinition(AbstractManagedObjectDefinition<C, S> d, DN dn) throws LdapException,
        DefinitionDecodingException {
        // @Checkstyle:on
        SearchResultEntry searchResultEntry = connection.readEntry(dn, "objectclass");
        Attribute objectClassAttr = searchResultEntry.getAttribute("objectclass");

        if (objectClassAttr == null) {
            // No object classes.
            throw new DefinitionDecodingException(d, Reason.NO_TYPE_INFORMATION);
        }

        final Set<String> objectClasses = new HashSet<>();
        for (ByteString byteValue : objectClassAttr) {
            objectClasses.add(byteValue.toString().toLowerCase().trim());
        }

        if (objectClasses.isEmpty()) {
            // No object classes.
            throw new DefinitionDecodingException(d, Reason.NO_TYPE_INFORMATION);
        }

        // Resolve the appropriate sub-type based on the object classes.
        DefinitionResolver resolver = new DefinitionResolver() {
            @Override
            public boolean matches(AbstractManagedObjectDefinition<?, ?> d) {
                String objectClass = profile.getObjectClass(d);
                return objectClasses.contains(objectClass);
            }
        };

        return d.resolveManagedObjectDefinition(resolver);
    }

    /**
     * Delete a subtree of entries. We cannot use the subtree delete control because it is not supported by the config
     * backend.
     */
    private void deleteSubtree(DN dn) throws LdapException {
        // Delete the children first.
        for (DN child : listEntries(dn, Filter.objectClassPresent())) {
            deleteSubtree(child);
        }

        // Delete the named entry.
        connection.delete(dn.toString());
    }

    private Collection<DN> listEntries(DN dn, Filter filter) throws LdapException {
        final SearchRequest searchRequest = Requests.newSearchRequest(dn, SearchScope.SINGLE_LEVEL, filter);
        try (ConnectionEntryReader reader = connection.search(searchRequest)) {
            List<DN> names = new LinkedList<>();
            while (reader.hasNext()) {
                names.add(reader.readEntry().getName());
            }
            return names;
        } catch (SearchResultReferenceIOException ignore) {
            return Collections.emptyList();
        }
    }
}
