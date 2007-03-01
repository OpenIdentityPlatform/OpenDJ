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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.util.args;



import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;



/**
 * This class defines an argument type that will be used to represent Boolean
 * values.  These arguments will never take values and will never be required.
 * If the argument is provided, then it will be considered true, and if not then
 * it will be considered false.  As such, the default value will always be
 * "false".
 */
public class BooleanArgument
       extends Argument
{
  /**
   * Creates a new Boolean argument with the provided information.
   *
   * @param  name              The generic name that should be used to refer to
   *                           this argument.
   * @param  shortIdentifier   The single-character identifier for this
   *                           argument, or <CODE>null</CODE> if there is none.
   * @param  longIdentifier    The long identifier for this argument, or
   *                           <CODE>null</CODE> if there is none.
   * @param  descriptionID     The unique ID of the description for this
   *                           argument.
   * @param  descriptionArgs   The arguments that are to be used when generating
   *                           the description for this argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public BooleanArgument(String name, Character shortIdentifier,
                         String longIdentifier, int descriptionID,
                         Object... descriptionArgs)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, false, false, false, null,
          null, null, descriptionID, descriptionArgs);
  }



  /**
   * Indicates whether the provided value is acceptable for use in this
   * argument.
   *
   * @param  valueString    The value for which to make the determination.
   * @param  invalidReason  A buffer into which the invalid reason may be
   *                        written if the value is not acceptable.
   *
   * @return  <CODE>true</CODE> if the value is acceptable, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean valueIsAcceptable(String valueString,
                                   StringBuilder invalidReason)
  {
    // This argument type should never have a value, so any value provided will
    // be unacceptable.
    int msgID = MSGID_BOOLEANARG_NO_VALUE_ALLOWED;
    invalidReason.append(getMessage(msgID, getName()));

    return false;
  }
}

