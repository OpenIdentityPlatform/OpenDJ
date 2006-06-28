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
package org.opends.server.core;



import java.util.List;

import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines an exception that may be thrown if a problem occurs in the
 * Directory Server.
 */
public class DirectoryException
       extends Exception
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.core.DirectoryException";



  /**
   * The serial version identifier required to satisfy the compiler because this
   * class extends <CODE>java.lang.Exception</CODE>, which implements the
   * <CODE>java.io.Serializable</CODE> interface.  This value was generated
   * using the <CODE>serialver</CODE> command-line utility included with the
   * Java SDK.
   */
  private static final long serialVersionUID = 2615453139798417203L;



  // The matched DN for this directory exception.
  private DN matchedDN;

  // The message ID for the error message.
  private int errorMessageID;

  // The set of referral URLs for this directory exception.
  private List<String> referralURLs;

  // The result code for this directory exception.
  private ResultCode resultCode;

  // The error message for this directory exception.
  private String errorMessage;



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory exception.
   * @param  errorMessage    The error message for this directory exception.
   * @param  errorMessageID  The unique ID for the error message.
   */
  public DirectoryException(ResultCode resultCode, String errorMessage,
                            int errorMessageID)
  {
    super(errorMessage);

    assert debugConstructor(CLASS_NAME, String.valueOf(resultCode),
                            String.valueOf(errorMessage),
                            String.valueOf(errorMessageID));

    this.resultCode     = resultCode;
    this.errorMessage   = errorMessage;
    this.errorMessageID = errorMessageID;
    this.matchedDN      = null;
    this.referralURLs   = null;
  }



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory exception.
   * @param  errorMessage    The error message for this directory exception.
   * @param  errorMessageID  The unique ID for the error message.
   * @param  cause           The exception that was caught to trigger this
   *                         directory exception.
   */
  public DirectoryException(ResultCode resultCode, String errorMessage,
                            int errorMessageID, Throwable cause)
  {
    super(errorMessage, cause);

    assert debugConstructor(CLASS_NAME, String.valueOf(resultCode),
                            String.valueOf(errorMessage),
                            String.valueOf(errorMessageID),
                            String.valueOf(cause));

    this.resultCode     = resultCode;
    this.errorMessage   = errorMessage;
    this.errorMessageID = errorMessageID;
    this.matchedDN      = null;
    this.referralURLs   = null;
  }



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory exception.
   * @param  errorMessage    The error message for this directory exception.
   * @param  errorMessageID  The unique ID for the error message.
   * @param  matchedDN       The matched DN for this directory exception.
   * @param  cause           The exception that was caught to trigger this
   *                         directory exception.
   */
  public DirectoryException(ResultCode resultCode, String errorMessage,
                            int errorMessageID, DN matchedDN, Throwable cause)
  {
    super(errorMessage, cause);

    assert debugConstructor(CLASS_NAME, String.valueOf(resultCode),
                            String.valueOf(errorMessage),
                            String.valueOf(errorMessageID),
                            String.valueOf(matchedDN), String.valueOf(cause));

    this.resultCode     = resultCode;
    this.errorMessage   = errorMessage;
    this.errorMessageID = errorMessageID;
    this.matchedDN      = matchedDN;
    this.referralURLs   = null;
  }



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory exception.
   * @param  errorMessage    The error message for this directory exception.
   * @param  errorMessageID  The unique ID for the error message.
   * @param  matchedDN       The matched DN for this directory exception.
   * @param  referralURLs    The set of referral URLs for this directory
   *                         exception.
   * @param  cause           The exception that was caught to trigger this
   *                         directory exception.
   */
  public DirectoryException(ResultCode resultCode, String errorMessage,
                            int errorMessageID, DN matchedDN,
                            List<String> referralURLs, Throwable cause)
  {
    super(errorMessage, cause);

    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(resultCode),
                              String.valueOf(errorMessage),
                              String.valueOf(errorMessageID),
                              String.valueOf(matchedDN),
                              String.valueOf(referralURLs),
                              String.valueOf(cause)
                            });

    this.resultCode     = resultCode;
    this.errorMessage   = errorMessage;
    this.errorMessageID = errorMessageID;
    this.matchedDN      = matchedDN;
    this.referralURLs   = referralURLs;
  }



  /**
   * Retrieves the result code for this directory exception.
   *
   * @return  The result code for this directory exception.
   */
  public ResultCode getResultCode()
  {
    assert debugEnter(CLASS_NAME, "getResultCode");

    return resultCode;
  }



  /**
   * Retrieves the error message for this directory exception.
   *
   * @return  The error message for this directory exception.
   */
  public String getErrorMessage()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessage");

    return errorMessage;
  }



  /**
   * Retrieves the unique ID for the error message associated with this
   * directory exception.
   *
   * @return  The unique ID for the error message associated with this directory
   *          exception.
   */
  public int getErrorMessageID()
  {
    assert debugEnter(CLASS_NAME, "getErrorMessageID");

    return errorMessageID;
  }



  /**
   * Retrieves the matched DN for this directory exception.
   *
   * @return  The matched DN for this directory exception, or <CODE>null</CODE>
   *          if there is none.
   */
  public DN getMatchedDN()
  {
    assert debugEnter(CLASS_NAME, "getMatchedDN");

    return matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this directory exception.
   *
   * @return  The set of referral URLs for this directory exception, or
   *          <CODE>null</CODE> if there are none.
   */
  public List<String> getReferralURLs()
  {
    assert debugEnter(CLASS_NAME, "getReferralURLs");

    return referralURLs;
  }
}

