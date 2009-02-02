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
import org.opends.server.api.ClientConnection;
import org.opends.server.api.QOSPolicy;
import org.opends.server.types.operation.PreParseOperation;



/**
 * This class defines the resource limits policy applicable to all
 * connections inside the same network group.
 */
abstract class ResourceLimitsPolicy extends QOSPolicy
{
  /**
   * Creates a new resource limits policy.
   */
  protected ResourceLimitsPolicy()
  {
    // No implementation required.
  }



  /**
   * Adds a connection to the network group.
   *
   * @param connection
   *          The client connection.
   */
  abstract void addConnection(ClientConnection connection);



  /**
   * Returns the minimum string length for a substring filter.
   *
   * @return The minimum string length for a substring filter.
   */
  abstract int getMinSubstring();



  /**
   * Returns the default maximum number of entries that should be
   * returned for a searches processed by this network group.
   *
   * @return The default maximum number of entries that should be
   *         returned for a searches processed by this network group.
   */
  abstract int getSizeLimit();



  /**
   * Returns the statistics associated with this resource limits policy.
   *
   * @return The statistics associated with this resource limits policy.
   */
  abstract ResourceLimitsPolicyStatistics getStatistics();



  /**
   * Returns the maximum length of time in seconds permitted for a
   * search operation processed by this network group.
   *
   * @return The maximum length of time in seconds permitted for a
   *         search operation processed by this network group.
   */
  abstract int getTimeLimit();



  /**
   * Determines if the provided operation is allowed according to this
   * resource limits policy.
   *
   * @param connection
   *          the ClientConnection to check
   * @param operation
   *          the ongoing operation
   * @param fullCheck
   *          a boolean indicating if full checks must be done
   * @param messages
   *          the messages to include in the disconnect notification
   *          response. It may be <CODE>null</CODE> if no message is to
   *          be sent.
   * @return a boolean indicating whether the connection is allowed
   */
  abstract boolean isAllowed(ClientConnection connection,
      PreParseOperation operation, boolean fullCheck,
      List<Message> messages);



  /**
   * Removes a connection from the network group.
   *
   * @param connection
   *          The client connection to remove.
   */
  abstract void removeConnection(ClientConnection connection);
}
