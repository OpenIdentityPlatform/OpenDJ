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
 *    Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import java.util.List;

import org.opends.messages.Message;
import org.opends.server.api.QOSPolicy;
import org.opends.server.types.operation.PreParseOperation;



/**
 * This class defines the request filtering policy applicable to all
 * connections inside the same network group.
 */
abstract class RequestFilteringPolicy extends QOSPolicy
{
  /**
   * Creates a new request filtering policy.
   */
  protected RequestFilteringPolicy()
  {
    // No implementation required.
  }



  /**
   * Returns the statistics associated with this request filtering
   * policy.
   *
   * @return The statistics associated with this request filtering
   *         policy.
   */
  abstract RequestFilteringPolicyStatistics getStatistics();



  /**
   * Determines if the provided operation is allowed according to this
   * request filtering policy.
   *
   * @param operation
   *          The operation
   * @param messages
   *          The messages to include in the disconnect notification
   *          response. It may be <CODE>null</CODE> if no message is to
   *          be sent.
   * @return {@code true} if the operation is allowed.
   */
  abstract boolean isAllowed(PreParseOperation operation,
      List<Message> messages);
}
