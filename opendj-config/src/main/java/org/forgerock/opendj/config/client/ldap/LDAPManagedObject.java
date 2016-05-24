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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config.client.ldap;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.AggregationPropertyDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectAlreadyExistsException;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyOption;
import org.forgerock.opendj.config.PropertyValueVisitor;
import org.forgerock.opendj.config.Reference;
import org.forgerock.opendj.config.RelationDefinition;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.client.ConcurrentModificationException;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.OperationRejectedException;
import org.forgerock.opendj.config.client.OperationRejectedException.OperationType;
import org.forgerock.opendj.config.client.spi.AbstractManagedObject;
import org.forgerock.opendj.config.client.spi.Driver;
import org.forgerock.opendj.config.client.spi.Property;
import org.forgerock.opendj.config.client.spi.PropertySet;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;

/**
 * A managed object bound to an LDAP connection.
 *
 * @param <T>
 *            The type of client configuration represented by the client managed
 *            object.
 */
final class LDAPManagedObject<T extends ConfigurationClient> extends AbstractManagedObject<T> {

    /** A visitor which is used to encode property LDAP values. */
    private static final class ValueEncoder extends PropertyValueVisitor<Object, Void> {

        /** Prevent instantiation. */
        private ValueEncoder() {
            // No implementation required.
        }

        @Override
        public <C extends ConfigurationClient, S extends Configuration> Object visitAggregation(
                AggregationPropertyDefinition<C, S> pd, String v, Void p) {
            // Aggregations values are stored as full DNs in LDAP, but
            // just their common name is exposed in the admin framework.
            Reference<C, S> reference = Reference.parseName(pd.getParentPath(), pd.getRelationDefinition(), v);
            return reference.toDN().toString();
        }

        @Override
        public <P> Object visitUnknown(PropertyDefinition<P> propertyDef, P value, Void p) {
            return propertyDef.encodeValue(value);
        }
    }

    /** The LDAP management driver associated with this managed object. */
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
     *            Indicates whether the managed object already exists.
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

    @Override
    protected void addNewManagedObject() throws LdapException, OperationRejectedException,
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
                dn = DNBuilder.create(parent, (InstantiableRelationDefinition<?, ?>) r,
                        driver.getLDAPProfile());
            } else {
                dn = DNBuilder.create(parent, (SetRelationDefinition<?, ?>) r,
                        driver.getLDAPProfile());
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
                    driver.getLDAPConnection().add(entry);
                } catch (LdapException e) {
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
        DN dn = DNBuilder.create(path, driver.getLDAPProfile());
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
            driver.getLDAPConnection().add(entry);
        } catch (LdapException e) {
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
            attr.add(ByteString.valueOfUtf8(objectClass));
            entry.addAttribute(attr);
        }
    }

    @Override
    protected Driver getDriver() {
        return driver;
    }

    @Override
    protected void modifyExistingManagedObject() throws ConcurrentModificationException, OperationRejectedException,
            LdapException {
        // Build the modify request
        ManagedObjectPath<?, ?> path = getManagedObjectPath();
        DN dn = DNBuilder.create(path, driver.getLDAPProfile());
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
                driver.getLDAPConnection().modify(request);
            } catch (LdapException e) {
                if (e.getResult().getResultCode() == ResultCode.UNWILLING_TO_PERFORM) {
                    LocalizableMessage m = LocalizableMessage.raw("%s", e.getLocalizedMessage());
                    throw new OperationRejectedException(OperationType.MODIFY, d.getUserFriendlyName(), m);
                } else {
                    throw e;
                }
            }

        }
    }

    @Override
    protected <M extends ConfigurationClient> ManagedObject<M> newInstance(ManagedObjectDefinition<M, ?> d,
            ManagedObjectPath<M, ?> path, PropertySet properties, boolean existsOnServer,
            PropertyDefinition<?> namingPropertyDefinition) {
        return new LDAPManagedObject<>(driver, d, path, properties, existsOnServer, namingPropertyDefinition);
    }

    /** Encode a property into LDAP string values. */
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

    @Override
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
