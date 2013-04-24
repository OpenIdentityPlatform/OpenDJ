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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.extensions;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import java.util.LinkedHashSet;

/**
 * Utility class providing common code for extensions tests.
 */
@SuppressWarnings("javadoc")
class ExtensionTestUtils
{

  public static void testSearchEmptyAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT, filter);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchNoAttrs(DN entryDN, AttributeType attributeType)
      throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add(SchemaConstants.NO_ATTRIBUTES);

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchAllUserAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("*");

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

  public static void testSearchAllOperationalAttrs(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("+");

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(attributeType));
  }

  public static void testSearchAttr(DN entryDN, String attrName,
      AttributeType attributeType) throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add(attrName);

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertTrue(e.hasAttribute(attributeType));
  }

  public static void testSearchExcludeAttr(DN entryDN,
      AttributeType attributeType) throws Exception
  {
    SearchFilter filter =
        SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrList = new LinkedHashSet<String>(1);
    attrList.add("objectClass");

    InternalClientConnection conn =
        InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
        conn.processSearch(entryDN, SearchScope.BASE_OBJECT,
            DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, filter,
            attrList);
    assertEquals(searchOperation.getSearchEntries().size(), 1);

    Entry e = searchOperation.getSearchEntries().get(0);
    assertNotNull(e);
    assertFalse(e.hasAttribute(attributeType));
  }

}
