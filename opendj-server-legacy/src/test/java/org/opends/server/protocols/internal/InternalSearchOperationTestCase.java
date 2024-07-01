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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.internal;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.ldap.DN;
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
    new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(),
        request, new TestInternalSearchListener());
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

