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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.extensions;

import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import static org.opends.server.protocols.internal.Requests.*;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

/**
 * Utility class providing common code for extensions tests.
 */
@SuppressWarnings("javadoc")
class ExtensionTestUtils
{

  public static void testSearchEmptyAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchNoAttrs(DN entryDN, AttributeType attributeType)
      throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)")
            .addAttribute(SchemaConstants.NO_ATTRIBUTES);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchAllUserAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute("*");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchAllOperationalAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute("+");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(attributeType));
  }

  public static void testSearchAttr(DN entryDN, String attrName,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute(attrName);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(attributeType));
  }

  public static void testSearchExcludeAttr(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request =
        newSearchRequest(entryDN, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute("objectClass");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

}
