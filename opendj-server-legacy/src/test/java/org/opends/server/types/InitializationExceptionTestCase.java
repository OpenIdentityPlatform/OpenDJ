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
 * Portions Copyright 2014-2015 ForgeRock AS.
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

