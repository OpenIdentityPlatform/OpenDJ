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
 * Copyright 2009 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Core schema tests.
 */
@SuppressWarnings("javadoc")
public class CoreSchemaTest extends AbstractSchemaTestCase {
    @Test
    public final void testCoreSchemaWarnings() {
        // Make sure core schema doesn't have any warnings.
        Assert.assertTrue(Schema.getCoreSchema().getWarnings().isEmpty());
    }
}
