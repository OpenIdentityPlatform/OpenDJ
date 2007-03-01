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
package org.opends.server.tools.makeldif;



/**
 * This class defines an exception that can be thrown if a problem occurs during
 * MakeLDIF processing.
 */
public class MakeLDIFException
       extends Exception
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = -918795152848233269L;



  // The unique identifier for the message.
  private int msgID;



  /**
   * Creates a new MakeLDIF exception with the provided information.
   *
   * @param  msgID    The unique identifier for the message.
   * @param  message  The message for this exception.
   */
  public MakeLDIFException(int msgID, String message)
  {
    super(message);

    this.msgID = msgID;
  }



  /**
   * Creates a new MakeLDIF exception with the provided information.
   *
   * @param  msgID    The unique identifier for the message.
   * @param  message  The message for this exception.
   * @param  cause    The underlying cause for this exception.
   */
  public MakeLDIFException(int msgID, String message, Throwable cause)
  {
    super(message, cause);

    this.msgID = msgID;
  }



  /**
   * Retrieves the unique identifier for the message.
   *
   * @return  The unique identifier for the message.
   */
  public int getMessageID()
  {
    return msgID;
  }
}

