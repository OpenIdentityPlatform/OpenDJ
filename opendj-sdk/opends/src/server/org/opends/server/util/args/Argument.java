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



import java.util.Iterator;
import java.util.LinkedList;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a generic argument that may be used in the argument list
 * for an application.  This is an abstract class that must be subclassed in
 * order to provide specific functionality.
 */
public abstract class Argument
{
  // Indicates whether this argument should be hidden in the usage information.
  private boolean isHidden;

  // Indicates whether this argument may be specified more than once for
  // multiple values.
  private boolean isMultiValued;

  // Indicates whether this argument was provided in the set of command-line
  // arguments.
  private boolean isPresent;

  // Indicates whether this argument is required to have a value.
  private boolean isRequired;

  // Indicates whether this argument requires a value.
  private boolean needsValue;

  // The single-character identifier for this argument.
  private Character shortIdentifier;

  // The unique ID of the description for this argument.
  private int descriptionID;

  // The set of values for this argument.
  private LinkedList<String> values;

  // The default value for the argument if none other is provided.
  private String defaultValue;

  // The description for this argument.
  private String description;

  // The long identifier for this argument.
  private String longIdentifier;

  // The generic name that will be used to refer to this argument.
  private String name;

  // The name of the property that can be used to set the default value.
  private String propertyName;

  // The value placeholder for this argument, which will be used in usage
  // information.
  private String valuePlaceholder;



  /**
   * Creates a new argument with the provided information.
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
  protected Argument(String name, Character shortIdentifier,
                     String longIdentifier, boolean isRequired,
                     boolean isMultiValued, boolean needsValue,
                     String valuePlaceholder, String defaultValue,
                     String propertyName, int descriptionID,
                     Object... descriptionArgs)
            throws ArgumentException
  {
    this.name             = name;
    this.shortIdentifier  = shortIdentifier;
    this.longIdentifier   = longIdentifier;
    this.isRequired       = isRequired;
    this.isMultiValued    = isMultiValued;
    this.needsValue       = needsValue;
    this.valuePlaceholder = valuePlaceholder;
    this.defaultValue     = defaultValue;
    this.propertyName     = propertyName;
    this.descriptionID    = descriptionID;
    this.description      = getMessage(descriptionID, descriptionArgs);

    if ((shortIdentifier == null) && (longIdentifier == null))
    {
      int   msgID    = MSGID_ARG_NO_IDENTIFIER;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }

    if (needsValue && (valuePlaceholder == null))
    {
      int    msgID   = MSGID_ARG_NO_VALUE_PLACEHOLDER;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }

    values    = new LinkedList<String>();
    isPresent = false;
    isHidden  = false;
  }



  /**
   * Retrieves the generic name that will be used to refer to this argument.
   *
   * @return  The generic name that will be used to refer to this argument.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves the single-character identifier that may be used to specify the
   * value of this argument.
   *
   * @return  The single-character identifier that may be used to specify the
   *          value of this argument, or <CODE>null</CODE> if there is none.
   */
  public Character getShortIdentifier()
  {
    return shortIdentifier;
  }



  /**
   * Retrieves the long (multi-character) identifier that may be used to specify
   * the value of this argument.
   *
   * @return  The long (multi-character) identifier that may be used to specify
   *          the value of this argument.
   */
  public String getLongIdentifier()
  {
    return longIdentifier;
  }



  /**
   * Indicates whether this argument is required to have at least one value.
   *
   * @return  <CODE>true</CODE> if this argument is required to have at least
   *          one value, or <CODE>false</CODE> if it does not need to have a
   *          value.
   */
  public boolean isRequired()
  {
    return isRequired;
  }



  /**
   * Specifies whether this argument is required to have at least one value.
   *
   * @param  isRequired  Indicates whether this argument is required to have at
   *                     least one value.
   */
  public void setRequired(boolean isRequired)
  {
    this.isRequired = isRequired;
  }



  /**
   * Indicates whether this argument is present in the parsed set of
   * command-line arguments.
   *
   * @return  <CODE>true</CODE> if this argument is present in the parsed set of
   *          command-line arguments, or <CODE>false</CODE> if not.
   */
  public boolean isPresent()
  {
    return isPresent;
  }



  /**
   * Specifies whether this argument is present in the parsed set of
   * command-line arguments.
   *
   * @param  isPresent  Indicates whether this argument is present in the set of
   *                    command-line arguments.
   */
  public void setPresent(boolean isPresent)
  {
    this.isPresent = isPresent;
  }



