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

import java.util.List;

/**
 * LDAPProfile Tester.
 */
public class LDAPProfileTest extends DirectoryServerTestCase {

  /**
   * Tests execution of getObjectClasses() and makes sure the
   * returned list contains "top".
   */
  @Test
  public void testGetObjectClasses() {
    LDAPProfile lp = LDAPProfile.getInstance();
    List<String> ocs = lp.getObjectClasses(GlobalCfgDefn.getInstance());
    assertTrue(ocs.contains("top"));
  }

}
