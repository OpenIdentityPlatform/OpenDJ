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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;



import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * This class tests the Synchronous Connection API.
 */
public class SynchronousConnectionTestCase extends TypesTestCase
{
  private AsynchronousConnection asyncCon;



  /**
   * Ensures that the LDAP Server is running.
   *
   * @throws Exception
   *           If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    final ConnectionFactory factory = Connections.newInternalConnectionFactory(
        LDAPServer.getInstance(), null);
    asyncCon = factory.getAsynchronousConnection(null).get();
  }



  /**
   * Ensures that the LDAP server is stopped.
   */
  @AfterClass()
  public void stopServer()
  {
    asyncCon.close();
    // Don't stop the server as some futures might get stuck.
    // TestCaseUtils.stopServer();
  }



  /**
   * Tests the ADD request.
   *
   * @throws Exception
   */
  @Test()
  public void testAddRequest() throws Exception
  {
    final SynchronousConnection con = new SynchronousConnection(asyncCon);
    final Result result = con.add(Requests.newAddRequest(DN.valueOf(""
        + "uid=syncconnectiontestcase,ou=people,o=test")));
    assertTrue(result.isSuccess());
  }



  /**
   * Tests the BIND request.
   *
   * @throws Exception
   */
  @Test()
  public void testBindRequest() throws Exception
  {
    final SynchronousConnection con = new SynchronousConnection(asyncCon);
    final BindResult result = con.bind(Requests.newSimpleBindRequest());
    assertTrue(result.isSuccess());
  }



  /**
   * Tests the COMPARE request.
   *
   * @throws Exception
   */
  @Test()
  public void testCompareRequest() throws Exception
  {
    final SynchronousConnection con = new SynchronousConnection(asyncCon);
    final CompareResult result = con.compare("uid=user.0,ou=people,o=test",
        "uid", "user.0");
    assertTrue(result.matched());
  }



  /**
   * Tests the ctor.
   *
   * @throws Exception
   */
  @Test()
  public void testCtor() throws Exception
  {
    final SynchronousConnection con = new SynchronousConnection(asyncCon);
    assertFalse(con.isClosed());
  }



  /**
   * Tests the SEARCH request.
   *
   * @throws Exception
   */
  @Test()
  public void testSearchRequest() throws Exception
  {
    final SynchronousConnection con = new SynchronousConnection(asyncCon);
    final ConnectionEntryReader reader = con.search(
        "uid=user.0,ou=people,o=test", SearchScope.BASE_OBJECT,
        "objectclass=*", "cn");
    Assert.assertTrue(reader.hasNext());
    Assert.assertFalse(reader.isReference());
    Assert.assertTrue(reader.hasNext());
    SearchResultEntry entry = reader.readEntry();
    Assert.assertEquals(entry.getName(),
        DN.valueOf("uid=user.0,ou=people,o=test"));
    Assert.assertFalse(reader.hasNext());
    try
    {
      reader.readEntry();
      Assert
          .fail("reader.readEntry() should have thrown NoSuchElementException");
    }
    catch (NoSuchElementException e)
    {
      // This is expected.
    }
    Assert.assertFalse(reader.hasNext());
  }
  // TODO: add more tests.
}
