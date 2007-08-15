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
package org.opends.server.util;
import org.opends.messages.Message;



import org.opends.server.types.IdentifiedException;



/**
 * This class defines an exception that may be thrown while attempting to parse
 * LDIF content.
 */
public final class LDIFException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 2291731429121120364L;



  // Indicates whether this exception is severe enough that it is no longer
  // possible to keep reading.
  private final boolean canContinueReading;

  // The line number of the last line read from the LDIF source.
  private final long lineNumber;



  /**
   * Creates a new LDIF exception with the provided information.
   *
   * @param  message    The message to use for this LDIF exception.
   */
  public LDIFException(Message message)
  {
    super(message);


    lineNumber         = -1;
    canContinueReading = true;
  }



  /**
   * Creates a new LDIF exception with the provided information.
   *
   * @param  message    The message to use for this LDIF exception.
   * @param  cause      The underlying cause that triggered this LDIF exception.
   */
  public LDIFException(Message message, Throwable cause)
  {
    super(message, cause);


    lineNumber         = -1;
    canContinueReading = true;
  }



  /**
   * Creates a new LDIF exception with the provided information.
   *
   * @param  message             The message to use for this LDIF exception.
   * @param  lineNumber          The line number of the last line read from the
   *                             LDIF source.
   * @param  canContinueReading  Indicates whether it is possible to continue
   *                             reading from the LDIF input source.
   */
  public LDIFException(Message message, long lineNumber,
                       boolean canContinueReading)
  {
    super(message);


    this.lineNumber         = lineNumber;
    this.canContinueReading = canContinueReading;
  }



  /**
   * Creates a new configuration exception with the provided message and
   * underlying cause.
   *
   * @param  message             The message to use for this LDIF exception.
   * @param  canContinueReading  Indicates whether it is possible to continue
   *                             reading from the LDIF input source.
   * @param  lineNumber          The line number of the last line read from the
   *                             LDIF source.
   * @param  cause               The underlying cause that triggered this LDIF
   *                             exception.
   */
  public LDIFException(Message message, long lineNumber,
                       boolean canContinueReading, Throwable cause)
  {
    super(message, cause);


    this.lineNumber         = lineNumber;
    this.canContinueReading = canContinueReading;
  }



  /**
   * Retrieves the line number of the last line read from the LDIF source.
   *
   * @return  The line number of the last line read from the LDIF source.
   */
  public long getLineNumber()
  {
    return lineNumber;
  }



  /**
   * Indicates whether the nature of this exception allows the caller to
   * continue reading LDIF data.  If this method returns <CODE>false</CODE>,
   * then the associated reader should be closed by the caller.
   *
   * @return  <CODE>true</CODE> if the problem was with a single entry but it is
   *          possible to continue reading with the next entry, or
   *          <CODE>false</CODE> if the problem was such that it is no longer
   *          possible to continue reading the data.
   */
  public boolean canContinueReading()
  {
    return canContinueReading;
  }
}

