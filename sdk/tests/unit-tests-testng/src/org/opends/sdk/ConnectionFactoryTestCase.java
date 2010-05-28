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

package org.opends.sdk;



import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Tests the connectionfactory classes.
 */
public abstract class ConnectionFactoryTestCase extends SdkTestCase
{
  class MyResultHandler implements ResultHandler<AsynchronousConnection>
  {
    // latch.
    private final CountDownLatch latch;
    // invalid flag.
    private volatile boolean invalid;



    MyResultHandler(final CountDownLatch latch)
    {
      this.latch = latch;
    }



    public void handleErrorResult(final ErrorResultException error)
    {
      // came here.
      invalid = true;
      latch.countDown();
    }



    public void handleResult(final AsynchronousConnection con)
    {
      //
      latch.countDown();
    }
  }



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
  }



  /**
   * Tests the async connection in the blocking mode. This is not fully async as
   * it blocks on the future.
   *
   * @throws Exception
   */
  @Test()
  public void testBlockingFutureNoHandler() throws Exception
  {
    final FutureResult<AsynchronousConnection> future = getAsynchronousConnection(null);
    final AsynchronousConnection con = future.get();
    // quickly check if iit is a valid connection.
    // Don't use a result handler.
    assertNotNull(con.readRootDSE(null).get());
    con.close();
  }



  /**
   * Tests the non-blocking fully async connection using a handler.
   *
   * @throws Exception
   */
  @Test()
  public void testNonBlockingFutureWithHandler() throws Exception
  {
    // Use the handler to get the result asynchronously.
    final CountDownLatch latch = new CountDownLatch(1);
    final MyResultHandler handler = new MyResultHandler(latch);
    final FutureResult<AsynchronousConnection> future = getAsynchronousConnection(handler);
    // Since we don't have anything to do, we would rather
    // be notified by the latch when the other thread calls our handler.
    latch.await(); // should do a timed wait rather?
    if (handler.invalid)
    {
      // There was an error.
      throw new Exception();
    }
  }



  /**
   * Tests the synchronous connection.
   *
   * @throws Exception
   */
  @Test()
  public void testSynchronousConnection() throws Exception
  {
    final Connection con = getConnection();
    assertNotNull(con);
    // quickly check if iit is a valid connection.
    assertTrue(con.readRootDSE().getEntry().getName().isRootDN());
    con.close();
  }



  /**
   * Gets the future result from the implementations.
   *
   * @return FutureResult.
   */
  protected abstract FutureResult<AsynchronousConnection> getAsynchronousConnection(
      ResultHandler<AsynchronousConnection> handler) throws Exception;



  /**
   * Gets the connection from the implementations.
   *
   * @return Connection
   */
  protected abstract Connection getConnection() throws Exception;
}
