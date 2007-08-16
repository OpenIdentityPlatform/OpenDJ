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
package org.opends.server.config;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanParameterInfo;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.ErrorLogger;
import static org.opends.messages.ConfigMessages.*;
/**
 * This class defines a configuration attribute that stores both an integer
 * value and an associated unit.  The unit will contain both a string and a
 * floating-point value.  When a unit is selected, then the associated value
 * will be used as a multiplier for the integer value to achieve the actual
 * value for this parameter.  For example, the attribute could be used to
 * specify a size in bytes, but a value with a unit of "kb" could multiply that
 * value by 1024, or "mb" by 1048576, or "gb" by 1073741824.  In this case, a
 * value of "50 gb" would be the logical equivalent of "53687091200 b".  Upper
 * and lower bounds may be imposed, and in that case they will be imposed on
 * the actual value not on merely the integer portion.  This attribute may only
 * hold a single value and it will always be required.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class IntegerWithUnitConfigAttribute
       extends ConfigAttribute
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // Indicates whether this configuration attribute should impose a lower bound
  // for the calculated value.
  private boolean hasLowerBound;

  // Indicates whether this configuration attribute should impose an upper bound
  // for the calculated value.
  private boolean hasUpperBound;

  // The set of unit names and associated multipliers.
  private HashMap<String,Double> units;

  // The active calculated value for this attribute.
  private long activeCalculatedValue;

  // The active value for this attribute.
  private long activeIntValue;

  // The lower bound for the calculated value.
  private long lowerBound;

  // The pending calculated value for this attribute.
  private long pendingCalculatedValue;

  // The the pending value for this attribute.
  private long pendingIntValue;

  // The upper bound for the calculated value.
  private long upperBound;

  // The active unit for this attribute.
  private String activeUnit;

  // The pending unit for this attribute.
  private String pendingUnit;



  /**
   * Creates a new integer with unit configuration attribute stub with the
   * provided information but no values.  The values will be set using the
   * <CODE>setInitialValue</CODE> method.  Mo validation will be performed on
   * the set of allowed units.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  units                The set of units and their associated
   *                              multipliers for this configuration attribute.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for the calculated value.
   * @param  lowerBound           The lower bound for the calculated value.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for the calculated value.
   * @param  upperBound           The upper bound for the calculated value.
   */
  public IntegerWithUnitConfigAttribute(String name, Message description,
                                        boolean requiresAdminAction,
                                        HashMap<String,Double> units,
                                        boolean hasLowerBound, long lowerBound,
                                        boolean hasUpperBound, long upperBound)
  {
    super(name, description, true, false, requiresAdminAction);


    this.units         = units;
    this.hasLowerBound = hasLowerBound;
    this.lowerBound    = lowerBound;
    this.hasUpperBound = hasUpperBound;
    this.upperBound    = upperBound;
  }



  /**
   * Creates a new integer with unit configuration attribute with the provided
   * information.  No validation will be performed on the provided value or
   * unit, or on the set of allowed units.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  units                The set of units and their associated
   *                              multipliers for this configuration attribute.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for the calculated value.
   * @param  lowerBound           The lower bound for the calculated value.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for the calculated value.
   * @param  upperBound           The upper bound for the calculated value.
   * @param  intValue             The selected value for this configuration
   *                              attribute.
   * @param  selectedUnit         The selected unit for this configuration
   *                              attribute.
   */
  public IntegerWithUnitConfigAttribute(String name, Message description,
                                        boolean requiresAdminAction,
                                        HashMap<String,Double> units,
                                        boolean hasLowerBound, long lowerBound,
                                        boolean hasUpperBound, long upperBound,
                                        long intValue, String selectedUnit)
  {
    super(name, description, true, false, requiresAdminAction,
          getValueSet(intValue, selectedUnit));



    this.units          = units;
    this.hasLowerBound  = hasLowerBound;
    this.lowerBound     = lowerBound;
    this.hasUpperBound  = hasUpperBound;
    this.upperBound     = upperBound;
    this.activeIntValue = intValue;
    this.activeUnit     = selectedUnit;

    pendingIntValue = activeIntValue;
    pendingUnit     = activeUnit;

    if (units.containsKey(selectedUnit))
    {
      activeCalculatedValue = (long) (activeIntValue * units.get(selectedUnit));
    }

    pendingCalculatedValue = activeCalculatedValue;
  }



  /**
   * Creates a new integer with unit configuration attribute with the provided
   * information.  No validation will be performed on the provided value or
   * unit, or on the set of allowed units.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  units                The set of units and their associated
   *                              multipliers for this configuration attribute.
   * @param  hasLowerBound        Indicates whether a lower bound will be
   *                              enforced for the calculated value.
   * @param  lowerBound           The lower bound for the calculated value.
   * @param  hasUpperBound        Indicates whether an upper bound will be
   *                              enforced for the calculated value.
   * @param  upperBound           The upper bound for the calculated value.
   * @param  activeIntValue       The active selected value for this
   *                              configuration attribute.
   * @param  activeSelectedUnit   The active selected unit for this
   *                              configuration attribute.
   * @param  pendingIntValue      The pending selected value for this
   *                              configuration attribute.
   * @param  pendingSelectedUnit  The pending selected unit for this
   *                              configuration attribute.
   */
  public IntegerWithUnitConfigAttribute(String name, Message description,
                                        boolean requiresAdminAction,
                                        HashMap<String,Double> units,
                                        boolean hasLowerBound, long lowerBound,
                                        boolean hasUpperBound, long upperBound,
                                        long activeIntValue,
                                        String activeSelectedUnit,
                                        long pendingIntValue,
                                        String pendingSelectedUnit)
  {
    super(name, description, true, false, requiresAdminAction,
          getValueSet(activeIntValue, activeSelectedUnit),
          (pendingSelectedUnit != null),
          getValueSet(pendingIntValue,pendingSelectedUnit));



    this.units          = units;
    this.hasLowerBound  = hasLowerBound;
    this.lowerBound     = lowerBound;
    this.hasUpperBound  = hasUpperBound;
    this.upperBound     = upperBound;
    this.activeIntValue = activeIntValue;
    this.activeUnit     = activeSelectedUnit;

    if (pendingSelectedUnit == null)
    {
      this.pendingIntValue = activeIntValue;
      this.pendingUnit     = activeUnit;
    }
    else
    {
      this.pendingIntValue = pendingIntValue;
      this.pendingUnit     = pendingSelectedUnit;
    }

    if (units.containsKey(activeUnit))
    {
      activeCalculatedValue = (long) (activeIntValue*units.get(activeUnit));
    }


    if (units.containsKey(pendingUnit))
    {
      pendingCalculatedValue = (long) (pendingIntValue*units.get(pendingUnit));
    }
  }



  /**
   * Retrieves the name of the data type for this configuration attribute.  This
   * is for informational purposes (e.g., inclusion in method signatures and
   * other kinds of descriptions) and does not necessarily need to map to an
   * actual Java type.
   *
   * @return  The name of the data type for this configuration attribute.
   */
  public String getDataType()
  {
    return "IntegerWithUnit";
  }



  /**
   * Retrieves the attribute syntax for this configuration attribute.
   *
   * @return  The attribute syntax for this configuration attribute.
   */
  public AttributeSyntax getSyntax()
  {
    return DirectoryServer.getDefaultStringSyntax();
  }



  /**
   * Retrieves the integer component of the active value for this configuration
   * attribute.
   *
   * @return  The integer component of the active value for this configuration
   *          attribute.
   */
  public long activeIntValue()
  {
    return activeIntValue;
  }



  /**
   * Retrieves the name of the active unit for this configuration attribute.
   *
   * @return  The name of the active unit for this configuration attribute.
   */
  public String activeUnit()
  {
    return activeUnit;
  }



  /**
   * Retrieves the calculated active value for this configuration attribute.
   * This will be the product of the active int value and the multiplier for the
   * associated active unit.
   *
   * @return  The calculated active value for this configuration attribute.
   */
  public long activeCalculatedValue()
  {
    return activeCalculatedValue;
  }



  /**
   * Retrieves the integer component of the pending value for this configuration
   * attribute.  If there is no pending value, then the integer component of the
   * active value will be returned.
   *
   * @return  The integer component of the pending value for this configuration
   *          attribute.
   */
  public long pendingIntValue()
  {
    if (hasPendingValues())
    {
      return pendingIntValue;
    }
    else
    {
      return activeIntValue;
    }
  }



  /**
   * Retrieves the name of the pending unit for this configuration attribute.
   * If there is no pending value, then the unit for the active value will be
   * returned.
   *
   * @return  The name of the pending unit for this configuration attribute.
   */
  public String pendingUnit()
  {
    if (hasPendingValues())
    {
      return pendingUnit;
    }
    else
    {
      return activeUnit;
    }
  }



  /**
   * Retrieves the calculated pending value for this configuration attribute.
   * This will be the product of the pending int value and the multiplier for
   * the associated pending unit.  If there is no pending value, then the
   * calculated active value will be returned.
   *
   * @return  The calculated pending value for this configuration attribute.
   */
  public long pendingCalculatedValue()
  {
    if (hasPendingValues())
    {
      return pendingCalculatedValue;
    }
    else
    {
      return activeCalculatedValue;
    }
  }



  /**
   * Retrieves the mapping between the allowed names for the units and their
   * multipliers for this configuration attribute.
   *
   * @return  The mapping between the allowed names for the units and their
   *          multipliers for this configuration attribute.
   */
  public HashMap<String,Double> getUnits()
  {
    return units;
  }



  /**
   * Indicates whether a lower bound will be enforced for the calculated value
   * of this configuration attribute.
   *
   * @return  <CODE>true</CODE> if a lower bound will be enforced for the
   *          calculated value of this configuration attribute, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasLowerBound()
  {
    return hasLowerBound;
  }



  /**
   * Retrieves the lower bound for the calculated value of this configuration
   * attribute.
   *
   * @return  The lower bound for the calculated value of this configuration
   *          attribute.
   */
  public long getLowerBound()
  {
    return lowerBound;
  }



  /**
   * Indicates whether an upper bound will be enforced for the calculated value
   * of this configuration attribute.
   *
   * @return  <CODE>true</CODE> if an upper bound will be enforced for the
   *          calculated value of this configuration attribute, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasUpperBound()
  {
    return hasUpperBound;
  }



  /**
   * Retrieves the upper bound for the calculated value of this configuration
   * attribute.
   *
   * @return  The upper bound for the calculated value of this configuration
   *          attribute.
   */
  public long getUpperBound()
  {
    return upperBound;
  }



  /**
   * Sets the value for this configuration attribute.
   *
   * @param  intValue  The integer component for the value of this configuration
   *                   attribute.
   * @param  unit      The unit for the value of this configuration attribute.
   *
   * @throws  ConfigException  If the provided unit is not recognized, or if the
   *                           resulting calculated value is outside the
   *                           acceptable bounds.
   */
  public void setValue(long intValue, String unit)
         throws ConfigException
  {
    if ((unit == null) || (! units.containsKey(unit)))
    {
      Message message = ERR_CONFIG_ATTR_INVALID_UNIT.get(unit, getName());
      throw new ConfigException(message);
    }


    long calculatedValue = (long) (intValue * units.get(unit));
    if (hasLowerBound && (calculatedValue < lowerBound))
    {
      Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
          getName(), calculatedValue, lowerBound);
      throw new ConfigException(message);
    }

    if (hasUpperBound && (calculatedValue > upperBound))
    {
      Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
          getName(), calculatedValue, upperBound);
      throw new ConfigException(message);
    }


    if (requiresAdminAction())
    {
      pendingCalculatedValue = calculatedValue;
      pendingIntValue        = intValue;
      pendingUnit            = unit;
      setPendingValues(getValueSet(intValue, unit));
    }
    else
    {
      activeCalculatedValue = calculatedValue;
      activeIntValue        = intValue;
      activeUnit            = unit;
      setActiveValues(getValueSet(intValue, unit));
    }
  }



  /**
   * Sets the value for this configuration attribute.
   *
   * @param  value  The string representation of the value to use for this
   *                configuration attribute.
   *
   * @throws  ConfigException  If the provided value is invalid for some reason.
   */
  public void setValue(String value)
         throws ConfigException
  {
    int spacePos = value.indexOf(' ');
    if (spacePos <= 0)
    {
      Message message = ERR_CONFIG_ATTR_NO_UNIT_DELIMITER.get(
          String.valueOf(value), getName());
      throw new ConfigException(message);
    }


    long longValue;
    try
    {
      longValue = Long.parseLong(value.substring(0, spacePos));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_ATTR_COULD_NOT_PARSE_INT_COMPONENT.get(
          String.valueOf(value), getName(), String.valueOf(e));
      throw new ConfigException(message, e);
    }

    setValue(longValue, value.substring(spacePos+1));
  }



  /**
   * Creates the appropriate value set with the provided value.
   *
   * @param  intValue  The integer component for the value to construct.
   * @param  unit      The unit name for the value to construct.
   *
   * @return  The constructed value set.
   */
  private static LinkedHashSet<AttributeValue> getValueSet(long intValue,
                                                           String unit)
  {
    if (unit == null)
    {
      return null;
    }

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);

    String valueString = intValue + " " + unit;
    valueSet.add(new AttributeValue(new ASN1OctetString(valueString),
                                    new ASN1OctetString(valueString)));

    return valueSet;
  }



  /**
   * Applies the set of pending values, making them the active values for this
   * configuration attribute.  This will not take any action if there are no
   * pending values.
   */
  public void applyPendingValues()
  {
    if (! hasPendingValues())
    {
      return;
    }

    super.applyPendingValues();
    activeCalculatedValue = pendingCalculatedValue;
    activeIntValue        = pendingIntValue;
    activeUnit            = pendingUnit;
  }



  /**
   * Indicates whether the provided value is acceptable for use in this
   * attribute.  If it is not acceptable, then the reason should be written into
   * the provided buffer.
   *
   * @param  value         The value for which to make the determination.
   * @param  rejectReason  A buffer into which a human-readable reason for the
   *                       reject may be written.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use in
   *          this attribute, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(AttributeValue value,
                                   StringBuilder rejectReason)
  {
    // Get a string representation of the value and convert it to lowercase.
    String lowerValue = value.getStringValue().toLowerCase();

    return valueIsAcceptable(lowerValue, rejectReason);
  }



  /**
   * Indicates whether the provided value is acceptable for use in this
   * attribute.  If it is not acceptable, then the reason should be written into
   * the provided buffer.
   *
   * @param  lowerValue    The lowercase version of the value for which to make
   *                       the determination.
   * @param  rejectReason  A buffer into which a human-readable reason for the
   *                       reject may be written.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use in
   *          this attribute, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(String lowerValue,
                                   StringBuilder rejectReason)
  {
    // Find the first space in the value, since it should separate the integer
    // from the unit.
    int spacePos = lowerValue.indexOf(' ');
    if (spacePos < 0)
    {
      rejectReason.append(ERR_CONFIG_ATTR_NO_UNIT_DELIMITER.get(
              lowerValue, getName()));
      return false;
    }


    // The part up to the space should be the integer component.
    long longValue;
    try
    {
      longValue = Long.parseLong(lowerValue.substring(0, spacePos));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      rejectReason.append(ERR_CONFIG_ATTR_INT_COULD_NOT_PARSE.get(
              lowerValue, getName(), String.valueOf(e)));
      return false;
    }


    // The rest of the value should be the unit.  See if it is in the set of
    // available units.
    String unit = lowerValue.substring(spacePos+1);
    double multiplier;
    if (! units.containsKey(unit))
    {
      rejectReason.append(ERR_CONFIG_ATTR_INVALID_UNIT.get(unit,
                                     getName()));
      return false;
    }
    else
    {
      multiplier = units.get(unit);
    }


    // Multiply the int value by the unit multiplier and see if that is within
    // the specified bounds.
    long calculatedValue = (long) (longValue * multiplier);
    if (hasLowerBound && (calculatedValue < lowerBound))
    {
      rejectReason.append(ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
              getName(), calculatedValue, lowerBound));
      return false;
    }

    if (hasUpperBound && (calculatedValue > upperBound))
    {
      rejectReason.append(ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
              getName(), calculatedValue, upperBound));
      return false;
    }


    // If we've gotten here, then the value is OK.
    return true;
  }



  /**
   * Converts the provided set of strings to a corresponding set of attribute
   * values.
   *
   * @param  valueStrings   The set of strings to be converted into attribute
   *                        values.
   * @param  allowFailures  Indicates whether the decoding process should allow
   *                        any failures in which one or more values could be
   *                        decoded but at least one could not.  If this is
   *                        <CODE>true</CODE> and such a condition is acceptable
   *                        for the underlying attribute type, then the returned
   *                        set of values should simply not include those
   *                        undecodable values.
   *
   * @return  The set of attribute values converted from the provided strings.
   *
   * @throws  ConfigException  If an unrecoverable problem occurs while
   *                           performing the conversion.
   */
  public LinkedHashSet<AttributeValue>
              stringsToValues(List<String> valueStrings, boolean allowFailures)
         throws ConfigException
  {
    if ((valueStrings == null) || valueStrings.isEmpty())
    {
      if (isRequired())
      {
        Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(getName());
        throw new ConfigException(message);
      }
      else
      {
        return new LinkedHashSet<AttributeValue>();
      }
    }


    int numValues = valueStrings.size();
    if ((! isMultiValued()) && (numValues > 1))
    {
      Message message =
          ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(getName());
      throw new ConfigException(message);
    }


    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(numValues);
    for (String valueString : valueStrings)
    {
      if ((valueString == null) || (valueString.length() == 0))
      {
        Message message = ERR_CONFIG_ATTR_EMPTY_STRING_VALUE.get(getName());
        if (allowFailures)
        {
          ErrorLogger.logError(message);
          continue;
        }
        else
        {
          throw new ConfigException(message);
        }
      }


      StringBuilder rejectReason = new StringBuilder();
      if (! valueIsAcceptable(valueString.toLowerCase(), rejectReason))
      {
        Message message = ERR_CONFIG_ATTR_INVALID_VALUE_WITH_UNIT.get(
                valueString, getName(),
                rejectReason.toString());

        if (allowFailures)
        {
          ErrorLogger.logError(message);
          continue;
        }
        else
        {
          throw new ConfigException(message);
        }
      }


      valueSet.add(new AttributeValue(new ASN1OctetString(valueString),
                                      new ASN1OctetString(valueString)));
    }


    // If this method was configured to continue on error, then it is possible
    // that we ended up with an empty list.  Check to see if this is a required
    // attribute and if so deal with it accordingly.
    if ((isRequired()) && valueSet.isEmpty())
    {
      Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(getName());
      throw new ConfigException(message);
    }


    return valueSet;
  }



  /**
   * Converts the set of active values for this configuration attribute into a
   * set of strings that may be stored in the configuration or represented over
   * protocol.  The string representation used by this method should be
   * compatible with the decoding used by the <CODE>stringsToValues</CODE>
   * method.
   *
   * @return  The string representations of the set of active values for this
   *          configuration attribute.
   */
  public List<String> activeValuesToStrings()
  {
    ArrayList<String> valueStrings = new ArrayList<String>(1);
    valueStrings.add(activeIntValue + " " + activeUnit);

    return valueStrings;
  }



  /**
   * Converts the set of pending values for this configuration attribute into a
   * set of strings that may be stored in the configuration or represented over
   * protocol.  The string representation used by this method should be
   * compatible with the decoding used by the <CODE>stringsToValues</CODE>
   * method.
   *
   * @return  The string representations of the set of pending values for this
   *          configuration attribute, or <CODE>null</CODE> if there are no
   *          pending values.
   */
  public List<String> pendingValuesToStrings()
  {
    if (hasPendingValues())
    {
      ArrayList<String> valueStrings = new ArrayList<String>(1);
      valueStrings.add(pendingIntValue + " " + pendingUnit);

      return valueStrings;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves a new configuration attribute of this type that will contain the
   * values from the provided attribute.
   *
   * @param  attributeList  The list of attributes to use to create the config
   *                        attribute.  The list must contain either one or two
   *                        elements, with both attributes having the same base
   *                        name and the only option allowed is ";pending" and
   *                        only if this attribute is one that requires admin
   *                        action before a change may take effect.
   *
   * @return  The generated configuration attribute.
   *
   * @throws  ConfigException  If the provided attribute cannot be treated as a
   *                           configuration attribute of this type (e.g., if
   *                           one or more of the values of the provided
   *                           attribute are not suitable for an attribute of
   *                           this type, or if this configuration attribute is
   *                           single-valued and the provided attribute has
   *                           multiple values).
   */
  public ConfigAttribute getConfigAttribute(List<Attribute> attributeList)
         throws ConfigException
  {
    long   activeIntValue  = 0;
    long   pendingIntValue = 0;
    String activeUnit      = null;
    String pendingUnit     = null;

    for (Attribute a : attributeList)
    {
      if (a.hasOptions())
      {
        // This must be the pending value.
        if (a.hasOption(OPTION_PENDING_VALUES))
        {
          if (pendingUnit != null)
          {
            // We cannot have multiple pending value sets.
            Message message =
                ERR_CONFIG_ATTR_MULTIPLE_PENDING_VALUE_SETS.get(a.getName());
            throw new ConfigException(message);
          }


          LinkedHashSet<AttributeValue> values = a.getValues();
          if (values.isEmpty())
          {
            // This is illegal -- it must have a value.
            Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(a.getName());
            throw new ConfigException(message);
          }
          else
          {
            Iterator<AttributeValue> iterator = values.iterator();

            String valueString = iterator.next().getStringValue();

            if (iterator.hasNext())
            {
              // This is illegal -- the attribute is single-valued.
              Message message =
                  ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(a.getName());
              throw new ConfigException(message);
            }

            try
            {
              int spacePos = valueString.indexOf(' ');
              pendingIntValue =
                   Long.parseLong(valueString.substring(0, spacePos));
              pendingUnit = valueString.substring(spacePos+1).trim();
            }
            catch (Exception e)
            {
              Message message = ERR_CONFIG_ATTR_COULD_NOT_PARSE_INT_COMPONENT.
                  get(valueString, a.getName(), String.valueOf(e));
              throw new ConfigException(message);
            }


            // Get the unit and use it to determine the corresponding
            // multiplier.
            if (! units.containsKey(pendingUnit))
            {
              Message message =
                  ERR_CONFIG_ATTR_INVALID_UNIT.get(pendingUnit, a.getName());
              throw new ConfigException(message);
            }

            double multiplier = units.get(activeUnit);
            pendingCalculatedValue = (long) (multiplier * pendingIntValue);


            // Check the bounds set for this attribute.
            if (hasLowerBound && (pendingCalculatedValue < lowerBound))
            {
              Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
                  a.getName(), pendingCalculatedValue, lowerBound);
              throw new ConfigException(message);
            }

            if (hasUpperBound && (pendingCalculatedValue > upperBound))
            {
              Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
                  a.getName(), pendingCalculatedValue, upperBound);
              throw new ConfigException(message);
            }
          }
        }
        else
        {
          // This is illegal -- only the pending option is allowed for
          // configuration attributes.
          Message message =
              ERR_CONFIG_ATTR_OPTIONS_NOT_ALLOWED.get(a.getName());
          throw new ConfigException(message);
        }
      }
      else
      {
        // This must be the active value.
        if (activeUnit != null)
        {
          // We cannot have multiple active value sets.
          Message message =
              ERR_CONFIG_ATTR_MULTIPLE_ACTIVE_VALUE_SETS.get(a.getName());
          throw new ConfigException(message);
        }


        LinkedHashSet<AttributeValue> values = a.getValues();
        if (values.isEmpty())
        {
          // This is illegal -- it must have a value.
          Message message = ERR_CONFIG_ATTR_IS_REQUIRED.get(a.getName());
          throw new ConfigException(message);
        }
        else
        {
          Iterator<AttributeValue> iterator = values.iterator();

          String valueString = iterator.next().getStringValue();

          if (iterator.hasNext())
          {
            // This is illegal -- the attribute is single-valued.
            Message message =
                ERR_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED.get(a.getName());
            throw new ConfigException(message);
          }

          try
          {
            int spacePos = valueString.indexOf(' ');
            activeIntValue =
                 Long.parseLong(valueString.substring(0, spacePos));
            activeUnit = valueString.substring(spacePos+1).trim();
          }
          catch (Exception e)
          {
            Message message = ERR_CONFIG_ATTR_COULD_NOT_PARSE_INT_COMPONENT.get(
                valueString, a.getName(), String.valueOf(e));
            throw new ConfigException(message);
          }


          // Get the unit and use it to determine the corresponding multiplier.
          if (! units.containsKey(activeUnit))
          {
            Message message =
                ERR_CONFIG_ATTR_INVALID_UNIT.get(activeUnit, a.getName());
            throw new ConfigException(message);
          }

          double multiplier = units.get(activeUnit);
          activeCalculatedValue = (long) (multiplier * activeIntValue);


          // Check the bounds set for this attribute.
          if (hasLowerBound && (activeCalculatedValue < lowerBound))
          {
            Message message = ERR_CONFIG_ATTR_INT_BELOW_LOWER_BOUND.get(
                a.getName(), activeCalculatedValue, lowerBound);
            throw new ConfigException(message);
          }

          if (hasUpperBound && (activeCalculatedValue > upperBound))
          {
            Message message = ERR_CONFIG_ATTR_INT_ABOVE_UPPER_BOUND.get(
                a.getName(), activeCalculatedValue, upperBound);
            throw new ConfigException(message);
          }
        }
      }
    }

    if (activeUnit == null)
    {
      // This is not OK.  The value set must contain an active value.
      Message message = ERR_CONFIG_ATTR_NO_ACTIVE_VALUE_SET.get(getName());
      throw new ConfigException(message);
    }

    if (pendingUnit == null)
    {
      // This is OK.  We'll just use the active value set.
      pendingIntValue = activeIntValue;
      pendingUnit     = activeUnit;
    }


    return new IntegerWithUnitConfigAttribute(getName(), getDescription(),
                                              requiresAdminAction(), units,
                                              hasLowerBound, lowerBound,
                                              hasUpperBound, upperBound,
                                              activeIntValue, activeUnit,
                                              pendingIntValue, pendingUnit);
  }



  /**
   * Retrieves a JMX attribute containing the active value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the active value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  public javax.management.Attribute toJMXAttribute()
  {
    return new javax.management.Attribute(getName(),
                                          activeIntValue + " " + activeUnit);
  }

  /**
   * Retrieves a JMX attribute containing the pending value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the pending value set for this
   *          configuration attribute, or <CODE>null</CODE> if it does not have
   *          any active values.
   */
  public javax.management.Attribute toJMXAttributePending()
    {

        return new javax.management.Attribute(getName() + ";"
                + OPTION_PENDING_VALUES, pendingIntValue + " " + pendingUnit);
    }


  /**
     * Adds information about this configuration attribute to the provided
     * JMX attribute list. If this configuration attribute requires
     * administrative action before changes take effect and it has a set of
     * pending values, then two attributes should be added to the list --
     * one for the active value and one for the pending value. The pending
     * value should be named with the pending option.
     *
     * @param attributeList
     *            The attribute list to which the JMX attribute(s) should
     *            be added.
     */
  public void toJMXAttribute(AttributeList attributeList)
  {
    String activeValue = activeIntValue + " " + activeUnit;
    attributeList.add(new javax.management.Attribute(getName(), activeValue));

    if (requiresAdminAction() &&
        (pendingCalculatedValue != activeCalculatedValue))
    {
      String name         = getName() + ";" + OPTION_PENDING_VALUES;
      String pendingValue = pendingIntValue + " " + pendingUnit;
      attributeList.add(new javax.management.Attribute(name, pendingValue));
    }
  }



  /**
   * Adds information about this configuration attribute to the provided list in
   * the form of a JMX <CODE>MBeanAttributeInfo</CODE> object.  If this
   * configuration attribute requires administrative action before changes take
   * effect and it has a set of pending values, then two attribute info objects
   * should be added to the list -- one for the active value (which should be
   * read-write) and one for the pending value (which should be read-only).  The
   * pending value should be named with the pending option.
   *
   * @param  attributeInfoList  The list to which the attribute information
   *                            should be added.
   */
  public void toJMXAttributeInfo(List<MBeanAttributeInfo> attributeInfoList)
  {
    attributeInfoList.add(new MBeanAttributeInfo(getName(),
                                                 String.class.getName(),
                                                 String.valueOf(
                                                         getDescription()),
                                                 true, true, false));

    if (requiresAdminAction())
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;
      attributeInfoList.add(new MBeanAttributeInfo(name,
                                                   String.class.getName(),
                                                   String.valueOf(
                                                           getDescription()),
                                                   true, false, false));
    }
  }



  /**
   * Retrieves a JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   * configuration attribute.
   *
   * @return  A JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   *          configuration attribute.
   */
  public MBeanParameterInfo toJMXParameterInfo()
  {
    return new MBeanParameterInfo(getName(), String.class.getName(),
                                  String.valueOf(getDescription()));
  }



  /**
   * Attempts to set the value of this configuration attribute based on the
   * information in the provided JMX attribute.
   *
   * @param  jmxAttribute  The JMX attribute to use to attempt to set the value
   *                       of this configuration attribute.
   *
   * @throws  ConfigException  If the provided JMX attribute does not have an
   *                           acceptable value for this configuration
   *                           attribute.
   */
  public void setValue(javax.management.Attribute jmxAttribute)
         throws ConfigException
  {
    Object value = jmxAttribute.getValue();
    if (value instanceof String)
    {
      setValue((String) value);
    }
    else
    {
      Message message = ERR_CONFIG_ATTR_INT_WITH_UNIT_INVALID_TYPE.get(
          String.valueOf(value), getName(), value.getClass().getName());
      throw new ConfigException(message);
    }
  }



  /**
   * Creates a duplicate of this configuration attribute.
   *
   * @return  A duplicate of this configuration attribute.
   */
  public ConfigAttribute duplicate()
  {
    return new IntegerWithUnitConfigAttribute(getName(), getDescription(),
                                              requiresAdminAction(), units,
                                              hasLowerBound, lowerBound,
                                              hasUpperBound, upperBound,
                                              activeIntValue, activeUnit,
                                              pendingIntValue, pendingUnit);
  }
}

