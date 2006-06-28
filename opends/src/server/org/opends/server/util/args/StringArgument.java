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
package org.opends.server.util.args;



/**
 * This class defines an argument type that will accept any string value.
 */
public class StringArgument
       extends Argument
{
  /**
   * Creates a new string argument with the provided information.
   *
   * @param  name              The generic name that should be used to refer to
   *                           this argument.
   * @param  shortIdentifier   The single-character identifier for this
   *                           argument, or <CODE>null</CODE> if there is none.
   * @param  longIdentifier    The long identifier for this argument, or
   *                           <CODE>null</CODE> if there is none.
   * @param  isRequired        Indicates whether this argument must be specified
   *                           on the command line.
   * @param  needsValue        Indicates whether this argument requires a value.
   * @param  valuePlaceholder  The placeholder for the argument value that will
   *                           be displayed in usage information, or
   *                           <CODE>null</CODE> if this argument does not
   *                           require a value.
   * @param  descriptionID     The unique ID of the description for this
   *                           argument.
   * @param  descriptionArgs   The arguments that are to be used when generating
   *                           the description for this argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public StringArgument(String name, Character shortIdentifier,
                        String longIdentifier, boolean isRequired,
                        boolean needsValue, String valuePlaceholder,
                        int descriptionID, Object... descriptionArgs)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, false, needsValue,
          valuePlaceholder, null, null, descriptionID, descriptionArgs);
  }



  /**
   * Creates a new string argument with the provided information.
   *
   * @param  name              The generic name that should be used to refer to
   *                           this argument.
   * @param  shortIdentifier   The single-character identifier for this
   *                           argument, or <CODE>null</CODE> if there is none.
   * @param  longIdentifier    The long identifier for this argument, or
   *                           <CODE>null</CODE> if there is none.
   * @param  isRequired        Indicates whether this argument must be specified
   *                           on the command line.
   * @param  isMultiValued     Indicates whether this argument may be specified
   *                           more than once to provide multiple values.
   * @param  needsValue        Indicates whether this argument requires a value.
   * @param  valuePlaceholder  The placeholder for the argument value that will
   *                           be displayed in usage information, or
   *                           <CODE>null</CODE> if this argument does not
   *                           require a value.
   * @param  defaultValue      The default value that should be used for this
   *                           argument if none is provided in a properties file
   *                           or on the command line.  This may be
   *                           <CODE>null</CODE> if there is no generic default.
   * @param  propertyName      The name of the property in a property file that
   *                           may be used to override the default value but
   *                           will be overridden by a command-line argument.
   * @param  descriptionID     The unique ID of the description for this
   *                           argument.
   * @param  descriptionArgs   The arguments that are to be used when generating
   *                           the description for this argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public StringArgument(String name, Character shortIdentifier,
                        String longIdentifier, boolean isRequired,
                        boolean isMultiValued, boolean needsValue,
                        String valuePlaceholder, String defaultValue,
                        String propertyName, int descriptionID,
                        Object... descriptionArgs)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, isMultiValued,
          needsValue, valuePlaceholder, defaultValue, propertyName,
          descriptionID, descriptionArgs);
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
    // All values will be acceptable for this argument.
    return true;
  }
}

