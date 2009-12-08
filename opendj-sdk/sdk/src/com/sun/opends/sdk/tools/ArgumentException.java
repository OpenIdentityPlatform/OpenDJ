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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package com.sun.opends.sdk.tools;



import org.opends.sdk.LocalizableException;
import org.opends.sdk.LocalizableMessage;




/**
 * This class defines an exception that may be thrown if there is a
 * problem with an argument definition.
 */
final class ArgumentException extends Exception implements
    LocalizableException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>, which
   * implements the <CODE>java.io.Serializable</CODE> interface. This
   * value was generated using the <CODE>serialver</CODE> command-line
   * utility included with the Java SDK.
   */
  private static final long serialVersionUID = 5623155045312160730L;

  // The I18N message associated with this exception.
  private final LocalizableMessage message;



  /**
   * Creates a new argument exception with the provided message.
   * 
   * @param message
   *          The message that explains the problem that occurred.
   */
  ArgumentException(LocalizableMessage message)
  {
    super(String.valueOf(message));
    this.message = message;
  }



  /**
   * Creates a new argument exception with the provided message and root
   * cause.
   * 
   * @param message
   *          The message that explains the problem that occurred.
   * @param cause
   *          The exception that was caught to trigger this exception.
   */
  ArgumentException(LocalizableMessage message, Throwable cause)
  {
    super(String.valueOf(message), cause);
    this.message = message;
  }



  /**
   * {@inheritDoc}
   */
  public LocalizableMessage getMessageObject()
  {
    return this.message;
  }

}
