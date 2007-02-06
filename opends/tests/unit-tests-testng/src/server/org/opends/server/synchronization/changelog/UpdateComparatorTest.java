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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.changelog;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.synchronization.protocol.OperationContext.SYNCHROCONTEXT;
import static org.testng.Assert.*;


import org.opends.server.core.DeleteOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.SynchronizationTestCase;
import org.opends.server.synchronization.changelog.UpdateComparator;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.protocol.DeleteContext;
import org.opends.server.synchronization.protocol.DeleteMsg;
import org.opends.server.synchronization.protocol.UpdateMessage;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.util.TimeThread;



/**
 * Test ChangeNumber and ChangeNumberGenerator
 */
public class UpdateComparatorTest extends SynchronizationTestCase
{

  /**
   * Create Update Message Data
   */
  @DataProvider(name = "updateMessageData")
  public Object[][] createUpdateMessageData() {

    ChangeNumber cn1 = new ChangeNumber(1, (short) 0, (short) 1);
    ChangeNumber cn2 = new ChangeNumber(TimeThread.getTime(),
                                       (short) 123, (short) 45);

    //
    // Create the update messgae
    InternalClientConnection connection =
        InternalClientConnection.getRootConnection();
    DeleteOperation op = null;
    try
    {
      op = new DeleteOperation(connection, 1, 1,null,
                                               DN.decode("dc=com"));
    }
    catch (DirectoryException e)
    {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn1, "uniqueid 1"));
    DeleteMsg msg1 = new DeleteMsg(op);
    op.setAttachment(SYNCHROCONTEXT, new DeleteContext(cn2, "uniqueid 2"));
    DeleteMsg msg2 = new DeleteMsg(op);


    return new Object[][] {
       {msg1, msg1, 0},
       {msg1, msg2, -1},
       {msg2, msg1, 1},
       {msg2, msg2, 0}
    };
  }

  /**
   * Test the comparator
   */
  @Test(dataProvider = "updateMessageData")
  public void updateMessageTest(
      UpdateMessage msg1, UpdateMessage msg2, int expected) throws Exception
  {
    assertEquals(UpdateComparator.comparator.compare(msg1, msg2), expected);
  }
}
