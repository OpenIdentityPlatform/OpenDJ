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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.admin.client.cli;

import static org.testng.Assert.*;

import org.testng.annotations.*;
import org.opends.admin.ads.ADSContext.ServerGroupProperty;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.SubCommandArgumentParser;

import com.sleepycat.je.dbi.GetMode;

import static org.opends.server.messages.AdminMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;


/**
 * PropertySet Tester.
 */
public class CliTest {


  /**
   * Initialization
   */
  @BeforeClass
  public void setUp()
  {
  }

  /**
   * Verify that returncode are registered with declared MesssageId
   */
  @Test
  public void testReturnCodeMessageId()
  {
    String invalidMsgId;
    String msg;
    int messageID;
    for (DsFrameworkCliReturnCode returnCode : DsFrameworkCliReturnCode
        .values())
    {
      if ((messageID = returnCode.getMessageId()) == MSGID_ADMIN_NO_MESSAGE)
      {
        continue;
      }
      invalidMsgId = "Unknown message for message ID " + messageID;
      // get the associated Msg
      msg = getMessage(messageID);
      if (msg.equals(invalidMsgId))
      {
        assertTrue(false, "ReturnCode \"" + returnCode
            + "\" is registered with a wrong  message Id (" + messageID + ")");
      }
    }
  }

  
  /**
   * Test if ADS errors are all associated with returncode.
   */
  @Test
  public void testCheckRegisterAdsError()
  {
    for (ErrorType error: ErrorType.values())
    {
      assertNotNull(DsFrameworkCliReturnCode.getReturncodeFromAdsError(error),
          "ErrorType." + error + " is not associated with a return code");
    }
  }

  /**
   * Test if ADS errors are all associated with returncode.
   */
  @Test
  public void testServerGroupAttributeDisplayName()
  {
    DsFrameworkCliServerGroup cli = new DsFrameworkCliServerGroup();
    
    try
    {
      cli.initializeCliGroup(new DsFrameworkCliParser(null, null, false),
          null);
    }
    catch (ArgumentException e)
    {
      assertTrue(false, e.getMessage());
    }
    for (ServerGroupProperty prop : ServerGroupProperty.values())
    {
      assertNotNull(cli.getAttributeDisplayName(prop), "ServerGroupProperty."
          + prop.toString() + " has no display value");
    }
  }
 

}

