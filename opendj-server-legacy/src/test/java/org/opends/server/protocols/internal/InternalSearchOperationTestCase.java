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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.protocols.internal;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.internal.InternalSearchOperation class.
 */
public class InternalSearchOperationTestCase
       extends InternalTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the first constructor, which takes raw arguments, without providing
   * an internal search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testConstructor1WithoutListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);
  }



  /**
   * Tests the first constructor, which takes raw arguments, with an internal
   * search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testConstructor1WithListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request, new TestInternalSearchListener());
  }



  /**
   * Tests the <CODE>getSearchEntries</CODE> and
   * <CODE>getSearchReferences</CODE> methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testGetSearchEntriesAndReferences() throws Exception
  {
    SearchRequest request = Requests.newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the <CODE>addSearchEntry</CODE> method without a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddSearchEntryWithoutListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);

    Entry e = TestCaseUtils.makeEntry("dn: ",
                                      "objectClass: top",
                                      "objectClass: ds-root-dse");
    searchOperation.addSearchEntry(new SearchResultEntry(e));
  }



  /**
   * Tests the <CODE>addSearchEntry</CODE> method with a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddSearchEntryWithListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = new InternalSearchOperation(
        getRootConnection(), nextOperationID(), nextMessageID(), request, new TestInternalSearchListener());

    Entry e = TestCaseUtils.makeEntry("dn: ",
                                      "objectClass: top",
                                      "objectClass: ds-root-dse");
    searchOperation.addSearchEntry(new SearchResultEntry(e));
  }


  /**
   * Tests the <CODE>addSearchReference</CODE> method without a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddSearchReferenceWithoutListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);

    SearchResultReference reference =
         new SearchResultReference("ldap://server.example.com:389/");
    searchOperation.addSearchReference(reference);
  }



  /**
   * Tests the <CODE>addSearchReference</CODE> method with a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddSearchReferenceWithListener() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.rootDN(), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = new InternalSearchOperation(
        getRootConnection(), nextOperationID(), nextMessageID(), request, new TestInternalSearchListener());

    SearchResultReference reference =
         new SearchResultReference("ldap://server.example.com:389/");
    searchOperation.addSearchReference(reference);
  }
}

