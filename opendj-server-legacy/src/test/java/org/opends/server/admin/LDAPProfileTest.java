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
 *      Portions Copyright 2015 ForgeRock AS.
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
