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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.schema;

import java.util.List;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.SearchResultEntry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This Test Class tests various matching rules for their compatibility
 * against RFC 4517,4518 and 3454.
 */
@SuppressWarnings("javadoc")
public final class StringPrepProfileTestCase extends SchemaTestCase
{
  /**
   * Ensures that the Directory Server is running before executing the
   * testcases.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /** Adds an entry for test. */
  private void addEntry() throws Exception
  {
    TestCaseUtils.clearJEBackend("userRoot", "dc=example,dc=com");
    TestCaseUtils.addEntries(
      "dn: cn=Jos\u00E9,dc=example,   dc=com",
            "objectClass: person",
            "cn: Jos\u00E9", // the value is precomposed.
            "sn: This\u00AD\u180D\uFE00\u0085\u0085is\u202A\u2028a\u0020test"
    );
  }

  /**
   * Tests the stringprep algorithm by adding an entry containing precomposed
   * DN and searching for it using the filter containing a combining sequence
   * and acute accent.
   */
  @Test
  public void testUnicodeSearch() throws Exception
  {
    try
    {
      addEntry();

      SearchRequest request = newSearchRequest(
          "dc=  example,dc=com",
          SearchScope.WHOLE_SUBTREE, "&(cn=Jos\u0065\u0301)(sn=This is a test)");
      InternalSearchOperation searchOperation = getRootConnection().processSearch(request);

      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
      List<SearchResultEntry> entries = searchOperation.getSearchEntries();
      assertEquals(entries.size(), 1);
    }
    finally
    {
      TestCaseUtils.clearJEBackend("userRoot", "dc=example,dc=com");
    }
  }
}
