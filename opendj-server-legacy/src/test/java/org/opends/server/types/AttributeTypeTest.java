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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.types;

import static org.opends.server.core.DirectoryServer.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Test for AttributeType */
public class AttributeTypeTest extends DirectoryServerTestCase
{
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startFakeServer();
  }

  @AfterClass
  public void tearDown() throws Exception
  {
    TestCaseUtils.shutdownFakeServer();
  }

  @Test
  public void defaultAttributeTypesWithDifferentCaseEquals()
  {
    AttributeType attrType = getAttributeTypeOrDefault("displayName");
    AttributeType attrType2 = getAttributeTypeOrDefault("displayname");
    Assert.assertNotSame(attrType, attrType2);
    Assert.assertEquals(attrType, attrType2);
  }
}
