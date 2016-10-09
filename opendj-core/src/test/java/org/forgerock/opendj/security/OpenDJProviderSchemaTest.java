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

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.schema.Schema.getCoreSchema;
import static org.forgerock.opendj.security.OpenDJProviderSchema.addOpenDJProviderSchema;

import org.forgerock.opendj.ldap.SdkTestCase;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class OpenDJProviderSchemaTest extends SdkTestCase {
    @Test
    public void testGetObjectClasses() throws Exception {
        assertThat(OpenDJProviderSchema.getObjectClasses()).isNotEmpty();
    }

    @Test
    public void testGetAttributeTypes() throws Exception {
        assertThat(OpenDJProviderSchema.getAttributeTypes()).isNotEmpty();
    }

    @Test
    public void testAddOpenDJProviderSchema() throws Exception {
        final SchemaBuilder schemaBuilder = new SchemaBuilder(getCoreSchema());
        final Schema schema = addOpenDJProviderSchema(schemaBuilder).toSchema();
        assertThat(schema.getWarnings()).isEmpty();
        for (ObjectClass objectClass : OpenDJProviderSchema.getObjectClasses()) {
            assertThat(schema.hasObjectClass(objectClass.getNameOrOID())).isTrue();
        }
        for (AttributeType attributeType : OpenDJProviderSchema.getAttributeTypes()) {
            assertThat(schema.hasAttributeType(attributeType.getNameOrOID())).isTrue();
        }
    }
}
