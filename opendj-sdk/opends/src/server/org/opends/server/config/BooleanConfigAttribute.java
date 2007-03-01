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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.config;



import java.util.ArrayList;
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

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a Boolean configuration attribute, which can hold a single
 * Boolean value of <CODE>true</CODE> or <CODE>false</CODE>.  Boolean
 * configuration attributes will always be required and will never be
 * multivalued.
 */
public class BooleanConfigAttribute
       extends ConfigAttribute
{



  // The active value for this attribute.
  private boolean activeValue;

  // The pending value for this attribute.
  private boolean pendingValue;



  /**
   * Creates a new Boolean configuration attribute stub with the provided
   * information but no values.  The values will be set using the
   * <CODE>setInitialValue</CODE> method.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   */
  public BooleanConfigAttribute(String name, String description,
                                boolean requiresAdminAction)
  {
    super(name, description, true, false, requiresAdminAction);

  }



  /**
   * Creates a new Boolean configuration attribute with the provided
   * information.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  value                The value for this Boolean configuration
   *                              attribute.
   */
  public BooleanConfigAttribute(String name, String description,
                                boolean requiresAdminAction,
                                boolean value)
  {
    super(name, description, true, false, requiresAdminAction,
          getValueSet(value));

    activeValue  = value;
    pendingValue = value;
  }



  /**
   * Creates a new Boolean configuration attribute with the provided
   * information.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  activeValue          The active value for this Boolean
   *                              configuration attribute.
   * @param  pendingValue         The pending value for this Boolean
   *                              configuration attribute.
   */
  public BooleanConfigAttribute(String name, String description,
                                boolean requiresAdminAction,
                                boolean activeValue, boolean pendingValue)
  {
    super(name, description, true, false, requiresAdminAction,
          getValueSet(activeValue), true, getValueSet(pendingValue));


    this.activeValue  = activeValue;
    this.pendingValue = pendingValue;
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

    return "Boolean";
  }



  /**
   * Retrieves the attribute syntax for this configuration attribute.
   *
   * @return  The attribute syntax for this configuration attribute.
   */
  public AttributeSyntax getSyntax()
  {

    return DirectoryServer.getDefaultBooleanSyntax();
  }



  /**
   * Retrieves the active boolean value for this configuration attribute.
   *
   * @return  The active boolean value for this configuration attribute.
   */
  public boolean activeValue()
  {

    return activeValue;
  }



  /**
   * Retrieves the pending boolean value for this configuration attribute.  If
   * there is no pending value, then the active value will be returned.
   *
   * @return  The pending boolean value for this configuration attribute.
   */
  public boolean pendingValue()
  {

    if (hasPendingValues())
    {
      return pendingValue;
    }
    else
    {
      return activeValue;
    }
  }



  /**
   * Specifies the boolean value for this configuration attribute.
   *
   * @param  booleanValue  The boolean value for this configuration attribute.
   */
  public void setValue(boolean booleanValue)
  {

    if (requiresAdminAction())
    {
      pendingValue = booleanValue;
      setPendingValues(getValueSet(booleanValue));
    }
    else
    {
      activeValue = booleanValue;
      setActiveValues(getValueSet(booleanValue));
    }
  }



  /**
   * Creates the appropriate value set with the provided value.
   *
   * @param  booleanValue  The boolean value to use to create the value set.
   *
   * @return  The value set constructed from the provided value.
   */
  private static LinkedHashSet<AttributeValue> getValueSet(boolean booleanValue)
  {

    LinkedHashSet<AttributeValue> valueSet =
         new LinkedHashSet<AttributeValue>(1);
    if (booleanValue)
    {
      valueSet.add(new AttributeValue(new ASN1OctetString(CONFIG_VALUE_TRUE),
                                      new ASN1OctetString(CONFIG_VALUE_TRUE)));
    }
    else
    {
      valueSet.add(new AttributeValue(new ASN1OctetString(CONFIG_VALUE_FALSE),
                                      new ASN1OctetString(CONFIG_VALUE_FALSE)));
    }

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
    activeValue = pendingValue;
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

    String stringValue = value.getStringValue();
    if (stringValue.equalsIgnoreCase(CONFIG_VALUE_TRUE) ||
        stringValue.equalsIgnoreCase(CONFIG_VALUE_FALSE))
    {
      return true;
    }

    rejectReason.append(getMessage(MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE,
                                   getName(), stringValue));
    return false;
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
              stringsToValues(List<String> valueStrings,
                              boolean allowFailures)
         throws ConfigException
  {

    if ((valueStrings == null) || valueStrings.isEmpty())
    {
      int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
      String message = getMessage(msgID, getName());
      throw new ConfigException(msgID, message);
    }


    Iterator<String> iterator = valueStrings.iterator();
    String valueString = iterator.next().toLowerCase();
    if (iterator.hasNext())
    {
      int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
      String message = getMessage(msgID, getName());
      throw new ConfigException(msgID, message);
    }

    if (valueString.equals("true") || valueString.equals("yes") ||
        valueString.equals("on") || valueString.equals("1"))
    {
      return getValueSet(true);
    }
    else if (valueString.equals("false") || valueString.equals("no") ||
             valueString.equals("off") || valueString.equals("0"))
    {
      return getValueSet(false);
    }
    else
    {
      int    msgID   = MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE;
      String message = getMessage(msgID, valueString);
      throw new ConfigException(msgID, message);
    }
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
    valueStrings.add(String.valueOf(activeValue));

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
      valueStrings.add(String.valueOf(pendingValue));

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


    boolean activeValue     = false;
    boolean pendingValue    = false;
    boolean activeValueSet  = false;
    boolean pendingValueSet = false;

    for (Attribute a : attributeList)
    {
      if (a.hasOptions())
      {
        // This must be the pending value.
        if (a.hasOption(OPTION_PENDING_VALUES))
        {
          if (pendingValueSet)
          {
            // We cannot have multiple pending values.
            int    msgID   = MSGID_CONFIG_ATTR_MULTIPLE_PENDING_VALUE_SETS;
            String message = getMessage(msgID, a.getName());
            throw new ConfigException(msgID, message);
          }


          LinkedHashSet<AttributeValue> values = a.getValues();
          if (values.isEmpty())
          {
            // This is illegal -- it must have a value.
            int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
            String message = getMessage(msgID, a.getName());
            throw new ConfigException(msgID, message);
          }
          else
          {
            // Get the value and parse it as a Boolean.
            Iterator<AttributeValue> iterator = values.iterator();
            String valueString = iterator.next().getStringValue().toLowerCase();

            if (valueString.equals("true") || valueString.equals("yes") ||
                valueString.equals("on") || valueString.equals("1"))
            {
              pendingValue    = true;
              pendingValueSet = true;
            }
            else if (valueString.equals("false") || valueString.equals("no") ||
                     valueString.equals("off") || valueString.equals("0"))
            {
              pendingValue    = false;
              pendingValueSet = true;
            }
            else
            {
              // This is an illegal value.
              int msgID = MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE;
              String message = getMessage(msgID, valueString);
              throw new ConfigException(msgID, message);
            }

            if (iterator.hasNext())
            {
              // This is illegal -- it must be single-valued.
              int    msgID   = MSGID_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED;
              String message = getMessage(msgID, a.getName());
              throw new ConfigException(msgID, message);
            }
          }
        }
        else
        {
          // This is illegal -- only the pending option is allowed for
          // configuration attributes.
          int    msgID   = MSGID_CONFIG_ATTR_OPTIONS_NOT_ALLOWED;
          String message = getMessage(msgID, a.getName());
          throw new ConfigException(msgID, message);
        }
      }
      else
      {
        // This must be the active value.
        if (activeValueSet)
        {
          // We cannot have multiple active values.
          int    msgID   = MSGID_CONFIG_ATTR_MULTIPLE_ACTIVE_VALUE_SETS;
          String message = getMessage(msgID, a.getName());
          throw new ConfigException(msgID, message);
        }


        LinkedHashSet<AttributeValue> values = a.getValues();
        if (values.isEmpty())
        {
          // This is illegal -- it must have a value.
          int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
          String message = getMessage(msgID, a.getName());
          throw new ConfigException(msgID, message);
        }
        else
        {
          // Get the value and parse it as a Boolean.
          Iterator<AttributeValue> iterator = values.iterator();
          String valueString = iterator.next().getStringValue().toLowerCase();

          if (valueString.equals("true") || valueString.equals("yes") ||
              valueString.equals("on") || valueString.equals("1"))
          {
            activeValue    = true;
            activeValueSet = true;
          }
          else if (valueString.equals("false") || valueString.equals("no") ||
                   valueString.equals("off") || valueString.equals("0"))
          {
            activeValue    = false;
            activeValueSet = true;
          }
          else
          {
            // This is an illegal value.
            int msgID = MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE;
            String message = getMessage(msgID, valueString);
            throw new ConfigException(msgID, message);
          }

          if (iterator.hasNext())
          {
            // This is illegal -- it must be single-valued.
            int    msgID   = MSGID_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED;
            String message = getMessage(msgID, a.getName());
            throw new ConfigException(msgID, message);
          }
        }
      }
    }

    if (! activeValueSet)
    {
      // This is not OK.  The value set must contain an active value.
      int    msgID   = MSGID_CONFIG_ATTR_NO_ACTIVE_VALUE_SET;
      String message = getMessage(msgID, getName());
      throw new ConfigException(msgID, message);
    }

    if (pendingValueSet)
    {
      return new BooleanConfigAttribute(getName(), getDescription(),
                                        requiresAdminAction(), activeValue,
                                        pendingValue);
    }
    else
    {
    return new BooleanConfigAttribute(getName(), getDescription(),
                                      requiresAdminAction(), activeValue);
    }
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

    return new javax.management.Attribute(getName(), activeValue);
  }

  /**
   * Retrieves a JMX attribute containing the pending value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the pending value set for this
   *          configuration attribute.
   */
  public javax.management.Attribute toJMXAttributePending()
    {
        return new javax.management.Attribute(getName() + ";"
                + OPTION_PENDING_VALUES, pendingValue);
    }



  /**
   * Adds information about this configuration attribute to the provided JMX
   * attribute list.  If this configuration attribute requires administrative
   * action before changes take effect and it has a set of pending values, then
   * two attributes should be added to the list -- one for the active value
   * and one for the pending value.  The pending value should be named with
   * the pending option.
   *
   * @param  attributeList  The attribute list to which the JMX attribute(s)
   *                        should be added.
   */
  public void toJMXAttribute(AttributeList attributeList)
  {

    attributeList.add(new javax.management.Attribute(getName(), activeValue));

    if (requiresAdminAction() && (pendingValue != activeValue))
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;
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
                                                 Boolean.class.getName(),
                                                 getDescription(), true, true,
                                                 false));

    if (requiresAdminAction())
    {
      String name = getName() + ";" + OPTION_PENDING_VALUES;
      attributeInfoList.add(new MBeanAttributeInfo(name,
                                                   Boolean.class.getName(),
                                                   getDescription(), true,
                                                   false, false));
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

    return new MBeanParameterInfo(getName(), Boolean.TYPE.getName(),
                                  getDescription());
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
    if (value instanceof Boolean)
    {
      setValue(((Boolean) value).booleanValue());
    }
    else if (value instanceof String)
    {
      String stringValue = ((String) value).toLowerCase();
      if (stringValue.equals("true") || stringValue.equals("yes") ||
          stringValue.equals("on") || stringValue.equals("1"))
      {
        setValue(true);
      }
      else if (stringValue.equals("false") || stringValue.equals("no") ||
               stringValue.equals("off") || stringValue.equals("0"))
      {
        setValue(false);
      }
      else
      {
        int    msgID   = MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE;
        String message = getMessage(msgID, stringValue);
        throw new ConfigException(msgID, message);
      }
    }
    else
    {
      int    msgID   = MSGID_CONFIG_ATTR_INVALID_BOOLEAN_VALUE;
      String message = getMessage(msgID, value.getClass().getName() + ":" +
                                         String.valueOf(value));
      throw new ConfigException(msgID, message);
    }
  }



  /**
   * Creates a duplicate of this configuration attribute.
   *
   * @return  A duplicate of this configuration attribute.
   */
  public ConfigAttribute duplicate()
  {

    return new BooleanConfigAttribute(getName(), getDescription(),
                                      requiresAdminAction(), activeValue,
                                      pendingValue);
  }
}

