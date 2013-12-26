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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.ldap;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyValueVisitor;
import org.opends.server.admin.Reference;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.OperationRejectedException.OperationType;
import org.opends.server.admin.client.spi.AbstractManagedObject;
import org.opends.server.admin.client.spi.Driver;
import org.opends.server.admin.client.spi.Property;
import org.opends.server.admin.client.spi.PropertySet;

/**
 * A managed object bound to an LDAP connection.
 *
 * @param <T>
 *            The type of client configuration represented by the client managed
 *            object.
 */
final class LDAPManagedObject<T extends ConfigurationClient> extends AbstractManagedObject<T> {

    /**
     * A visitor which is used to encode property LDAP values.
     */
    private static final class ValueEncoder extends PropertyValueVisitor<Object, Void> {

        // Prevent instantiation.
        private ValueEncoder() {
            // No implementation required.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <C extends ConfigurationClient, S extends Configuration> Object visitAggregation(
                AggregationPropertyDefinition<C, S> pd, String v, Void p) {
            // Aggregations values are stored as full DNs in LDAP, but
            // just their common name is exposed in the admin framework.
            Reference<C, S> reference = Reference.parseName(pd.getParentPath(), pd.getRelationDefinition(), v);
            return reference.toDN().toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public <P> Object visitUnknown(PropertyDefinition<P> propertyDef, P value, Void p) {
            return propertyDef.encodeValue(value);
        }
    }

    // The LDAP management driver associated with this managed object.
    private final LDAPDriver driver;

