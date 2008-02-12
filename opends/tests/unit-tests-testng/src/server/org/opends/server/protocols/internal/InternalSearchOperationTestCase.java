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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;


import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

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
  @BeforeClass()
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
  @Test()
  public void testConstructor1WithoutListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    new InternalSearchOperation(conn, conn.nextOperationID(),
                                conn.nextMessageID(), new ArrayList<Control>(),
                                new ASN1OctetString(), SearchScope.BASE_OBJECT,
                                DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                                false, LDAPFilter.decode("(objectClass=*)"),
                                new LinkedHashSet<String>(), null);
  }



  /**
   * Tests the first constructor, which takes raw arguments, with an internal
   * search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor1WithListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    new InternalSearchOperation(conn, conn.nextOperationID(),
                                conn.nextMessageID(), new ArrayList<Control>(),
                                new ASN1OctetString(), SearchScope.BASE_OBJECT,
                                DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                                false, LDAPFilter.decode("(objectClass=*)"),
                                new LinkedHashSet<String>(),
                                new TestInternalSearchListener());
  }



  /**
   * Tests the second constructor, which takes processed arguments, without
   * providing an internal search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor2WithoutListener()
         throws Exception
  {
    SearchFilter searchFilter =
         SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    new InternalSearchOperation(conn, conn.nextOperationID(),
                                conn.nextMessageID(), new ArrayList<Control>(),
                                DN.nullDN(), SearchScope.BASE_OBJECT,
                                DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                                false, searchFilter,
                                new LinkedHashSet<String>(), null);
  }



  /**
   * Tests the second constructor, which takes processed arguments, with an
   * internal search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testConstructor2WithListener()
         throws Exception
  {
    SearchFilter searchFilter =
         SearchFilter.createFilterFromString("(objectClass=*)");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    new InternalSearchOperation(conn, conn.nextOperationID(),
                                conn.nextMessageID(), new ArrayList<Control>(),
                                DN.nullDN(), SearchScope.BASE_OBJECT,
                                DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                                false, searchFilter,
                                new LinkedHashSet<String>(),
                                new TestInternalSearchListener());
  }



  /**
   * Tests the <CODE>getSearchEntries</CODE> and
   * <CODE>getSearchReferences</CODE> methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetSearchEntriesAndReferences()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch(new ASN1OctetString(""), SearchScope.BASE_OBJECT,
                            LDAPFilter.decode("(objectClass=*)"));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(searchOperation.getSearchEntries().isEmpty());
    assertTrue(searchOperation.getSearchReferences().isEmpty());
  }



  /**
   * Tests the <CODE>addSearchEntry</CODE> method without a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddSearchEntryWithoutListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(),
                                     new ArrayList<Control>(),
                                     new ASN1OctetString(),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(objectClass=*)"),
                                     new LinkedHashSet<String>(), null);

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
  @Test()
  public void testAddSearchEntryWithListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(),
                                     new ArrayList<Control>(),
                                     new ASN1OctetString(),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(objectClass=*)"),
                                     new LinkedHashSet<String>(),
                                     new TestInternalSearchListener());

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
  @Test()
  public void testAddSearchReferenceWithoutListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(),
                                     new ArrayList<Control>(),
                                     new ASN1OctetString(),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(objectClass=*)"),
                                     new LinkedHashSet<String>(), null);

    SearchResultReference reference =
         new SearchResultReference("ldap://server.example.com:389/");
    searchOperation.addSearchReference(reference);
  }



  /**
   * Tests the <CODE>addSearchReference</CODE> method with a search listener.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddSearchReferenceWithListener()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(),
                                     new ArrayList<Control>(),
                                     new ASN1OctetString(),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false,
                                     LDAPFilter.decode("(objectClass=*)"),
                                     new LinkedHashSet<String>(),
                                     new TestInternalSearchListener());

    SearchResultReference reference =
         new SearchResultReference("ldap://server.example.com:389/");
    searchOperation.addSearchReference(reference);
  }
}

