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
package org.opends.server.types;
import org.opends.messages.Message;





import java.util.List;



/**
 * This class defines an exception that may be thrown if a problem
 * occurs in the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class DirectoryException
       extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  private static final long serialVersionUID = 2615453139798417203L;



  // The matched DN for this directory exception.
  private final DN matchedDN;

  // The set of referral URLs for this directory exception.
  private final List<String> referralURLs;

  // The result code for this directory exception.
  private final ResultCode resultCode;



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory
   *                         exception.
   * @param  errorMessage    The error message for this directory
   *                         exception.
   */
  public DirectoryException(ResultCode resultCode,
                            Message errorMessage)
  {
    super(errorMessage);


    this.resultCode     = resultCode;
    this.matchedDN      = null;
    this.referralURLs   = null;
  }



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory
   *                         exception.
   * @param  errorMessage    The error message for this directory
   *                         exception.
   * @param  cause           The exception that was caught to trigger
   *                         this directory exception.
   */
  public DirectoryException(ResultCode resultCode,
                            Message errorMessage,
                            Throwable cause)
  {
    super(errorMessage, cause);


    this.resultCode     = resultCode;
    this.matchedDN      = null;
    this.referralURLs   = null;
  }


  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode     The result code for this directory
   *                        exception.
   * @param  cause          The exception that was caught to trigger
   *                        this directory exception.  The message of
   *                        this exception will be set to that of this
   *                        parameter.
   */
  public DirectoryException(ResultCode resultCode,
                            OpenDsException cause)
  {
    super(cause.getMessageObject(), cause);


    this.resultCode     = resultCode;
    this.matchedDN      = null;
    this.referralURLs   = null;
  }


  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory
   *                         exception.
   * @param  errorMessage    The error message for this directory
   *                         exception.
   * @param  matchedDN       The matched DN for this directory
   *                         exception.
   * @param  cause           The exception that was caught to trigger
   *                         this directory exception.
   */
  public DirectoryException(ResultCode resultCode,
                            Message errorMessage,
                            DN matchedDN, Throwable cause)
  {
    super(errorMessage, cause);


    this.resultCode     = resultCode;
    this.matchedDN      = matchedDN;
    this.referralURLs   = null;
  }



  /**
   * Creates a new directory exception with the provided information.
   *
   * @param  resultCode      The result code for this directory
   *                         exception.
   * @param  errorMessage    The error message for this directory
   * @param  matchedDN       The matched DN for this directory
   *                         exception.
   * @param  referralURLs    The set of referral URLs for this
   *                         directory exception.
   * @param  cause           The exception that was caught to trigger
   *                         this directory exception.
   */
  public DirectoryException(ResultCode resultCode,
                            Message errorMessage,
                            DN matchedDN, List<String> referralURLs,
                            Throwable cause)
  {
    super(errorMessage, cause);


    this.resultCode     = resultCode;
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
    return resultCode;
  }



  /**
   * Retrieves the matched DN for this directory exception.
   *
   * @return  The matched DN for this directory exception, or
   *          <CODE>null</CODE> if there is none.
   */
  public DN getMatchedDN()
  {
    return matchedDN;
  }



  /**
   * Retrieves the set of referral URLs for this directory exception.
   *
   * @return  The set of referral URLs for this directory exception,
   *          or <CODE>null</CODE> if there are none.
   */
  public List<String> getReferralURLs()
  {
    return referralURLs;
  }
}

