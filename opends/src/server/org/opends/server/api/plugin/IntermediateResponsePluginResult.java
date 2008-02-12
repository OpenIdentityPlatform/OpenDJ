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



/**
 * This class defines a data structure that holds information about
 * the result of processing by an intermediate response plugin.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class IntermediateResponsePluginResult
{
  /**
   * An intermediate response plugin result instance that indicates
   * all processing was successful.
   */
  public static final IntermediateResponsePluginResult SUCCESS =
       new IntermediateResponsePluginResult();



  // Indicates whether any further intermediate response plugins
  // should be invoked for this operation.
  private final boolean continuePluginProcessing;

  // Indicates whether processing should continue for the associated
  // operation.
  private final boolean continueOperation;

  // Indicates whether the intermediate response plugin terminated the
  // client connection.
  private final boolean connectionTerminated;

  // Indicates whether the associated intermediate response message
  // should be sent to the client.
  private final boolean sendIntermediateResponse;



  /**
   * Creates a new intermediate response plugin result with the
   * default settings.  In this case, it will indicate that the
   * connection has not been terminated, that further plugin
   * processing should continue, that the intermediate response should
   * be returned to the client, and that processing on the associated
   * operation should continue.
   */
  private IntermediateResponsePluginResult()
  {
    this(false, true, true, true);
  }



  /**
   * Creates a new intermediate response plugin result with the
   * provided information.
   *
   * @param  connectionTerminated      Indicates whether the
   *                                   intermediate response plugin
   *                                   terminated the client
   *                                   connection.
   * @param  continuePluginProcessing  Indicates whether any further
   *                                   intermediate response plugins
   *                                   should be invoked for this
   *                                   operation.
   * @param  sendIntermediateResponse  Indicates whether the
   *                                   intermediate response message
   *                                   should be sent to the client.
   * @param  continueOperation         Indicates whether the server
   *                                   should continue processing on
   *                                   the associated operation.
   */
  public IntermediateResponsePluginResult(
              boolean connectionTerminated,
              boolean continuePluginProcessing,
              boolean sendIntermediateResponse,
              boolean continueOperation)
  {
    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.sendIntermediateResponse = sendIntermediateResponse;
    this.continueOperation        = continueOperation;
  }



  /**
   * Indicates whether the intermediate response plugin terminated the
   * client connection.
   *
   * @return  {@code true} if the intermediate response plugin
   *          terminated the client connection, or {@code false} if
   *          not.
   */
  public boolean connectionTerminated()
  {
    return connectionTerminated;
  }



  /**
   * Indicates whether any further intermediate response plugins
   * should be invoked for this operation.
   *
   * @return  {@code true} if any further intermediate response
   *          plugins should be invoked for this operation, or
   *          {@code false} if not.
   */
  public boolean continuePluginProcessing()
  {
    return continuePluginProcessing;
  }



  /**
   * Indicates whether the associated intermediate response message
   * should be sent to the client.
   *
   * @return  {@code true} if the associated intermediate response
   *          message should be sent to the client, or {@code false}
   *          if not.
   */
  public boolean sendIntermediateResponse()
  {
    return sendIntermediateResponse;
  }



  /**
   * Indicates whether processing should continue for the associated
   * operation.
   *
   * @return  {@code true} if processing on the operation should
   *          continue, or {@code false} if not.
   */
  public boolean continueOperation()
  {
    return continueOperation;
  }



  /**
   * Retrieves a string representation of this intermediate response
   * plugin result.
   *
   * @return  A string representation of this intermediate response
   *          plugin result.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this intermediate response
   * plugin result to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("IntermediateResponsePluginResult(" +
                  "connectionTerminated=");
    buffer.append(connectionTerminated);
    buffer.append(", continuePluginProcessing=");
    buffer.append(continuePluginProcessing);
    buffer.append(", sendIntermediateResponse=");
    buffer.append(sendIntermediateResponse);
    buffer.append(", continueOperation=");
    buffer.append(continueOperation);
    buffer.append(")");
  }
}

