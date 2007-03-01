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
package org.opends.server.backends.jeb;

/**
 * This class defines an exception that may be thrown if a problem occurs in the
 * JE backend database.
 */
public class JebException
     extends Exception
{


  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  static final long serialVersionUID = 3110979454298870834L;

  /**
   * The message ID for the message associated with this initialization
   * exception.
   */
  private int messageID;



  /**
   * Creates a new JE backend exception with the provided message.
   *
   * @param  messageID  The unique identifier for the associated message.
   * @param  message    The message that explains the problem that occurred.
   */
  public JebException(int messageID, String message)
  {
    super(message);


    this.messageID = messageID;
  }



  /**
   * Creates a new JE backend exception with the provided message and root
   * cause.
   *
   * @param  messageID  The unique identifier for the associated message.
   * @param  message    The message that explains the problem that occurred.
   * @param  cause      The exception that was caught to trigger this exception.
   */
  public JebException(int messageID, String message, Throwable cause)
  {
    super(message, cause);


    this.messageID = messageID;
  }



  /**
   * Retrieves the unique identifier for the associated message.
   * @return The message identifier.
   */
  public int getMessageID()
  {
    return messageID;
  }

}

