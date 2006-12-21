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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines an exception that may be thrown if a problem
 * occurs while attempting to iterate across the members of a group.
 */
public class MembershipException
       extends Exception
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.MembershipException";



  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  private static final long serialVersionUID = -7312072056288770065L;



  /**
   * Indicates whether it is possible to continue iterating through
   * the list of group members.
   */
  private final boolean continueIterating;



  /**
   * The unique identifier for the error message.
   */
  private final int errorMessageID;



  /**
   * The error message for this membership exception.
   */
  private final String errorMessage;



  /**
   * Creates a new membership exception with the provided information.
   *
   * @param  errorMessageID     The unique identifier for the error
   *                            message.
   * @param  errorMessage       The error message for this membership
   *                            exception.
   * @param  continueIterating  Indicates whether it is possible to
   *                            continue iterating through the list of
   *                            group members.
   */
  public MembershipException(int errorMessageID, String errorMessage,
                             boolean continueIterating)
  {
    super(errorMessage);

    assert debugConstructor(CLASS_NAME,
                            String.valueOf(errorMessageID),
                            String.valueOf(errorMessage),
                            String.valueOf(continueIterating));

    this.errorMessageID    = errorMessageID;
    this.errorMessage      = errorMessage;
    this.continueIterating = continueIterating;
  }



  /**
   * Creates a new membership exception with the provided information.
   *
   * @param  errorMessageID     The unique identifier for the error
   *                            message.
   * @param  errorMessage       The error message for this membership
   *                            exception.
   * @param  continueIterating  Indicates whether it is possible to
   *                            continue iterating through the list of
   *                            group members.
   * @param  cause              The underlying cause for this
   *                            membership exception.
   */
  public MembershipException(int errorMessageID, String errorMessage,
                             boolean continueIterating,
                             Throwable cause)
  {
    super(errorMessage, cause);

    assert debugConstructor(CLASS_NAME,
                            String.valueOf(errorMessageID),
                            String.valueOf(errorMessage),
                            String.valueOf(continueIterating),
                            String.valueOf(cause));

    this.errorMessageID    = errorMessageID;
    this.errorMessage      = errorMessage;
    this.continueIterating = continueIterating;
  }



  /**
   * Retrieves the unique identifier for the error message.
   *
   * @return  The unique identifier for the error message.
   */
  public final int getErrorMessageID()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessageID");

    return errorMessageID;
  }



  /**
   * Retrieves the error message for this membership exception.
   *
   * @return  The error message for this membership exception.
   */
  public final String getErrorMessage()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessage");

    return errorMessage;
  }



  /**
   * Indicates whether it is possible to continue iterating through
   * the list of group members.
   *
   * @return  {@code true} if it is possible to continue iterating
   *          through the list of group members, or {@code false} if
   *          not.
   */
  public final boolean continueIterating()
  {
    assert debugEnter(CLASS_NAME, "continueIterating");

    return continueIterating;
  }
}

