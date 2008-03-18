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
package org.opends.server.api.plugin;

import org.opends.messages.Message;
import org.opends.server.types.ResultCode;
import org.opends.server.types.DN;
import org.opends.server.types.DisconnectReason;

import java.util.List;

/**
 * This class defines a data structure that holds information about
 * the result of processing by a plugin.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=true,
    mayExtend=false,
    mayInvoke=true)
public final class PluginResult
{
  /**
   * Defines a startup plugin result consisting of either continue
   * skip further plugins, or stop startup with an error message.
   */
  public static final class Startup
  {
    // Whether to continue startup.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why startup should stop.
    private final Message errorMessage;

    private static Startup DEFAULT_RESULT =
        new Startup(true, true, null);

    /**
     * Construct a new startup plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why startup should
     * stop.
     */
    private Startup(boolean continueProcessing,
                    boolean continuePluginProcessing,
                    Message errorMessage)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
    }

    /**
     * Defines a continue processing startup plugin result.
     *
     * @return a continue processing startup plugin result.
     */
    public static Startup continueStartup()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing startup plugin result.
     *
     * @return  a skip further plugin processing startup plugin
     * result.
     */
    public static Startup skipFurtherPluginProcesssing()
    {
      return new Startup(true, false, null);
    }

    /**
     * Defines a new stop processing startup plugin result.
     *
     * @param errorMessage An message explaining why processing
     * should stop for the given entry.
     *
     * @return a new stop processing startup plugin result.
     */
    public static Startup stopStartup(Message errorMessage)
    {
      return new Startup(false, false, errorMessage);
    }

    /**
     * Whether to continue startup.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }
  }

  /**
   * Defines a pre parse plugin result for core server operation
   * processing consisting of either continue, skip further
   * plugins, or stop operation processing with a result code,
   * matched DN, referral URLs, and error message.
   */
  public static final class PreParse
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private static PreParse DEFAULT_RESULT =
        new PreParse(true, true, null, null, null, null);

    /**
     * Construct a new pre parse plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param resultCode The result code for this result.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     * stop.
     */
    private PreParse (boolean continueProcessing,
                      boolean continuePluginProcessing,
                      Message errorMessage,
                      ResultCode resultCode, DN matchedDN,
                      List<String> referralURLs)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
      this.resultCode = resultCode;
      this.matchedDN = matchedDN;
      this.referralURLs = referralURLs;
    }

    /**
     * Defines a continue processing pre parse plugin result.
     *
     * @return a continue processing pre parse plugin result.
     */
    public static PreParse continueOperationProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing pre parse plugin
     * result.
     *
     * @return  a skip further plugin processing pre parse plugin
     * result.
     */
    public static PreParse skipFurtherPluginProcesssing()
    {
      return new PreParse(true, false, null, null, null, null);
    }

    /**
     * Defines a new stop processing pre parse plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     *
     * @return a new stop processing pre parse plugin result.
     */
    public static PreParse stopProcessing(ResultCode resultCode,
                                          Message errorMessage,
                                          DN matchedDN,
                                          List<String> referralURLs)
    {
      return new PreParse(false, false, errorMessage, resultCode,
          matchedDN, referralURLs);
    }

    /**
     * Contrust a new stop processing pre parse plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     *
     * @return a new stop processing pre parse plugin result.
     */
    public static PreParse stopProcessing(ResultCode resultCode,
                                          Message errorMessage)
    {
      return new PreParse(false, false, errorMessage, resultCode,
          null, null);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * Retrieves the result code for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the result code for the operation or <code>null</code>
     * if none is provided.
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the matched DN for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the matched DN for the operation or <code>null</code>
     * if none is provided.
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * Retrieves the referral URLs for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the refferal URLs for the operation or
     * <code>null</code> if none is provided.
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }
  }

  /**
   * Defines a pre operation plugin result for core server operation
   * processing consisting of either continue, skip further
   * plugins, or stop operation processing with a result code,
   * matched DN, referral URLs, and error message.
   */
  public static final class PreOperation
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private static PreOperation DEFAULT_RESULT =
        new PreOperation(true, true, null, null, null, null);

    /**
     * Construct a new pre operation plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param resultCode The result code for this result.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     * stop.
     */
    private PreOperation (boolean continueProcessing,
                          boolean continuePluginProcessing,
                          Message errorMessage,
                          ResultCode resultCode, DN matchedDN,
                          List<String> referralURLs)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
      this.resultCode = resultCode;
      this.matchedDN = matchedDN;
      this.referralURLs = referralURLs;
    }

    /**
     * Defines a continue processing pre operation plugin result.
     *
     * @return a continue processing pre operation plugin result.
     */
    public static PreOperation continueOperationProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing pre operation plugin
     * result.
     *
     * @return  a skip further plugin processing pre operation plugin
     * result.
     */
    public static PreOperation skipFurtherPluginProcesssing()
    {
      return new PreOperation(true, false, null, null, null, null);
    }

    /**
     * Defines a new stop processing pre operation plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     *
     * @return a new stop processing pre operation plugin result.
     */
    public static PreOperation stopProcessing(
        ResultCode resultCode, Message errorMessage, DN matchedDN,
        List<String> referralURLs)
    {
      return new PreOperation(false, false, errorMessage, resultCode,
          matchedDN, referralURLs);
    }

    /**
     * Contrust a new stop processing pre operation plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     *
     * @return a new stop processing pre operation plugin result.
     */
    public static PreOperation stopProcessing(ResultCode resultCode,
                                              Message errorMessage)
    {
      return new PreOperation(false, false, errorMessage, resultCode,
          null, null);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * Retrieves the result code for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the result code for the operation or <code>null</code>
     * if none is provided.
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the matched DN for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the matched DN for the operation or <code>null</code>
     * if none is provided.
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * Retrieves the referral URLs for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the refferal URLs for the operation or
     * <code>null</code> if none is provided.
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }
  }

  /**
   * Defines a post operation plugin result for core server operation
   * processing consisting of either continue, skip further
   * plugins, or stop operation processing with a result code,
   * matched DN, referral URLs, and error message.
   */
  public static final class PostOperation
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private static PostOperation DEFAULT_RESULT =
        new PostOperation(true, null, null, null, null);

    /**
     * Constructs a new post operation plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param resultCode The result code for this result.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     */
    private PostOperation(boolean continueProcessing,
                          Message errorMessage,
                          ResultCode resultCode, DN matchedDN,
                          List<String> referralURLs)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.resultCode = resultCode;
      this.matchedDN = matchedDN;
      this.referralURLs = referralURLs;
    }

    /**
     * Defines a continue processing post operation plugin result.
     *
     * @return a continue processing post operation plugin result.
     */
    public static PostOperation continueOperationProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a new stop processing post operation plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     *
     * @return a new stop processing post operation plugin result.
     */
    public static PostOperation stopProcessing(
        ResultCode resultCode, Message errorMessage, DN matchedDN,
        List<String> referralURLs)
    {
      return new PostOperation(false, errorMessage, resultCode,
          matchedDN, referralURLs);
    }

    /**
     * Contrust a new stop processing post operation plugin result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     *
     * @return a new stop processing post operation plugin result.
     */
    public static PostOperation stopProcessing(ResultCode resultCode,
                                               Message errorMessage)
    {
      return new PostOperation(false, errorMessage, resultCode, null,
          null);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * Retrieves the result code for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the result code for the operation or <code>null</code>
     * if none is provided.
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the matched DN for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the matched DN for the operation or <code>null</code>
     * if none is provided.
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * Retrieves the referral URLs for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the refferal URLs for the operation or
     * <code>null</code> if none is provided.
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }
  }


  /**
   * Defines a post response plugin result for core server operation
   * processing consisting of either continue or skip further
   * plugins.
   */
  public static final class PostResponse
  {
    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    private static PostResponse DEFAULT_RESULT =
        new PostResponse(true);

    /**
     * Constructs a new post response plugin result.
     *
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     */
    private PostResponse (boolean continuePluginProcessing)
    {
      this.continuePluginProcessing = continuePluginProcessing;
    }

    /**
     * Defines a continue processing post response plugin result.
     *
     * @return a continue processing post response plugin result.
     */
    public static PostResponse continueOperationProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing post response plugin
     *  result.
     *
     * @return  a skip further plugin processing post response plugin
     *  result.
     */
    public static PostResponse skipFurtherPluginProcesssing()
    {
      return new PostResponse(false);
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }
  }

  /**
   * Defines a LDIF plugin result for import from LDIF
   * processing consisting of either continue, skip further
   * plugins, or stop processing with an error message.
   */
  public static final class ImportLDIF
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    private static ImportLDIF DEFAULT_RESULT =
        new ImportLDIF(true, true, null);

    /**
     * Construct a new import LDIF plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why startup should
     * stop.
     */
    private ImportLDIF(boolean continueProcessing,
                       boolean continuePluginProcessing,
                       Message errorMessage)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
    }

    /**
     * Defines a continue processing LDIF import plugin result.
     *
     * @return a continue processing LDIF import plugin result.
     */
    public static ImportLDIF continueEntryProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing LDIF import plugin
     *  result.
     *
     * @return  a skip further plugin processing LDIF import plugin
     *  result.
     */
    public static ImportLDIF skipFurtherPluginProcesssing()
    {
      return new ImportLDIF(true, false, null);
    }

    /**
     * Defines a new stop processing LDIF import plugin result.
     *
     * @param errorMessage An message explaining why processing
     * should stop for the given entry.
     *
     * @return a new stop processing LDIF import plugin result.
     */
    public static ImportLDIF stopEntryProcessing(Message errorMessage)
    {
      return new ImportLDIF(false, false, errorMessage);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }
  }

  /**
   * Defines a subordinate modify DN plugin result for core server
   * operation processing consisting of either continue, skip further
   * plugins, or stop operation processing with a result code,
   * matched DN, referral URLs, and error message.
   */
  public static final class SubordinateModifyDN
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private static SubordinateModifyDN DEFAULT_RESULT =
        new SubordinateModifyDN(true, true, null, null, null, null);

    /**
     * Construct a new subordinate modify DN plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param resultCode The result code for this result.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     * stop.
     */
    private SubordinateModifyDN(boolean continueProcessing,
                                boolean continuePluginProcessing,
                                Message errorMessage,
                                ResultCode resultCode, DN matchedDN,
                                List<String> referralURLs)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
      this.resultCode = resultCode;
      this.matchedDN = matchedDN;
      this.referralURLs = referralURLs;
    }

    /**
     * Defines a continue processing subordinate modify DN plugin
     *  result.
     *
     * @return a continue processing subordinate modify DN plugin
     *  result.
     */
    public static SubordinateModifyDN continueOperationProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing subordinate modify DN
     * plugin result.
     *
     * @return  a skip further plugin processing subordinate modify DN
     * plugin result.
     */
    public static SubordinateModifyDN skipFurtherPluginProcesssing()
    {
      return new SubordinateModifyDN(true, false, null, null, null,
          null);
    }

    /**
     * Defines a new stop processing subordinate modify DN plugin
     * result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     *
     * @return a new stop processing subordinate modify DN plugin
     * result.
     */
    public static SubordinateModifyDN stopProcessing(
        ResultCode resultCode, Message errorMessage, DN matchedDN,
        List<String> referralURLs)
    {
      return new SubordinateModifyDN(false, false, errorMessage,
          resultCode, matchedDN, referralURLs);
    }

    /**
     * Contrust a new stop processing subordinate modify DN plugin
     * result.
     *
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     *
     * @return a new stop processing subordinate modify DN plugin
     * result.
     */
    public static SubordinateModifyDN stopProcessing(
        ResultCode resultCode, Message errorMessage)
    {
      return new SubordinateModifyDN(false, false, errorMessage,
          resultCode, null, null);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * Retrieves the result code for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the result code for the operation or <code>null</code>
     * if none is provided.
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the matched DN for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the matched DN for the operation or <code>null</code>
     * if none is provided.
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * Retrieves the referral URLs for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the refferal URLs for the operation or
     * <code>null</code> if none is provided.
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }
  }

  /**
   * Defines an intermediate response plugin result for core server
   *  operation processing consisting of either continue, skip further
   * plugins, or stop operation processing with a result code,
   * matched DN, referral URLs, and error message.
   */
  public static final class IntermediateResponse
  {
    // Whether to continue operation processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // Whether to send the intermediate response to the client.
    private final boolean sendResponse;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The matched DN for this result.
    private final DN matchedDN;

    // The set of referral URLs for this result.
    private final List<String> referralURLs;

    // The result code for this result.
    private final ResultCode resultCode;

    private static IntermediateResponse DEFAULT_RESULT =
        new IntermediateResponse(true, true, true, null, null, null,
            null);

    /**
     * Construct a new intermediate response plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param sendResponse Whether to send the intermediate response
     * to the client.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param resultCode The result code for this result.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     * stop.
     */
    private IntermediateResponse(boolean continueProcessing,
                                 boolean continuePluginProcessing,
                                 boolean sendResponse,
                                 Message errorMessage,
                                 ResultCode resultCode, DN matchedDN,
                                 List<String> referralURLs)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
      this.resultCode = resultCode;
      this.matchedDN = matchedDN;
      this.referralURLs = referralURLs;
      this.sendResponse = sendResponse;
    }

    /**
     * Defines a continue processing intermediate response plugin
     * result.
     *
     * @param sendResponse Whether to send the intermediate response
     * to the client.
     * @return a continue processing intermediate response plugin
     * result.
     */
    public static IntermediateResponse
    continueOperationProcessing(boolean sendResponse)
    {
      if(sendResponse)
      {
        return DEFAULT_RESULT;
      }
      else
      {
        return new IntermediateResponse(true, true, sendResponse,
            null, null, null, null);
      }
    }

    /**
     * Defines a skip further plugin processing intermediate response
     * plugin result.
     *
     * @param sendResponse Whether to send the intermediate response
     * to the client.
     *
     * @return  a skip further plugin processing intermediate response
     * plugin result.
     */
    public static IntermediateResponse
    skipFurtherPluginProcesssing(boolean sendResponse)
    {
      return new IntermediateResponse(true, false, sendResponse,
          null, null, null, null);
    }

    /**
     * Defines a new stop processing intermediate response plugin
     * result.
     *
     * @param sendResponse Whether to send the intermediate response
     * to the client.
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param matchedDN The matched DN for this result.
     * @param referralURLs The set of referral URLs for this result.
     *
     * @return a new stop processing intermediate response plugin
     * result.
     */
    public static IntermediateResponse stopProcessing(
        boolean sendResponse, ResultCode resultCode,
        Message errorMessage, DN matchedDN, List<String> referralURLs)
    {
      return new IntermediateResponse(false, false, sendResponse,
          errorMessage, resultCode, matchedDN, referralURLs);
    }

    /**
     * Contrust a new stop processing intermediate response plugin
     * result.
     *
     * @param sendResponse Whether to send the intermediate response
     * to the client.
     * @param resultCode The result code for this result.
     * @param errorMessage An message explaining why processing
     * should stop.
     *
     * @return a new stop processing intermediate response plugin
     * result.
     */
    public static IntermediateResponse stopProcessing(
        boolean sendResponse, ResultCode resultCode,
        Message errorMessage)
    {
      return new IntermediateResponse(false, false, sendResponse,
          errorMessage, resultCode, null, null);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Whether to send the intermediate response to the client.
     *
     * @return <code>true</code> if the intermediate response should
     * be sent to the client or <code>false</code> otherwise.
     */
    public boolean sendResponse()
    {
      return sendResponse;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * Retrieves the result code for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the result code for the operation or <code>null</code>
     * if none is provided.
     */
    public ResultCode getResultCode()
    {
      return resultCode;
    }

    /**
     * Retrieves the matched DN for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the matched DN for the operation or <code>null</code>
     * if none is provided.
     */
    public DN getMatchedDN()
    {
      return matchedDN;
    }

    /**
     * Retrieves the referral URLs for the operation
     * if <code>continueProcessing</code> returned <code>false</code>.
     *
     * @return the refferal URLs for the operation or
     * <code>null</code> if none is provided.
     */
    public List<String> getReferralURLs()
    {
      return referralURLs;
    }
  }

  /**
   * Defines a post connect plugin result for client connection
   * processing consisting of either continue, skip further
   * plugins, or stop.
   */
  public static final class PostConnect
  {
    // Whether to continue connection processing.
    private final boolean continueProcessing;

    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    // An message explaining why processing should stop.
    private final Message errorMessage;

    // The disconnect reason that provides the generic cause for the
    // disconnect.
    private final DisconnectReason disconnectReason;

    // Whether to send a disconnect notification to the client.
    private final boolean sendDisconnectNotification;

    private static PostConnect DEFAULT_RESULT =
        new PostConnect(true, true, null, null, false);

    /**
     * Construct a new post connect plugin result.
     *
     * @param continueProcessing Whether to continue startup.
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     * @param errorMessage An message explaining why processing
     * should stop.
     * @param disconnectReason The generic cause for the disconnect.
     * @param sendDisconnectNotification Whether to send a disconnect
     * notification to the client.
     */
    private PostConnect(boolean continueProcessing,
                        boolean continuePluginProcessing,
                        Message errorMessage,
                        DisconnectReason disconnectReason,
                        boolean sendDisconnectNotification)
    {
      this.continueProcessing = continueProcessing;
      this.errorMessage = errorMessage;
      this.continuePluginProcessing = continuePluginProcessing;
      this.disconnectReason = disconnectReason;
      this.sendDisconnectNotification = sendDisconnectNotification;
    }

    /**
     * Defines a continue processing post connect plugin result.
     *
     * @return a continue processing post connect plugin result.
     */
    public static PostConnect continueConnectProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing post connect plugin
     * result.
     *
     * @return  a skip further plugin processing post connect plugin
     * result.
     */
    public static PostConnect skipFurtherPluginProcesssing()
    {
      return new PostConnect(true, false, null, null, false);
    }

    /**
     * Defines a new stop processing post connect plugin result.
     *
     * @param disconnectReason The generic cause for the disconnect.
     * @param sendDisconnectNotification Whether to send a disconnect
     * notification to the client.
     * @param errorMessage An message explaining why processing
     * should stop for the given entry.
     *
     * @return a new stop processing post connect plugin result.
     */
    public static PostConnect disconnectClient(
        DisconnectReason disconnectReason,
        boolean sendDisconnectNotification, Message errorMessage)
    {
      return new PostConnect(false, false, errorMessage,
          disconnectReason, sendDisconnectNotification);
    }

    /**
     * Whether to continue operation processing.
     *
     * @return <code>true</code> if processing should continue
     * or <code>false</code> otherwise.
     */
    public boolean continueProcessing()
    {
      return continueProcessing;
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }

    /**
     * Retrieves the error message if <code>continueProcessing</code>
     * returned <code>false</code>.
     *
     * @return An error message explaining why processing should
     * stop or <code>null</code> if none is provided.
     */
    public Message getErrorMessage()
    {
      return errorMessage;
    }

    /**
     * The disconnect reason that provides the generic cause for the
     * disconnect.
     *
     * @return the generic cause for the disconnect.
     */
    public DisconnectReason getDisconnectReason()
    {
      return disconnectReason;
    }

    /**
     * Indicates whether to try to provide notification to the client
     * that the connection will be closed.
     *
     * @return <code>true</code> if notification should be provided or
     * <code>false</code> otherwise.
     */
    public boolean sendDisconnectNotification()
    {
      return sendDisconnectNotification;
    }
  }

  /**
   * Defines a post disconnect plugin result for client connection
   * processing consisting of either continue or skip further
   * plugins.
   */
  public static final class PostDisconnect
  {
    // Whether to invoke the rest of the plugins.
    private final boolean continuePluginProcessing;

    private static PostDisconnect DEFAULT_RESULT =
        new PostDisconnect(true);

    /**
     * Construct a new post disconnect plugin result.
     *
     * @param continuePluginProcessing Whether to invoke the rest
     * of the plugins.
     */
    private PostDisconnect(boolean continuePluginProcessing)
    {
      this.continuePluginProcessing = continuePluginProcessing;
    }

    /**
     * Defines a continue processing post disconnect plugin result.
     *
     * @return a continue processing post disconnect plugin result.
     */
    public static PostDisconnect continueDisconnectProcessing()
    {
      return DEFAULT_RESULT;
    }

    /**
     * Defines a skip further plugin processing post disconnect
     * plugin result.
     *
     * @return  a skip further plugin processing post disconnect
     * plugin result.
     */
    public static PostDisconnect skipFurtherPluginProcesssing()
    {
      return new PostDisconnect(false);
    }

    /**
     * Whether to invoke the rest of the plugins.
     *
     * @return <code>true</code> if the rest of the plugins should
     * be invoked for <code>false</code> to skip the rest of the
     * plugins.
     */
    public boolean continuePluginProcessing()
    {
      return continuePluginProcessing;
    }
  }
}
