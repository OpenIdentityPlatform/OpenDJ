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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the initialization exception class.
 */
public class InitializationExceptionTestCase
       extends TypesTestCase
{
  /**
   * Tests the first constructor, which takes int and String arguments.
   */
  @Test()
  public void testConstructor1()
  {
    String message = "Test Constructor 1";

    validateException(new InitializationException(1, message), 1, message);
    validateException(new InitializationException(1, ""), 1, "");
    validateException(new InitializationException(1, null), 1, null);

    validateException(new InitializationException(0, message), 0, message);
    validateException(new InitializationException(0, ""), 0, "");
    validateException(new InitializationException(0, null), 0, null);

    validateException(new InitializationException(-1, message), -1, message);
    validateException(new InitializationException(-1, ""), -1, "");
    validateException(new InitializationException(-1, null), -1, null);
  }



  /**
   * Tests the second constructor, which takes int, String, and Throwable
   * arguments.
   */
  @Test()
  public void testConstructor2()
  {
    String    message = "Test Constructor 2";
    Exception e       = new Exception("Test Constructor 2 Exception");

    validateException(new InitializationException(1, message, e), 1, message);
    validateException(new InitializationException(1, "", e), 1, "");
    validateException(new InitializationException(1, null, e), 1, null);

    validateException(new InitializationException(0, message, e), 0, message);
    validateException(new InitializationException(0, "", e), 0, "");
    validateException(new InitializationException(0, null, e), 0, null);

    validateException(new InitializationException(-1, message, e), -1, message);
    validateException(new InitializationException(-1, "", e), -1, "");
    validateException(new InitializationException(-1, null, e), -1, null);

    validateException(new InitializationException(1, message, null), 1,
                      message);
    validateException(new InitializationException(1, "", null), 1, "");
    validateException(new InitializationException(1, null, null), 1, null);

    validateException(new InitializationException(0, message, null), 0,
                      message);
    validateException(new InitializationException(0, "", null), 0, "");
    validateException(new InitializationException(0, null, null), 0, null);

    validateException(new InitializationException(-1, message, null), -1,
                      message);
    validateException(new InitializationException(-1, "", null), -1, "");
    validateException(new InitializationException(-1, null, null), -1, null);
  }



  /**
   * Verifies the contents of the provided initialization exception.
   *
   * @param  ie         The initialization exception to verify.
   * @param  messageID  The expected message ID for the exception.
   * @param  message    The expected message for the exception.
   */
  private void validateException(InitializationException ie, int messageID,
                                 String message)
  {
    assertEquals(ie.getMessageID(), messageID);

    if (message == null)
    {
      assertNull(ie.getMessage());
    }
    else
    {
      assertEquals(ie.getMessage(), message);
    }
  }
}

