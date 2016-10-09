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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.security;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static org.forgerock.opendj.ldap.schema.Schema.getCoreSchema;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldif.LDIFEntryReader;

/** Utility methods for accessing the LDAP schema elements required in order to support the OpenDJ security provider. */
public final class OpenDJProviderSchema {
    // Minimal schema for required for interacting with the LDAP key store.
    private static final URL SCHEMA_LDIF_URL = OpenDJProviderSchema.class.getResource("03-keystore.ldif");
    static final Schema SCHEMA;
    private static final Set<ObjectClass> OBJECT_CLASSES;
    private static final Set<AttributeType> ATTRIBUTE_TYPES;
    // Object classes.
    static final String OC_KEY_STORE_OBJECT = "ds-keystore-object";
    static final String OC_TRUSTED_CERTIFICATE = "ds-keystore-trusted-certificate";
    static final String OC_PRIVATE_KEY = "ds-keystore-private-key";
    static final String OC_SECRET_KEY = "ds-keystore-secret-key";
    // Attribute types.
    static final String ATTR_ALIAS = "ds-keystore-alias";
    static final String ATTR_KEY_ALGORITHM = "ds-keystore-key-algorithm";
    static final String ATTR_KEY = "ds-keystore-key";
    static final String ATTR_CERTIFICATE_CHAIN = "ds-keystore-certificate-chain";
    private static final String ATTR_CERTIFICATE = "ds-keystore-certificate";
    static final String ATTR_CERTIFICATE_BINARY = ATTR_CERTIFICATE + ";binary";
    // Standard attribute types.
    static final String ATTR_OBJECT_CLASS = "objectClass";
    static final String ATTR_MODIFY_TIME_STAMP = "modifyTimeStamp";
    static final String ATTR_CREATE_TIME_STAMP = "createTimeStamp";

    static {
        try (final InputStream inputStream = SCHEMA_LDIF_URL.openStream();
             final LDIFEntryReader reader = new LDIFEntryReader(inputStream)) {
            SCHEMA = new SchemaBuilder(getCoreSchema())
                    .addSchema(reader.readEntry(), false)
                    .toSchema()
                    .asNonStrictSchema();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        OBJECT_CLASSES = unmodifiableSet(new LinkedHashSet<>(asList(SCHEMA.getObjectClass(OC_KEY_STORE_OBJECT),
                                                                    SCHEMA.getObjectClass(OC_TRUSTED_CERTIFICATE),
                                                                    SCHEMA.getObjectClass(OC_SECRET_KEY),
                                                                    SCHEMA.getObjectClass(OC_PRIVATE_KEY))));

        ATTRIBUTE_TYPES = unmodifiableSet(new LinkedHashSet<>(asList(SCHEMA.getAttributeType(ATTR_ALIAS),
                                                                     SCHEMA.getAttributeType(ATTR_KEY_ALGORITHM),
                                                                     SCHEMA.getAttributeType(ATTR_KEY),
                                                                     SCHEMA.getAttributeType(ATTR_CERTIFICATE_CHAIN),
                                                                     SCHEMA.getAttributeType(ATTR_CERTIFICATE))));
    }

    /**
     * Returns the set of LDAP object classes required in order to support the OpenDJ security provider.
     *
     * @return The set of LDAP object classes required in order to support the OpenDJ security provider.
     */
    public static Set<ObjectClass> getObjectClasses() {
        return OBJECT_CLASSES;
    }

    /**
     * Returns the set of LDAP attribute types required in order to support the OpenDJ security provider.
     *
     * @return The set of LDAP attribute types required in order to support the OpenDJ security provider.
     */
    public static Set<AttributeType> getAttributeTypes() {
        return ATTRIBUTE_TYPES;
    }

    /**
     * Returns a URL referencing a resource containing the LDIF schema that is required in order to support the
     * OpenDJ security provider.
     *
     * @return The URL referencing the LDIF schema.
     */
    public static URL getSchemaLDIFResource() {
        return SCHEMA_LDIF_URL;
    }

    /**
     * Adds the schema elements required by the OpenDJ security provider to the provided schema builder.
     *
     * @param builder
     *         The schema builder to which the schema elements should be added.
     * @return The schema builder.
     */
    public static SchemaBuilder addOpenDJProviderSchema(final SchemaBuilder builder) {
        for (final AttributeType attributeType : ATTRIBUTE_TYPES) {
            builder.buildAttributeType(attributeType).addToSchema();
        }
        for (final ObjectClass objectClass : OBJECT_CLASSES) {
            builder.buildObjectClass(objectClass).addToSchema();
        }
        return builder;
    }

    private OpenDJProviderSchema() {
        // Prevent instantiation.
    }
}
