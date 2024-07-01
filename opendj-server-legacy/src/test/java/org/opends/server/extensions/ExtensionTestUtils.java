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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.schema.SchemaConstants;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Entry;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/** Utility class providing common code for extensions tests. */
@SuppressWarnings("javadoc")
class ExtensionTestUtils
{

  public static void testSearchEmptyAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT);
    assertAttributeFound(request, attributeType, false);
  }

  public static void testSearchNoAttrs(DN entryDN, AttributeType attributeType)
      throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT)
        .addAttribute(SchemaConstants.NO_ATTRIBUTES);
    assertAttributeFound(request, attributeType, false);
  }

  public static void testSearchAllUserAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT).addAttribute("*");
    assertAttributeFound(request, attributeType, false);
  }

  public static void testSearchAllOperationalAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT).addAttribute("+");
    assertAttributeFound(request, attributeType, true);
  }

  public static void testSearchAttr(DN entryDN, String attrName,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT).addAttribute(attrName);
    assertAttributeFound(request, attributeType, true);
  }

  public static void testSearchExcludeAttr(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    final SearchRequest request = newSearchRequest(entryDN, SearchScope.BASE_OBJECT).addAttribute("objectClass");
    assertAttributeFound(request, attributeType, false);
  }

  private static void assertAttributeFound(final SearchRequest request, AttributeType attributeType, boolean expected)
  {
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertEquals(e.hasAttribute(attributeType), expected);
  }

}