  /**
   * Indicates whether this argument should be hidden from the usage
   * information.
   *
   * @return  <CODE>true</CODE> if this argument should be hidden from the usage
   *          information, or <CODE>false</CODE> if not.
   */
  public boolean isHidden()
  {
    return isHidden;
  }



  /**
   * Specifies whether this argument should be hidden from the usage
   * information.
   *
   * @param  isHidden  Indicates whether this argument should be hidden from the
   *                   usage information.
   */
  public void setHidden(boolean isHidden)
  {
    this.isHidden = isHidden;
  }



  /**
   * Indicates whether this argument may be provided more than once on the
   * command line to specify multiple values.
   *
   * @return  <CODE>true</CODE> if this argument may be provided more than once
   *          on the command line to specify multiple values, or
   *          <CODE>false</CODE> if it may have at most one value.
   */
  public boolean isMultiValued()
  {
    return isMultiValued;
  }



  /**
   * Specifies whether this argument may be provided more than once on the
   * command line to specify multiple values.
   *
   * @param  isMultiValued  Indicates whether this argument may be provided more
   *                        than once on the command line to specify multiple
   *                        values.
   */
  public void setMultiValued(boolean isMultiValued)
  {
    this.isMultiValued = isMultiValued;
  }



  /**
   * Indicates whether a value must be provided with this argument if it is
   * present.
   *
   * @return  <CODE>true</CODE> if a value must be provided with the argument if
   *          it is present, or <CODE>false</CODE> if the argument does not take
   *          a value and the presence of the argument identifier itself is
   *          sufficient to convey the necessary information.
   */
  public boolean needsValue()
  {
    return needsValue;
  }



  /**
   * Specifies whether a value must be provided with this argument if it is
   * present.  If this is changed from <CODE>false</CODE> to <CODE>true</CODE>,
   * then a value placeholder must also be provided.
   *
   * @param  needsValue  Indicates whether a value must be provided with this
   *                     argument if it is present.

   */
  public void setNeedsValue(boolean needsValue)
  {
    this.needsValue = needsValue;
  }



  /**
   * Retrieves the value placeholder that will be displayed for this argument in
   * the generated usage information.
   *
   * @return  The value placeholder that will be displayed for this argument in
   *          the generated usage information, or <CODE>null</CODE> if there is
   *          none.
   */
  public String getValuePlaceholder()
  {
    return valuePlaceholder;
  }



  /**
   * Specifies the value placeholder that will be displayed for this argument in
   * the generated usage information.  It may be <CODE>null</CODE> only if
   * <CODE>needsValue()</CODE> returns <CODE>false</CODE>.
   *
   * @param  valuePlaceholder  The value placeholder that will be displayed for
   *                           this argument in the generated usage information.
   */
  public void setValuePlaceholder(String valuePlaceholder)
  {
    this.valuePlaceholder = valuePlaceholder;
  }



  /**
   * Retrieves the default value that will be used for this argument if it is
   * not specified on the command line and it is not set from a properties file.
   *
   * @return  The default value that will be used for this argument if it is not
   *          specified on the command line and it is not set from a properties
   *          file, or <CODE>null</CODE> if there is no default value.
   */
  public String getDefaultValue()
  {
    return defaultValue;
  }



  /**
   * Specifies the default value that will be used for this argument if it is
   * not specified on the command line and it is not set from a properties file.
   *
   * @param  defaultValue  The default value that will be used for this argument
   *                       if it is not specified on the command line and it is
   *                       not set from a properties file.
   */
  public void setDefaultValue(String defaultValue)
  {
    this.defaultValue = defaultValue;
  }



  /**
   * Retrieves the name of a property in a properties file that may be used to
   * set the default value for this argument if it is present.  A value read
   * from a properties file will override the default value returned from the
   * <CODE>getDefaultValue</CODE>, but the properties file value will be
   * overridden by a value supplied on the command line.
   *
   * @return  The name of a property in a properties file that may be used to
   *          set the default value for this argument if it is present.
   */
  public String getPropertyName()
  {
    return propertyName;
  }



  /**
   * Specifies the name of a property in a properties file that may be used to
   * set the default value for this argument if it is present.
   *
   * @param  propertyName  The name of a property in a properties file that may
   *                       be used to set the default value for this argument if
   *                       it is present.
   */
  public void setPropertyName(String propertyName)
  {
    this.propertyName = propertyName;
  }



  /**
   * Retrieves the unique ID for the description of this argument.
   *
   * @return  The unique ID for the description of this argument.
   */
  public int getDescriptionID()
  {
    return descriptionID;
  }



