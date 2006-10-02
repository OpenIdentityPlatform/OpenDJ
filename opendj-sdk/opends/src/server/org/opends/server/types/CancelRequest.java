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
package org.opends.server.types;



import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a data structure that can be used to hold
 * information about a request to cancel or abandon an operation in
 * progress.
 */
public class CancelRequest
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.CancelRequest";



  // Indicates whether to send a response to the original request if
  // the operation is canceled.
  private final boolean notifyOriginalRequestor;

  // A message that explains the purpose for this cancellation (may be
  // included in the response to the original requestor).
  private final String cancelReason;

  // A buffer to hold a human-readable response that the server
  // provided for the result of the cancellation.
  private StringBuilder responseMessage;



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
                       String cancelReason)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(notifyOriginalRequestor),
                            String.valueOf(cancelReason));

    this.notifyOriginalRequestor = notifyOriginalRequestor;
    this.cancelReason            = cancelReason;
    this.responseMessage         = new StringBuilder();
  }



  /**
   * Creates a new cancel request with the provided information.
   *
   * @param  notifyOriginalRequestor  Indicates whether the original
   *                                  requestor should receive a
   *                                  response if the operation is
   *                                  canceled.
   * @param  cancelReason             A message that explains the
   *                                  purpose for this cancellation.
   * @param  responseMessage          A buffer to hold a
   *                                  human-readable response that the
   *                                  server provided for the result
   *                                  of the cancellation.
   */
  public CancelRequest(boolean notifyOriginalRequestor,
                       String cancelReason,
                       StringBuilder responseMessage)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(notifyOriginalRequestor),
                            String.valueOf(cancelReason),
                            "java.lang.StringBuilder");

    this.notifyOriginalRequestor = notifyOriginalRequestor;
    this.cancelReason            = cancelReason;
    this.responseMessage         = responseMessage;
  }



  /**
   * Indicates whether the original requestor should receive a
   * response to the request if the operation is canceled.
   *
   * @return  <CODE>true</CODE> if the original requestor should
   *          receive a response if the operation is canceled, or
   *          <CODE>false</CODE> if not.
   */
  public final boolean notifyOriginalRequestor()
  {
    assert debugEnter(CLASS_NAME, "notifyOriginalRequestor");

    return notifyOriginalRequestor;
  }



  /**
   * Retrieves a message that explains the purpose for this
   * cancellation.
   *
   * @return  A message that explains the purpose for this
   *          cancellation.
   */
  public final String getCancelReason()
  {
    assert debugEnter(CLASS_NAME, "getCancelReason");

    return cancelReason;
  }



  /**
   * Retrieves the buffer that is used to hold a human-readable
   * response that the server provided for the result of the
   * cancellation.  The caller may alter the contents of this buffer.
   *
   * @return  The buffer that is used to hold a human-readable
   *          response that the server provided for the result of this
   *          cancellation.
   */
  public final StringBuilder getResponseMessage()
  {
    assert debugEnter(CLASS_NAME, "getResponseMessage");

    return responseMessage;
  }



  /**
   * Appends the provided message to the buffer used to hold
   * information about the result of the cancellation.
   *
   * @param  message  The message to append to the response message
   *                  buffer.
   */
  public final void addResponseMessage(String message)
  {
    assert debugEnter(CLASS_NAME, "addResponseMessage",
                      String.valueOf(message));

    responseMessage.append(message);
  }
}

