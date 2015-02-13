/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import org.testng.annotations.Test;

import org.forgerock.i18n.LocalizableMessage;

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
  @Test
  public void testConstructor1()
  {
    LocalizableMessage message = LocalizableMessage.raw("Test Constructor 1");

    validateException(new InitializationException(message), message);
    validateException(new InitializationException(null), null);
  }



  /**
   * Tests the second constructor, which takes int, String, and Throwable
   * arguments.
   */
  @Test
  public void testConstructor2()
  {
    LocalizableMessage   message = LocalizableMessage.raw("Test Constructor 2");
    Exception e       = new Exception("Test Constructor 2 Exception");

    validateException(new InitializationException(message, e), message);
    validateException(new InitializationException(null, e), null);

    validateException(new InitializationException(message, null),
                      message);
    validateException(new InitializationException(null, null), null);
  }



  /**
   * Verifies the contents of the provided initialization exception.
   *
   * @param  ie         The initialization exception to verify.
   * @param  message    The expected message for the exception.
   */
  private void validateException(InitializationException ie,
                                 LocalizableMessage message)
  {
    if (message == null)
    {
      assertNull(ie.getMessageObject());
    }
    else
    {
      assertEquals(ie.getMessageObject(), message);
    }
  }
}

