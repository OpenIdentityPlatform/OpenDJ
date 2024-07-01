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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.protocols.internal;



import static org.mockito.Mockito.mock;

import java.util.Collection;

import org.testng.annotations.Test;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.ServerContext;

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
  @Test
  public void testGetInstanceAndInitialize()
         throws Exception
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    handler.initializeConnectionHandler(mock(ServerContext.class), null);
  }



  /**
   * Tests the <CODE>finalizeConnectionHandler</CODE> method.
   */
  @Test
  public void testFinalizeConnectionHandler()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    handler.finalizeConnectionHandler(null);
  }



  /**
   * Tests the <CODE>getClientConnections</CODE> method.
   */
  @Test
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
  @Test
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
  @Test
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
  @Test
  public void testToString2()
  {
    InternalConnectionHandler handler = InternalConnectionHandler.getInstance();
    assertNotNull(handler);

    StringBuilder buffer = new StringBuilder();
    handler.toString(buffer);
    assertFalse(buffer.toString().equals(""));
  }
}

