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
 * Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config;

import static org.testng.Assert.assertNotNull;

import java.util.Locale;

import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class ManagedObjectDefinitionI18NResourceTest extends ConfigTestCase {

    private ManagedObjectDefinitionI18NResource definitionI18NResource;

    @BeforeClass
    public void setUp() {
        definitionI18NResource = ManagedObjectDefinitionI18NResource.getInstanceForProfile("ldap");
    }

    @Test
    public void testGetMessage() {
        // Ideally we should test getting messages with arguments
        // but I couldn't find any existing properties files with
        // args
        assertNotNull(definitionI18NResource.getMessage(GlobalCfgDefn.getInstance(), "objectclass",
                Locale.getDefault()));
    }

}
