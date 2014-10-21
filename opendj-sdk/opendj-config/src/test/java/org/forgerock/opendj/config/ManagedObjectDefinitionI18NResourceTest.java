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
 *      Copyright 2008 Sun Microsystems, Inc.
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
