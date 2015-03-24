/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This class defines a data structure that can be used to hold
 * information about a request to cancel or abandon an operation in
 * progress.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class CancelRequest
{
  /**
   * Indicates whether to send a response to the original request if
   * the operation is canceled.
   */
  private final boolean notifyOriginalRequestor;

  /**
   * A message that explains the purpose for this cancellation (may be
   * included in the response to the original requestor).
   */
  private final LocalizableMessage cancelReason;



  /**
   * Creates a new cancel request with the provided information.
   *
   * @param  notifyOriginalRequestor  Indicates whether the original
   *                                  requestor should receive a
   *                                  response if the operation is
   *                                  canceled.
   * @param  cancelReason             A message that explains the
   *                                  purpose for this cancellation.
   */
  public CancelRequest(boolean notifyOriginalRequestor,
                       LocalizableMessage cancelReason)
  {
    this.notifyOriginalRequestor = notifyOriginalRequestor;
    this.cancelReason            = cancelReason;
  }



  /**
   * Indicates whether the original requestor should receive a
   * response to the request if the operation is canceled.
   *
   * @return  <CODE>true</CODE> if the original requestor should
   *          receive a response if the operation is canceled, or
   *          <CODE>false</CODE> if not.
   */
  public boolean notifyOriginalRequestor()
  {
    return notifyOriginalRequestor;
  }



  /**
   * Retrieves a message that explains the purpose for this
   * cancellation.
   *
   * @return  A message that explains the purpose for this
   *          cancellation.
   */
  public LocalizableMessage getCancelReason()
  {
    return cancelReason;
  }
}