  /**
   * Retrieves the human-readable description for this argument.
   *
   * @return  The human-readable description for this argument.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Indicates whether this argument has at least one value.
   *
   * @return  <CODE>true</CODE> if this argument has at least one value, or
   *          <CODE>false</CODE> if it does not have any values.
   */
  public boolean hasValue()
  {
    return (! values.isEmpty());
  }



  /**
   * Retrieves the string vale for this argument.  If it has multiple values,
   * then the first will be returned.  If it does not have any values, then the
   * default value will be returned.
   *
   * @return  The string value for this argument, or <CODE>null</CODE> if there
   *          are no values and no default value has been given.
   */
  public String getValue()
  {
    if (values.isEmpty())
    {
      return defaultValue;
    }

    return values.getFirst();
  }



  /**
   * Retrieves the set of string values for this argument.
   *
   * @return  The set of string values for this argument.
   */
  public LinkedList<String> getValues()
  {
    return values;
  }



  /**
   * Retrieves the value of this argument as an integer.
   *
   * @return  The value of this argument as an integer.
   *
   * @throws  ArgumentException  If there are multiple values, or the value
   *                             cannot be parsed as an integer.
   */
  public int getIntValue()
         throws ArgumentException
  {
    if (values.isEmpty())
    {
      int    msgID   = MSGID_ARG_NO_INT_VALUE;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }

    Iterator<String> iterator = values.iterator();
    String valueString = iterator.next();

    int intValue;
    try
    {
      intValue = Integer.parseInt(valueString);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_ARG_CANNOT_DECODE_AS_INT;
      String message = getMessage(msgID, valueString, name);
      throw new ArgumentException(msgID, message, e);
    }

    if (iterator.hasNext())
    {
      int    msgID   = MSGID_ARG_INT_MULTIPLE_VALUES;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }
    else
    {
      return intValue;
    }
  }



  /**
   * Retrieves the set of values for this argument as a list of integers.
   *
   * @return  A list of the integer representations of the values for this
   *          argument.
   *
   * @throws  ArgumentException  If any of the values cannot be parsed as an
   *                             integer.
   */
  public LinkedList<Integer> getIntValues()
         throws ArgumentException
  {
    LinkedList<Integer> intList = new LinkedList<Integer>();

    Iterator<String> iterator = values.iterator();
    while (iterator.hasNext())
    {
      String valueString = iterator.next();

      try
      {
        intList.add(Integer.valueOf(valueString));
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_ARG_CANNOT_DECODE_AS_INT;
        String message = getMessage(msgID, valueString, name);
        throw new ArgumentException(msgID, message, e);
      }
    }

    return intList;
  }



  /**
   * Retrieves the value of this argument as a <CODE>Boolean</CODE>.
   *
   * @return  The value of this argument as a <CODE>Boolean</CODE>.
   *
   * @throws  ArgumentException  If this argument cannot be interpreted as a
   *                             Boolean value.
   */
  public boolean getBooleanValue()
         throws ArgumentException
  {
    if (values.isEmpty())
    {
      int    msgID   = MSGID_ARG_NO_BOOLEAN_VALUE;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }

    Iterator<String> iterator = values.iterator();
    String valueString = toLowerCase(iterator.next());

    boolean booleanValue;
    if (valueString.equals("true") || valueString.equals("yes") ||
        valueString.equals("on") || valueString.equals("1"))
    {
      booleanValue = true;
    }
    else if (valueString.equals("false") || valueString.equals("no") ||
             valueString.equals("off") || valueString.equals("0"))
    {
      booleanValue = false;
    }
    else
    {
      int    msgID   = MSGID_ARG_CANNOT_DECODE_AS_BOOLEAN;
      String message = getMessage(msgID, valueString, name);
      throw new ArgumentException(msgID, message);
    }

    if (iterator.hasNext())
    {
      int    msgID   = MSGID_ARG_BOOLEAN_MULTIPLE_VALUES;
      String message = getMessage(msgID, name);
      throw new ArgumentException(msgID, message);
    }
    else
    {
      return booleanValue;
    }
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
  public abstract boolean valueIsAcceptable(String valueString,
                                            StringBuilder invalidReason);



  /**
   * Adds a value to the set of values for this argument.  This should only be
   * called if the value is allowed by the <CODE>valueIsAcceptable</CODE>
   * method.
   *
   * @param  valueString  The string representation of the value to add to this
   *                      argument.
   */
  public void addValue(String valueString)
  {
    values.add(valueString);
  }



  /**
   * Clears the set of values assigned to this argument.
   */
  public void clearValues()
  {
    values.clear();
  }
}

