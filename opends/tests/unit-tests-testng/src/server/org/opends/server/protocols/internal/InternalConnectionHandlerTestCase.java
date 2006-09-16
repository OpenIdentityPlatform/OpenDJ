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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.internal;



import java.util.Collection;

import org.testng.annotations.Test;

import org.opends.server.api.ClientConnection;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.internal.InternalConnectionHandler class.
 */
public class InternalConnectionHandlerTestCase
       extends InternalTestCase
{
  /**
   * Retrieves an instance of the connection handler and initializes it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetInstanceAndInitialize()
         throws Exception
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    handler.initializeConnectionHandler(null);
  }



  /**
   * Tests the <CODE>finalizeConnectionHandler</CODE> method.
   */
  @Test()
  public void testFinalizeConnectionHandler()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    handler.finalizeConnectionHandler(null, false);
  }



  /**
   * Tests the <CODE>getClientConnections</CODE> method.
   */
  @Test()
  public void testGetClientConnections()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    Collection<ClientConnection> connections = handler.getClientConnections();
    assertNotNull(connections);
    assertTrue(connections.isEmpty());
  }



  /**
   * Tests the <CODE>run</CODE> method.  This will make sure that it returns as
   * expected rather than actually running as a thread.
   */
  @Test()
  public void testRun()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    handler.run();
  }



  /**
   * Tests the first <CODE>toString</CODE> method, which doesn't take any
   * arguments.
   */
  @Test()
  public void testToString1()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    String s = handler.toString();
    assertNotNull(s);
    assertFalse(s.equals(""));
  }



  /**
   * Tests the second <CODE>toString</CODE> method, which takes a
   * <CODE>StringBuilder</CODE> argument.
   */
  @Test()
  public void testToString2()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    StringBuilder buffer = new StringBuilder();
    handler.toString(buffer);
    assertFalse(buffer.toString().equals(""));
  }
}

