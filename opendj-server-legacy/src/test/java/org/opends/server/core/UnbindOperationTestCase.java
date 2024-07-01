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
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.CancelRequest;
import org.opends.server.types.Control;
import org.opends.server.types.Operation;
import org.forgerock.opendj.ldap.ResultCode;
import org.testng.annotations.Test;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for unbind operations.
 */
public class UnbindOperationTestCase extends OperationTestCase
{

  /** {@inheritDoc} */
  @Override
  protected Operation[] createTestOperations()
         throws Exception
  {
    final InternalClientConnection conn = getRootConnection();
    return new Operation[]
    {
      new UnbindOperationBasis(conn, nextOperationID(), nextMessageID(), null),
      new UnbindOperationBasis(conn, nextOperationID(), nextMessageID(), new ArrayList<Control>())
    };
  }

  /**
   * Invokes a number of operation methods on the provided unbind operation for
   * which all processing has been completed.
   *
   * @param  unbindOperation  The operation to be tested.
   */
  private void examineCompletedOperation(UnbindOperation unbindOperation)
  {
    assertTrue(unbindOperation.getProcessingStartTime() > 0);
    assertTrue(unbindOperation.getProcessingStopTime() > 0);
    assertTrue(unbindOperation.getProcessingTime() >= 0);
  }

  /**
   * Attempts an internal unbind operation.  This won't actually do anything,
   * since there's nothing to disconnect with an internal connection, but it
   * will at least exercise the code path.
   */
  @Test
  public void testUnbindInternal()
  {
    InvocationCounterPlugin.resetAllCounters();
    final InternalClientConnection conn = getRootConnection();

    UnbindOperationBasis unbindOperation = new UnbindOperationBasis(
        conn, nextOperationID(), nextMessageID(), new ArrayList<Control>());
    unbindOperation.run();
    examineCompletedOperation(unbindOperation);

//    assertTrue(InvocationCounterPlugin.getPreParseCount() > 0);
//    assertTrue(InvocationCounterPlugin.getPostOperationCount() > 0);
  }

  /**
   * Tests the <CODE>cancel</CODE> method to ensure that it indicates that the
   * operation cannot be cancelled.
   */
  @Test
  public void testCancel()
  {
    final InternalClientConnection conn = getRootConnection();
    CancelRequest cancelRequest =
         new CancelRequest(false, LocalizableMessage.raw("Test Unbind Cancel"));

    UnbindOperationBasis unbindOperation = new UnbindOperationBasis(
        conn, nextOperationID(), nextMessageID(), new ArrayList<Control>());
    assertEquals(unbindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
  }

  /**
   * Tests the <CODE>getCancelRequest</CODE> method to ensure that it always
   * returns <CODE>null</CODE>.
   */
  @Test
  public void testGetCancelRequest()
  {
    final InternalClientConnection conn = getRootConnection();
    CancelRequest cancelRequest =
         new CancelRequest(false, LocalizableMessage.raw("Test Unbind Cancel"));

    UnbindOperationBasis unbindOperation = new UnbindOperationBasis(
        conn, nextOperationID(), nextMessageID(), new ArrayList<Control>());
    assertNull(unbindOperation.getCancelRequest());

    assertEquals(unbindOperation.cancel(cancelRequest).getResultCode(),
                 ResultCode.CANNOT_CANCEL);
    assertNull(unbindOperation.getCancelRequest());
  }
}