    /**
     * Creates a new LDAP managed object instance.
     *
     * @param driver
     *            The underlying LDAP management driver.
     * @param d
     *            The managed object's definition.
     * @param path
     *            The managed object's path.
     * @param properties
     *            The managed object's properties.
     * @param existsOnServer
     *            Indicates whether or not the managed object already exists.
     * @param namingPropertyDefinition
     *            The managed object's naming property definition if there is
     *            one.
     */
    LDAPManagedObject(LDAPDriver driver, ManagedObjectDefinition<T, ? extends Configuration> d,
            ManagedObjectPath<T, ? extends Configuration> path, PropertySet properties, boolean existsOnServer,
            PropertyDefinition<?> namingPropertyDefinition) {
        super(d, path, properties, existsOnServer, namingPropertyDefinition);
        this.driver = driver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addNewManagedObject() throws ErrorResultException, OperationRejectedException,
            ConcurrentModificationException, ManagedObjectAlreadyExistsException {
        // First make sure that the parent managed object still exists.
        ManagedObjectDefinition<?, ?> d = getManagedObjectDefinition();
        ManagedObjectPath<?, ?> path = getManagedObjectPath();
        ManagedObjectPath<?, ?> parent = path.parent();

        try {
            if (!driver.managedObjectExists(parent)) {
                throw new ConcurrentModificationException();
            }
        } catch (ManagedObjectNotFoundException e) {
            throw new ConcurrentModificationException();
        }

        // We may need to create the parent "relation" entry if this is a
        // child of an instantiable or set relation.
        RelationDefinition<?, ?> r = path.getRelationDefinition();
        if (r instanceof InstantiableRelationDefinition || r instanceof SetRelationDefinition) {

            // TODO: this implementation does not handle relations which
            // comprise of more than one RDN arc (this will probably never
            // be required anyway).
            DN dn;
            if (r instanceof InstantiableRelationDefinition) {
                dn = LDAPNameBuilder.create(parent, (InstantiableRelationDefinition) r, driver.getLDAPProfile());
            } else {
                dn = LDAPNameBuilder.create(parent, (SetRelationDefinition) r, driver.getLDAPProfile());
            }

            if (!driver.entryExists(dn)) {
                Entry entry = new LinkedHashMapEntry(dn);

                // Create the branch's object class attribute.
                List<String> objectClasses = driver.getLDAPProfile().getRelationObjectClasses(r);
                addObjectClassesToEntry(objectClasses, entry);

                // Create the branch's naming attribute.
                entry.addAttribute(dn.rdn().getFirstAVA().toAttribute());

                // Create the entry.
                try {
                    driver.getLDAPConnection().createEntry(entry);
                } catch (ErrorResultException e) {
                    if (e.getResult().getResultCode() == ResultCode.UNWILLING_TO_PERFORM) {
                        LocalizableMessage m = LocalizableMessage.raw("%s", e.getLocalizedMessage());
                        throw new OperationRejectedException(OperationType.CREATE, d.getUserFriendlyName(), m);
                    } else {
                        throw e;
                    }
                }
            }
        }

        // Now add the entry representing this new managed object.
        DN dn = LDAPNameBuilder.create(path, driver.getLDAPProfile());
        Entry entry = new LinkedHashMapEntry(dn);

        // Create the object class attribute.
        ManagedObjectDefinition<?, ?> definition = getManagedObjectDefinition();
        List<String> objectClasses = driver.getLDAPProfile().getObjectClasses(definition);
        addObjectClassesToEntry(objectClasses, entry);

        // Create the naming attribute if there is not naming property.
        PropertyDefinition<?> namingPropertyDef = getNamingPropertyDefinition();
        if (namingPropertyDef == null) {
            entry.addAttribute(dn.rdn().getFirstAVA().toAttribute());
        }

        // Create the remaining attributes.
        for (PropertyDefinition<?> propertyDef : definition.getAllPropertyDefinitions()) {
            String attrID = driver.getLDAPProfile().getAttributeName(definition, propertyDef);
            Attribute attribute = new LinkedAttribute(attrID);
            encodeProperty(attribute, propertyDef);
            if (attribute.size() != 0) {
                entry.addAttribute(attribute);
            }
        }

        try {
            // Create the entry.
            driver.getLDAPConnection().createEntry(entry);
        } catch (ErrorResultException e) {
            if (e.getResult().getResultCode() == ResultCode.ENTRY_ALREADY_EXISTS) {
                throw new ManagedObjectAlreadyExistsException();
            } else if (e.getResult().getResultCode() == ResultCode.UNWILLING_TO_PERFORM) {
                LocalizableMessage m = LocalizableMessage.raw("%s", e.getLocalizedMessage());
                throw new OperationRejectedException(OperationType.CREATE, d.getUserFriendlyName(), m);
            } else {
                throw e;
            }
        }
    }

    private void addObjectClassesToEntry(List<String> objectClasses, Entry entry) {
        for (String objectClass : objectClasses) {
            Attribute attr = new LinkedAttribute("objectClass");
            attr.add(ByteString.valueOf(objectClass));
            entry.addAttribute(attr);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Driver getDriver() {
        return driver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyExistingManagedObject() throws ConcurrentModificationException, OperationRejectedException,
            ErrorResultException {
        // Build the modify request
        ManagedObjectPath<?, ?> path = getManagedObjectPath();
        DN dn = LDAPNameBuilder.create(path, driver.getLDAPProfile());
        ModifyRequest request = Requests.newModifyRequest(dn);
        ManagedObjectDefinition<?, ?> d = getManagedObjectDefinition();
        for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
            Property<?> p = getProperty(pd);
            if (p.isModified()) {
                String attrID = driver.getLDAPProfile().getAttributeName(d, pd);
                Attribute attribute = new LinkedAttribute(attrID);
                encodeProperty(attribute, pd);
                request.addModification(ModificationType.REPLACE, attrID,
                        attribute.toArray(new Object[attribute.size()]));
            }
        }

        // Perform the LDAP modification if something has changed.
        if (!request.getModifications().isEmpty()) {
            try {
                driver.getLDAPConnection().modifyEntry(request);
            } catch (ErrorResultException e) {
                if (e.getResult().getResultCode() == ResultCode.UNWILLING_TO_PERFORM) {
                    LocalizableMessage m = LocalizableMessage.raw("%s", e.getLocalizedMessage());
                    throw new OperationRejectedException(OperationType.CREATE, d.getUserFriendlyName(), m);
                } else {
                    throw e;
                }
            }

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <M extends ConfigurationClient> ManagedObject<M> newInstance(ManagedObjectDefinition<M, ?> d,
            ManagedObjectPath<M, ?> path, PropertySet properties, boolean existsOnServer,
            PropertyDefinition<?> namingPropertyDefinition) {
        return new LDAPManagedObject<M>(driver, d, path, properties, existsOnServer, namingPropertyDefinition);
    }

    // Encode a property into LDAP string values.
    private <P> void encodeProperty(Attribute attribute, PropertyDefinition<P> propertyDef) {
        PropertyValueVisitor<Object, Void> visitor = new ValueEncoder();
        Property<P> property = getProperty(propertyDef);
        if (propertyDef.hasOption(PropertyOption.MANDATORY)) {
            // For mandatory properties we fall-back to the default values
            // if defined which can sometimes be the case e.g when a
            // mandatory property is overridden.
            for (P value : property.getEffectiveValues()) {
                attribute.add(propertyDef.accept(visitor, value, null));
            }
        } else {
            for (P value : property.getPendingValues()) {
                attribute.add(propertyDef.accept(visitor, value, null));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isModified() {
        ManagedObjectDefinition<?, ?> d = getManagedObjectDefinition();
        for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
            Property<?> p = getProperty(pd);
            if (p.isModified()) {
                return true;
            }
        }
        return false;
    }

}
