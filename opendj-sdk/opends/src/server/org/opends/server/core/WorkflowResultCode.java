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
package org.opends.server.core;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import org.opends.server.types.ResultCode;


/**
 * This class implements the workflow result code. The workflow result code
 * contains an LDAP result code along with an LDAP error message.
 */
public class WorkflowResultCode
{
  // The global result code.
  private ResultCode resultCode = ResultCode.UNDEFINED;

  // The global error message.
  private MessageBuilder errorMessage = new MessageBuilder(Message.EMPTY);


  /**
   * Creates a new instance of a workflow result. By default the result code
   * is set to UNDEFINED and there is no error message.
   */
  public WorkflowResultCode()
  {
    // Nothing to implement.
  }


  /**
   * Creates a new instance of a workflow result code and initializes it
   * with a result code and an error message.
   *
   * @param resultCode    the initial value for the result code
   * @param errorMessage  the initial value for the error message
   */
  public WorkflowResultCode(
      ResultCode     resultCode,
      MessageBuilder errorMessage
      )
  {
    this.resultCode   = resultCode;
    this.errorMessage = errorMessage;
  }


  /**
   * Elaborates a global result code. A workflow may execute an operation
   * on several subordinate workflows. In such case, the parent workflow
   * has to take into account all the subordinate result codes to elaborate
   * a global result code.
   *
   * Sometimes, a referral result code has to be turned into a reference
   * entry. When such case is occurring the elaborateGlobalResultCode method
   * will return true.
   *
   * The global result code is elaborated as follows:
   *
   * <PRE>
   *  -----------+------------+------------+-------------------------------
   *  new        | current    | resulting  |
   *  resultCode | resultCode | resultCode | action
   *  -----------+------------+------------+-------------------------------
   *  SUCCESS      NO_SUCH_OBJ  SUCCESS      -
   *               REFERRAL     SUCCESS      send reference entry to client
   *               other        [unchanged]  -
   *  ---------------------------------------------------------------------
   *  NO_SUCH_OBJ  SUCCESS      [unchanged]  -
   *               REFERRAL     [unchanged]  -
   *               other        [unchanged]  -
   *  ---------------------------------------------------------------------
   *  REFERRAL     SUCCESS      [unchanged]  send reference entry to client
   *               REFERRAL     SUCCESS      send reference entry to client
   *               NO_SUCH_OBJ  REFERRAL     -
   *               other        [unchanged]  send reference entry to client
   *  ---------------------------------------------------------------------
   *  others       SUCCESS      other        -
   *               REFERRAL     other        send reference entry to client
   *               NO_SUCH_OBJ  other        -
   *               other2       [unchanged]  -
   *  ---------------------------------------------------------------------
   * </PRE>
   *
   * @param newResultCode    the new result code to take into account
   * @param newErrorMessage  the new error message associated to the new
   *                         error code
   * @return <code>true</code> if a referral result code must be turned
   *         into a reference entry
   */
  public boolean elaborateGlobalResultCode(
      ResultCode     newResultCode,
      MessageBuilder newErrorMessage
      )
  {
    // Returned value
    boolean sendReferenceEntry = false;

    // if global result code has not been set yet then just take the new
    // result code as is
    if (resultCode == ResultCode.UNDEFINED)
    {
      resultCode   = newResultCode;
      errorMessage = new MessageBuilder (newErrorMessage);
    }
    else
    {
      // Elaborate the new result code (see table in the description header).

      switch (newResultCode)
      {
      case SUCCESS:
        //
        // Received SUCCESS
        // ----------------
        //
        switch (resultCode)
        {
          case NO_SUCH_OBJECT:
            resultCode = ResultCode.SUCCESS;
            errorMessage = new MessageBuilder(Message.EMPTY);
            break;
          case REFERRAL:
            resultCode = ResultCode.SUCCESS;
            errorMessage = new MessageBuilder(Message.EMPTY);
            sendReferenceEntry = true;
            break;
          default:
            // global resultCode remains the same
            break;
        }
        break;
      case NO_SUCH_OBJECT:
        //
        // Received NO SUCH OBJECT
        // -----------------------
        //
        // global resultCode remains the same
        break;
      case REFERRAL:
        //
        // Received REFERRAL
        // -----------------
        //
        switch (resultCode)
        {
          case REFERRAL:
            resultCode = ResultCode.SUCCESS;
            errorMessage = new MessageBuilder(Message.EMPTY);
            sendReferenceEntry = true;
            break;
          case NO_SUCH_OBJECT:
            resultCode = ResultCode.REFERRAL;
            errorMessage = new MessageBuilder (Message.EMPTY);
            break;
          default:
            // global resultCode remains the same
            sendReferenceEntry = true;
            break;
        }
        break;
      default:
        //
        // Received other result codes
        // ---------------------------
        //
        switch (resultCode)
        {
          case REFERRAL:
            resultCode = newResultCode;
            errorMessage = new MessageBuilder (newErrorMessage);
            sendReferenceEntry = true;
            break;
          case SUCCESS:
            resultCode = newResultCode;
            errorMessage = new MessageBuilder (newErrorMessage);
            break;
          case NO_SUCH_OBJECT:
            resultCode = newResultCode;
            errorMessage = new MessageBuilder (newErrorMessage);
            break;
          default:
            // global resultCode remains the same but append the new
            // error message into the current error message
            if (errorMessage == null)
            {
              errorMessage =  new MessageBuilder (newErrorMessage);
            }
            else
            {
              errorMessage.append(newErrorMessage);
            }
            break;
        }
        break;
      }
    }

    return sendReferenceEntry;
  }


  /**
   * Returns the global result code.
   *
   * @return the global result code.
   */
  public ResultCode resultCode()
  {
    return resultCode;
  }


  /**
   * Returns the global error message.
   *
   * @return the global error message.
   */
  public MessageBuilder errorMessage()
  {
    return errorMessage;
  }

}
