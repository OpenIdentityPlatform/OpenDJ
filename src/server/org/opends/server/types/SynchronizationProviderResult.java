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
 * This class defines a data structure that holds information about
 * the result of processing by a synchronization provider.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.VOLATILE,
    mayInstantiate=false,
    mayExtend=false,
    mayInvoke=true)
public interface SynchronizationProviderResult
{
  /**
   * Indicates whether processing on the associated operation should
   * continue.
   *
   * @return  <CODE>true</CODE> if processing on the associated
   *          operation should continue, or <CODE>false</CODE> if it
   *          should stop.
   */
  public boolean continueProcessing();

  /**
   * Retrieves the error message if <code>continueProcessing</code>
   * returned <code>false</code>.
   *
   * @return An error message explaining why processing should
   * stop or <code>null</code> if none is provided.
   */
  public Message getErrorMessage();

  /**
   * Retrieves the result code for the operation
   * if <code>continueProcessing</code> returned <code>false</code>.
   *
   * @return the result code for the operation or <code>null</code>
   * if none is provided.
   */
  public ResultCode getResultCode();

  /**
   * Retrieves the matched DN for the operation
   * if <code>continueProcessing</code> returned <code>false</code>.
   *
   * @return the matched DN for the operation or <code>null</code>
   * if none is provided.
   */
  public DN getMatchedDN();

  /**
   * Retrieves the referral URLs for the operation
   * if <code>continueProcessing</code> returned <code>false</code>.
   *
   * @return the refferal URLs for the operation or
   * <code>null</code> if none is provided.
   */
  public List<String> getReferralURLs();

  /**
   * Defines a continue processing synchronization provider result.
   */
  public class ContinueProcessing
      implements SynchronizationProviderResult
  {
    /**
     * {@inheritDoc}
     */
    public ResultCode getResultCode()
    {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public DN getMatchedDN()
    {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getReferralURLs()
    {
      return null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean continueProcessing()
    {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    public Message getErrorMessage()
    {
      return null;
    }
  }

  /**
   * Defines a stop processing synchronization provider result.
   */
  public class StopProcessing
      implements SynchronizationProviderResult
  {
    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private final Message errorMessage;

    /**
     * Contrust a new stop processing synchronization provider
     *  result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     */
    public StopProcessing(ResultCode resultCode, Message errorMessage,
                          DN matchedDN, List<String> referralURLs)
    {
      this.errorMessage = errorMessage;
      this.matchedDN = matchedDN;
      this.resultCode = resultCode;
      this.referralURLs = referralURLs;
    }

    /**
     * Contrust a new stop processing synchronization provider
     *  result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     */
    public StopProcessing(ResultCode resultCode, Message errorMessage)
    {
      this.errorMessage = errorMessage;
      this.resultCode = resultCode;
      this.matchedDN = null;
      this.referralURLs = null;
    }

    /**
     * {@inheritDoc}
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * {@inheritDoc}
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }

    /**
     * {@inheritDoc}
     */
    public boolean continueProcessing()
    {
      return false;
    }

    /**
     * {@inheritDoc}
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }
  }
}

