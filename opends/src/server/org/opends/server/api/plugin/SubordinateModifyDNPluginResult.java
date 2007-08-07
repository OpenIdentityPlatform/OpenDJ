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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.api.plugin;



/**
 * This class defines a data structure that holds information about
 * the result of processing by a subordinate modify DN plugin.
 */
public class SubordinateModifyDNPluginResult
{
  /**
   * A pre-operation plugin result instance that indicates all
   * processing was successful.
   */
  public static final SubordinateModifyDNPluginResult SUCCESS =
       new SubordinateModifyDNPluginResult();



  // Indicates whether to abort the modify DN operation without making
  // any changes.
  private final boolean abortModifyDNOperation;

  // Indicates whether any further subordinate modify DN plugins
  // should be invoked for this operation.
  private final boolean continuePluginProcessing;

  // Indicates whether the subordinate modify DN plugin terminated the
  // client connection.
  private final boolean connectionTerminated;



  /**
   * Creates a new subordinate modify DN plugin result with the
   * default settings.  In this case, it will indicate that the
   * connection has not been terminated, that further subordinate
   * modify DN processing should continue, and that the modify DN
   * operation should not be aborted.
   */
  private SubordinateModifyDNPluginResult()
  {
    this(false, true, false);
  }



  /**
   * Creates a new subordinate modify DN plugin result with the
   * provided information.
   *
   * @param  connectionTerminated      Indicates whether the
   *                                   subordinate modify DN plugin
   *                                   terminated the client
   *                                   connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   subordinate modify DN plugins
   *                                   should be invoked for this
   *                                   operation.
   * @param  abortModifyDNOperation    Indicates whether the modify DN
   *                                   operation should be aborted and
   *                                   all associated changes
   *                                   discarded.
   */
  public SubordinateModifyDNPluginResult(boolean connectionTerminated,
              boolean continuePluginProcessing,
              boolean abortModifyDNOperation)
  {
    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.abortModifyDNOperation   = abortModifyDNOperation;
  }



  /**
   * Indicates whether the subordinate modify DN plugin terminated the
   * client connection.
   *
   * @return  {@code true} if the subordinate modify DN plugin
   *          terminated the client connection, or {@code false} if
   *          not.
   */
  public boolean connectionTerminated()
  {
    return connectionTerminated;
  }



  /**
   * Indicates whether any further subordinate modify DN plugins
   * should be invoked for this operation.
   *
   * @return  {@code true} if any further subordinate modify DN
   *          plugins should be invoked for this operation, or
   *          {@code false} if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Indicates whether the modify DN operation should be aborted and
   * all associated changes discarded.
   *
   * @return  {@code true} if the modify DN operation should be
   *          aborted, or {@code false} if not.
   */
  public boolean abortModifyDNOperation()
  {
    return abortModifyDNOperation;
  }



  /**
   * Retrieves a string representation of this post-response plugin
   * result.
   *
   * @return  A string representation of this post-response plugin
   *          result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this subordinate modify DN
   * plugin result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("SubordinateModifyDNPluginResult(");
    buffer.append("connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(", abortModifyDNOperation=");
    buffer.append(abortModifyDNOperation);
    buffer.append(")");
  }
}

