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
package org.opends.server.types.operation;
import org.opends.messages.Message;


import org.opends.server.types.Control;
import org.opends.server.types.DN;

import org.opends.messages.MessageBuilder;


/**
 * This class defines a set of methods that are available for use by
 * pre-operation plugins for all types of operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PreOperationOperation
       extends PluginOperation
{
  /**
   * Adds the provided control to the set of controls to include in
   * the response to the client.
   *
   * @param  control  The control to add to the set of controls to
   *                  include in the response to the client.
   */
  public void addResponseControl(Control control);



  /**
   * Removes the provided control from the set of controls to include
   * in the response to the client.
   *
   * @param  control  The control to remove from the set of controls
   *                  to include in the response to the client.
   */
  public void removeResponseControl(Control control);



  /**
   * Retrieves the error message for this operation.  Its contents may
   * be altered by the caller.
   *
   * @return  The error message for this operation.
   */
  public MessageBuilder getErrorMessage();



  /**
   * Specifies the error message for this operation.
   *
   * @param  errorMessage  The error message for this operation.
   */
  public void setErrorMessage(MessageBuilder errorMessage);



  /**
   * Appends the provided message to the error message buffer.  If the
   * buffer has not yet been created, then this will create it first
   * and then add the provided message.
   *
   * @param  message  The message to append to the error message
   *                  buffer.
   */
  public void appendErrorMessage(Message message);



  /**
   * Retrieves the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.  The contents of this buffer may be altered by the
   * caller.
   *
   * @return  The additional log message for this operation.
   */
  public MessageBuilder getAdditionalLogMessage();



  /**
   * Specifies the additional log message for this operation, which
   * should be written to the log but not included in the response to
   * the client.
   *
   * @param  additionalLogMessage  The additional log message for this
   */
  public void setAdditionalLogMessage(
                   MessageBuilder additionalLogMessage);



  /**
   * Appends the provided message to the additional log information
   * for this operation.
   *
   * @param  message  The message that should be appended to the
   *                  additional log information for this operation.
   */
  public void appendAdditionalLogMessage(Message message);



  /**
   * Retrieves the authorization DN for this operation.  In many
   * cases, it will be the same as the DN of the authenticated user
   * for the underlying connection, or the null DN if no
   * authentication has been performed on that connection.  However,
   * it may be some other value if special processing has been
   * requested (e.g., the operation included a proxied authorization
   * control).
   *
   * @return  The authorization DN for this operation.
   */
  public DN getAuthorizationDN();
}

