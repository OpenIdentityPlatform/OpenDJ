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
package org.opends.server.util.args;
import org.opends.messages.Message;



import static org.opends.messages.UtilityMessages.*;
import org.opends.messages.MessageBuilder;


/**
 * This class defines an argument type that will only accept integer values,
 * and potentially only those in a given range.
 */
public class IntegerArgument
       extends Argument
{
  // Indicates whether a lower bound will be enforced for this argument.
  private boolean hasLowerBound;

  // Indicates whether an upper bound will be enforced for this argument.
  private boolean hasUpperBound;

  // The lower bound that will be enforced for this argument.
  private int lowerBound;

  // The upper bound that will be enforced for this argument.
  private int upperBound;



  /**
   * Creates a new integer argument with the provided information.
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
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public IntegerArgument(String name, Character shortIdentifier,
                         String longIdentifier, boolean isRequired,
                         boolean needsValue, Message valuePlaceholder,
                         Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, false, needsValue,
          valuePlaceholder, null, null, description);

    hasLowerBound = false;
    hasUpperBound = false;
    lowerBound    = Integer.MIN_VALUE;
    upperBound    = Integer.MAX_VALUE;
  }



  /**
   * Creates a new integer argument with the provided information.
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
   * @param  hasLowerBound     Indicates whether a lower bound should be
   *                           enforced for values of this argument.
   * @param  lowerBound        The lower bound that should be enforced for
   *                           values of this argument.
   * @param  hasUpperBound     Indicates whether an upperbound should be
   *                           enforced for values of this argument.
   * @param  upperBound        The upper bound that should be enforced for
   *                           values of this argument.
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public IntegerArgument(String name, Character shortIdentifier,
                         String longIdentifier, boolean isRequired,
                         boolean needsValue, Message valuePlaceholder,
                         boolean hasLowerBound, int lowerBound,
                         boolean hasUpperBound, int upperBound,
                         Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, false, needsValue,
          valuePlaceholder, null, null, description);

    this.hasLowerBound = hasLowerBound;
    this.hasUpperBound = hasUpperBound;
    this.lowerBound    = lowerBound;
    this.upperBound    = upperBound;

    if (hasLowerBound && hasUpperBound && (lowerBound > upperBound))
    {
      Message message = ERR_INTARG_LOWER_BOUND_ABOVE_UPPER_BOUND.get(
          name, lowerBound, upperBound);
      throw new ArgumentException(message);
    }
  }



  /**
   * Creates a new integer argument with the provided information.
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
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public IntegerArgument(String name, Character shortIdentifier,
                         String longIdentifier, boolean isRequired,
                         boolean isMultiValued, boolean needsValue,
                         Message valuePlaceholder, int defaultValue,
                         String propertyName,
                         Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, isMultiValued,
          needsValue, valuePlaceholder, String.valueOf(defaultValue),
          propertyName, description);

    hasLowerBound = false;
    hasUpperBound = false;
    lowerBound    = Integer.MIN_VALUE;
    upperBound    = Integer.MAX_VALUE;
  }



  /**
   * Creates a new integer argument with the provided information.
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
   * @param  hasLowerBound     Indicates whether a lower bound should be
   *                           enforced for values of this argument.
   * @param  lowerBound        The lower bound that should be enforced for
   *                           values of this argument.
   * @param  hasUpperBound     Indicates whether an upperbound should be
   *                           enforced for values of this argument.
   * @param  upperBound        The upper bound that should be enforced for
   *                           values of this argument.
   * @param  description       Message for the description of this
   *                           argument.
   *
   * @throws  ArgumentException  If there is a problem with any of the
   *                             parameters used to create this argument.
   */
  public IntegerArgument(String name, Character shortIdentifier,
                         String longIdentifier, boolean isRequired,
                         boolean isMultiValued, boolean needsValue,
                         Message valuePlaceholder, int defaultValue,
                         String propertyName, boolean hasLowerBound,
                         int lowerBound, boolean hasUpperBound, int upperBound,
                         Message description)
         throws ArgumentException
  {
    super(name, shortIdentifier, longIdentifier, isRequired, isMultiValued,
          needsValue, valuePlaceholder, String.valueOf(defaultValue),
          propertyName, description);

    this.hasLowerBound = hasLowerBound;
    this.hasUpperBound = hasUpperBound;
    this.lowerBound    = lowerBound;
    this.upperBound    = upperBound;

    if (hasLowerBound && hasUpperBound && (lowerBound > upperBound))
    {
      Message message = ERR_INTARG_LOWER_BOUND_ABOVE_UPPER_BOUND.get(
          name, lowerBound, upperBound);
      throw new ArgumentException(message);
    }
  }



  /**
   * Indicates whether a lower bound should be enforced for values of this
   * argument.
   *
   * @return  <CODE>true</CODE> if a lower bound should be enforced for values
   *          of this argument, or <CODE>false</CODE> if not.
   */
  public boolean hasLowerBound()
  {
    return hasLowerBound;
  }



  /**
   * Retrieves the lower bound that may be enforced for values of this argument.
   *
   * @return  The lower bound that may be enforced for values of this argument.
   */
  public int getLowerBound()
  {
    return lowerBound;
  }



  /**
   * Indicates whether a upper bound should be enforced for values of this
   * argument.
   *
   * @return  <CODE>true</CODE> if a upper bound should be enforced for values
   *          of this argument, or <CODE>false</CODE> if not.
   */
  public boolean hasUpperBound()
  {
    return hasUpperBound;
  }



  /**
   * Retrieves the upper bound that may be enforced for values of this argument.
   *
   * @return  The upper bound that may be enforced for values of this argument.
   */
  public int getUpperBound()
  {
    return upperBound;
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
                                   MessageBuilder invalidReason)
  {
    // First, the value must be decodable as an integer.
    int intValue;
    try
    {
      intValue = Integer.parseInt(valueString);
    }
    catch (Exception e)
    {
      invalidReason.append(ERR_ARG_CANNOT_DECODE_AS_INT.get(
              valueString, getName()));
      return false;
    }


    // If there is a lower bound, then the value must be greater than or equal
    // to it.
    if (hasLowerBound && (intValue < lowerBound))
    {
      invalidReason.append(ERR_INTARG_VALUE_BELOW_LOWER_BOUND.get(
              getName(), intValue, lowerBound));
      return false;
    }


    // If there  is an upper bound, then the value must be less than or equal to
    // it.
    if (hasUpperBound && (intValue > upperBound))
    {

      invalidReason.append(ERR_INTARG_VALUE_ABOVE_UPPER_BOUND.get(
              getName(), intValue, upperBound));
      return false;
    }


    // At this point, the value should be acceptable.
    return true;
  }
}

