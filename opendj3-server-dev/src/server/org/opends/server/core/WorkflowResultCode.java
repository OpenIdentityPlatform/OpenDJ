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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ResultCode;

/**
 * This class implements the workflow result code. The workflow result code
 * contains an LDAP result code along with an LDAP error message.
 */
public class WorkflowResultCode
{
  /** The global result code. */
  private ResultCode resultCode = ResultCode.UNDEFINED;

  /** The global error message. */
  private LocalizableMessageBuilder errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);

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
  public WorkflowResultCode(ResultCode resultCode, LocalizableMessageBuilder errorMessage)
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
   * Sometimes, a referral result code has to be turned into a reference entry.
   * When such case is occurring the elaborateGlobalResultCode method
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
   * @param newErrorMessage  the new error message associated to the new error code
   * @return <code>true</code> if a referral result code must be turned into a reference entry
   */
  public boolean elaborateGlobalResultCode(ResultCode newResultCode, LocalizableMessageBuilder newErrorMessage)
  {
    // if global result code has not been set yet then just take the new
    // result code as is
    if (resultCode == ResultCode.UNDEFINED)
    {
      resultCode   = newResultCode;
      errorMessage = new LocalizableMessageBuilder (newErrorMessage);
      return false;
    }


    // Elaborate the new result code (see table in the description header).
    switch (newResultCode.asEnum())
    {
    case SUCCESS:
      switch (resultCode.asEnum())
      {
        case NO_SUCH_OBJECT:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return false;
        case REFERRAL:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return true;
        default:
          // global resultCode remains the same
          return false;
      }

    case NO_SUCH_OBJECT:
      // global resultCode remains the same
      return false;

    case REFERRAL:
      switch (resultCode.asEnum())
      {
        case REFERRAL:
          resultCode = ResultCode.SUCCESS;
          errorMessage = new LocalizableMessageBuilder(LocalizableMessage.EMPTY);
          return true;
        case NO_SUCH_OBJECT:
          resultCode = ResultCode.REFERRAL;
          errorMessage = new LocalizableMessageBuilder (LocalizableMessage.EMPTY);
          return false;
        default:
          // global resultCode remains the same
          return true;
      }

    default:
      switch (resultCode.asEnum())
      {
        case REFERRAL:
          resultCode = newResultCode;
          errorMessage = new LocalizableMessageBuilder (newErrorMessage);
          return true;
        case SUCCESS:
        case NO_SUCH_OBJECT:
          resultCode = newResultCode;
          errorMessage = new LocalizableMessageBuilder (newErrorMessage);
          return false;
        default:
          // Do nothing (we don't want to override the first error)
          return false;
      }
    }
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
  public LocalizableMessageBuilder errorMessage()
  {
    return errorMessage;
  }

}
