/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.i18n.LocalizableMessage;



/**
 * This class defines an exception that may be thrown if the operation
 * being processed is cancelled for some reason (e.g., an abandon or
 * cancel request from the client).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class CanceledOperationException
    extends IdentifiedException
{
  /**
   * The serial version identifier required to satisfy the compiler
   * because this class extends <CODE>java.lang.Exception</CODE>,
   * which implements the <CODE>java.io.Serializable</CODE> interface.
   * This value was generated using the <CODE>serialver</CODE>
   * command-line utility included with the Java SDK.
   */
  private static final long serialVersionUID = -1936491673256446966L;



  /**
   * The cancel result that provides information about the status of
   * the cancellation.
   */
  private final CancelRequest cancelRequest;



  /**
   * Creates a new cancelled operation exception with the provided
   * result and no additional message.
   *
   * @param  cancelRequest  The result of the cancel processing.
   */
  public CanceledOperationException(CancelRequest cancelRequest)
  {
    super();


    this.cancelRequest = cancelRequest;
  }



  /**
   * Creates a new cancelled operation exception with the provided
   * information.
   *
   * @param  cancelRequest The request of the cancel processing.
   * @param  message       The message providing additional
   *                       information about the cancel processing, or
   *                       <CODE>null</CODE> if there is no message.
   */
  public CanceledOperationException(CancelRequest cancelRequest,
                                     LocalizableMessage message)
  {
    super(message);


    this.cancelRequest = cancelRequest;
  }



  /**
   * Retrieves the cancel request for this cancelled operation
   * exception.
   *
   * @return  The cancel request for this cancelled operation
   *          exception.
   */
  public CancelRequest getCancelRequest()
  {
    return cancelRequest;
  }
}

