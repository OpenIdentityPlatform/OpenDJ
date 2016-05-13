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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ObjectClassTestCase extends AbstractSchemaTestCase {

    @Test
    public void extensibleObjectShouldNotAcceptPlaceholderAttribute() {
        Schema schema = getCoreSchema();
        ObjectClass extensibleObject = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        AttributeType attributeType = schema.getAttributeType("dummy");
        assertThat(attributeType.isPlaceHolder()).isTrue();
        assertThat(extensibleObject.isRequired(attributeType)).isFalse();
        assertThat(extensibleObject.isOptional(attributeType)).isFalse();
        assertThat(extensibleObject.isRequiredOrOptional(attributeType)).isFalse();
    }

    @Test
    public void extensibleObjectShouldAcceptAnyValidAttribute() {
        Schema schema = getCoreSchema();
        ObjectClass extensibleObject = schema.getObjectClass(EXTENSIBLE_OBJECT_OBJECTCLASS_OID);

        AttributeType cn = schema.getAttributeType("cn");
        assertThat(cn.isPlaceHolder()).isFalse();
        assertThat(extensibleObject.isRequired(cn)).isFalse();
        assertThat(extensibleObject.isOptional(cn)).isTrue();
        assertThat(extensibleObject.isRequiredOrOptional(cn)).isTrue();
    }
}
