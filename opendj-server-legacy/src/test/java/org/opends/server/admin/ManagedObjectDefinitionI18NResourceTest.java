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
 * Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.server.admin;

import static org.testng.Assert.*;
import org.testng.annotations.*;
import org.opends.server.admin.std.meta.GlobalCfgDefn;
import org.opends.server.DirectoryServerTestCase;

import java.util.Locale;

/**
 * ManagedObjectDefinitionI18NResource Tester.
 */
public class ManagedObjectDefinitionI18NResourceTest extends DirectoryServerTestCase {
  ManagedObjectDefinitionI18NResource modr;

  /**
   * Creates the resource.
   */
  @BeforeClass
  public void setUp() {
    modr = ManagedObjectDefinitionI18NResource.getInstanceForProfile("ldap");
  }

  /**
   * Tests ability to get a message.
   */
  @Test
  public void testGetMessage() {

    // Ideally we should test getting messages with arguments
    // but I couldn't find any existing properties files with
    // args

    assertNotNull(modr.getMessage(
            GlobalCfgDefn.getInstance(),
            "objectclass",
            Locale.getDefault()));
  }

}
