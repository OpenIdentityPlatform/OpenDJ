/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
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
  ManagedObjectDefinitionI18NResource modr = null;

  /**
   * Creates the resource
   */
  @BeforeClass
  public void setUp() {
    modr = ManagedObjectDefinitionI18NResource.getInstanceForProfile("ldap");
  }

  /**
   * Tests ability to get a message
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
