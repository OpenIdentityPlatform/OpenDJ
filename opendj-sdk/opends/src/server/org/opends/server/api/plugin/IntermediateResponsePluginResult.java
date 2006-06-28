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
package org.opends.server.api.plugin;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that holds information about
 * the result of processing by an intermediate response plugin.
 */
public class IntermediateResponsePluginResult
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.api.plugin." +
            "IntermediateResponsePluginResult";



  // Indicates whether any further intermediate response plugins
  // should be invoked for this operation.
  private boolean continuePluginProcessing;

  // Indicates whether processing should continue for the associated
  // operation.
  private boolean continueOperation;

  // Indicates whether the intermediate response plugin terminated the
  // client connection.
  private boolean connectionTerminated;

  // Indicates whether the associated intermediate response message
  // should be sent to the client.
  private boolean sendIntermediateResponse;



  /**
   * Creates a new intermediate response plugin result with the
   * default settings.  In this case, it will indicate that the
   * connection has not been terminated, that further plugin
   * processing should continue, that the intermediate response should
   * be returned to the client, and that processing on the associated
   * operation should continue.
   */
  public IntermediateResponsePluginResult()
  {
    assert debugConstructor(CLASS_NAME);

    this.connectionTerminated     = false;
    this.continuePluginProcessing = true;
    this.sendIntermediateResponse = true;
    this.continueOperation        = true;
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
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(connectionTerminated),
                            String.valueOf(continuePluginProcessing),
                            String.valueOf(sendIntermediateResponse),
                            String.valueOf(continueOperation));

    this.connectionTerminated     = connectionTerminated;
    this.continuePluginProcessing = continuePluginProcessing;
    this.sendIntermediateResponse = sendIntermediateResponse;
    this.continueOperation        = continueOperation;
  }



  /**
   * Indicates whether the intermediate response plugin terminated the
   * client connection.
   *
   * @return  <CODE>true</CODE> if the intermediate response plugin
   *          terminated the client connection, or <CODE>false</CODE>
   *          if not.
   */
  public boolean connectionTerminated()
  {
    assert debugEnter(CLASS_NAME, "connectionTerminated");

    return connectionTerminated;
  }



  /**
   * Specifies whether the intermediate response plugin terminated the
   * client connection.
   *
   * @param  connectionTerminated  Specifies whether the intermediate
   *                               response plugin terminated the
   *                               client connection.
   */
  public void setConnectionTerminated(boolean connectionTerminated)
  {
    assert debugEnter(CLASS_NAME, "setConnectionTerminated",
                      String.valueOf(connectionTerminated));

    this.connectionTerminated = connectionTerminated;
  }



  /**
   * Indicates whether any further intermediate response plugins
   * should be invoked for this operation.
   *
   * @return  <CODE>true</CODE> if any further intermediate response
   *          plugins should be invoked for this operation, or
   *          <CODE>false</CODE> if not.
   */
  public boolean continuePluginProcessing()
  {
    assert debugEnter(CLASS_NAME, "continuePluginProcessing");

    return continuePluginProcessing;
  }



  /**
   * Specifies whether any further intermediate response plugins
   * should be invoked for this operation.
   *
   * @param  continuePluginProcessing  Specifies whether any further
   *                                   intermediate response plugins
   *                                   should be invoked for this
   *                                   operation.
   */
  public void setContinuePluginProcessing(
                   boolean continuePluginProcessing)
  {
    assert debugEnter(CLASS_NAME, "setContinuePluginProcessing",
                      String.valueOf(continuePluginProcessing));

    this.continuePluginProcessing = continuePluginProcessing;
  }



  /**
   * Indicates whether the associated intermediate response message
   * should be sent to the client.
   *
   * @return  <CODE>true</CODE> if the associated intermediate
   *          response message should be sent to the client, or
   *          <CODE>false</CODE> if not.
   */
  public boolean sendIntermediateResponse()
  {
    assert debugEnter(CLASS_NAME, "sendIntermediateResponse");

    return sendIntermediateResponse;
  }



  /**
   * Specifies whether the associated intermediate response message
   * should be sent to the client.
   *
   * @param  sendIntermediateResponse  Specifies whether the
   *                                   associated intermediate
   *                                   response message should be sent
   *                                   to the client.
   */
  public void setSendIntermediateResponse(
                   boolean sendIntermediateResponse)
  {
    assert debugEnter(CLASS_NAME, "setSendIntermediateResponse",
                      String.valueOf(sendIntermediateResponse));

    this.sendIntermediateResponse = sendIntermediateResponse;
  }



  /**
   * Indicates whether processing should continue for the associated
   * operation.
   *
   * @return  <CODE>true</CODE> if processing on the operation should
   *          continue, or <CODE>false</CODE> if not.
   */
  public boolean continueOperation()
  {
    assert debugEnter(CLASS_NAME, "continueOperation");

    return continueOperation;
  }



  /**
   * Specifies whether processing should continue for the associated
   * operation.
   *
   * @param  continueOperation  Specifies whether processing should
   *                            continue for the associated operation.
   */
  public void setContinueOperation(boolean continueOperation)
  {
    assert debugEnter(CLASS_NAME, "setContinueOperation",
                      String.valueOf(continueOperation));

    this.continueOperation = continueOperation;
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
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

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

