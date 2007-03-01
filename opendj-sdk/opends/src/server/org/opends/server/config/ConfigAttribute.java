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



import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanParameterInfo;

import org.opends.server.api.AttributeSyntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines a configuration attribute, which can hold zero or more
 * values associated with a configurable property within the Directory Server.
 * Subclasses should define and enforce actual data types.
 */
public abstract class ConfigAttribute
{



  // Indicates whether this configuration attribute has pending changes that
  // will be applied after appropriate administrative action has been performed.
  private boolean hasPendingValues;

  // Indicates whether this configuration attribute may have multiple values.
  private boolean isMultiValued;

  // Indicates whether this configuration attribute is required to have a value.
  private boolean isRequired;

  // Indicates whether changes to this attribute require administrative action
  // before they will take effect.
  private boolean requiresAdminAction;

  // The value or set of values that are currently in effect for this
  // configuration attribute.
  private LinkedHashSet<AttributeValue> activeValues;

  // The value or set of values that will be in effect once the appropriate
  // administrative action has been taken.
  private LinkedHashSet<AttributeValue> pendingValues;

  // The description for this configuration attribute.
  private String description;

  // The name for this configuration attribute.
  private String name;



  /**
   * Creates a new configuration attribute stub with the provided information
   * but no values.  The values will be set using the
   * <CODE>setInitialValue</CODE> method.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   */
  protected ConfigAttribute(String name, String description, boolean isRequired,
                            boolean isMultiValued, boolean requiresAdminAction)
  {


    this.name                = name;
    this.description         = description;
    this.isRequired          = isRequired;
    this.isMultiValued       = isMultiValued;
    this.requiresAdminAction = requiresAdminAction;

    hasPendingValues = false;
    activeValues     = new LinkedHashSet<AttributeValue>();
    pendingValues    = activeValues;
  }



  /**
   * Creates a new configuration attribute with the provided information.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  activeValues         The set of values for this attribute that are
   *                              currently active.
   */
  protected ConfigAttribute(String name, String description, boolean isRequired,
                            boolean isMultiValued, boolean requiresAdminAction,
                            LinkedHashSet<AttributeValue> activeValues)
  {


    this.name                = name;
    this.description         = description;
    this.isRequired          = isRequired;
    this.isMultiValued       = isMultiValued;
    this.requiresAdminAction = requiresAdminAction;
    this.hasPendingValues    = false;

    if (activeValues == null)
    {
      this.activeValues = new LinkedHashSet<AttributeValue>();
    }
    else
    {
      this.activeValues = activeValues;
    }

    this.pendingValues = this.activeValues;
  }



  /**
   * Creates a new configuration attribute with the provided information.
   *
   * @param  name                 The name for this configuration attribute.
   * @param  description          The description for this configuration
   *                              attribute.
   * @param  isRequired           Indicates whether this configuration attribute
   *                              is required to have at least one value.
   * @param  isMultiValued        Indicates whether this configuration attribute
   *                              may have multiple values.
   * @param  requiresAdminAction  Indicates whether changes to this
   *                              configuration attribute require administrative
   *                              action before they will take effect.
   * @param  activeValues         The set of values for this attribute that are
   *                              currently active.
   * @param  hasPendingValues     Indicates whether this attribute has any
   *                              pending values that will take effect after
   *                              appropriate administrative action.
   * @param  pendingValues        The set of values for this attribute that will
   *                              be in effect after the appropriate
   *                              administrative action is taken.  This may be
   *                              <CODE>null</CODE> if changes will take effect
   *                              immediately.
   */
  protected ConfigAttribute(String name, String description, boolean isRequired,
                            boolean isMultiValued, boolean requiresAdminAction,
                            LinkedHashSet<AttributeValue> activeValues,
                            boolean hasPendingValues,
                            LinkedHashSet<AttributeValue> pendingValues)
  {


    this.name                = name;
    this.description         = description;
    this.isRequired          = isRequired;
    this.isMultiValued       = isMultiValued;
    this.requiresAdminAction = requiresAdminAction;
    this.hasPendingValues    = hasPendingValues;

    if (activeValues == null)
    {
      this.activeValues = new LinkedHashSet<AttributeValue>();
    }
    else
    {
      this.activeValues = activeValues;
    }

    if (! hasPendingValues)
    {
      this.pendingValues = this.activeValues;
    }
    else
    {
      if (pendingValues == null)
      {
        this.pendingValues = new LinkedHashSet<AttributeValue>();
      }
      else
      {
        this.pendingValues = pendingValues;
      }
    }
  }



  /**
   * Retrieves the name for this configuration attribute.
   *
   * @return  The name for this configuration attribute.
   */
  public String getName()
  {

    return name;
  }



  /**
   * Retrieves the description for this configuration attribute.
   *
   * @return  The description for this configuration attribute, or
   *          <CODE>null</CODE> if there is no description.
   */
  public String getDescription()
  {

    return description;
  }



  /**
   * Retrieves the name of the data type for this configuration attribute.  This
   * is for informational purposes (e.g., inclusion in method signatures and
   * other kinds of descriptions) and does not necessarily need to map to an
   * actual Java type.
   *
   * @return  The name of the data type for this configuration attribute.
   */
  public abstract String getDataType();



  /**
   * Retrieves the attribute syntax for this configuration attribute.
   *
   * @return  The attribute syntax for this configuration attribute.
   */
  public abstract AttributeSyntax getSyntax();



  /**
   * Indicates whether this configuration attribute is required to have at least
   * one value.
   *
   * @return  <CODE>true</CODE> if this configuration attribute is required to
   *          have at least one value, or <CODE>false</CODE> if not.
   */
  public boolean isRequired()
  {

    return isRequired;
  }



  /**
   * Indicates whether this configuration attribute may have multiple values.
   *
   * @return  <CODE>true</CODE> if this configuration attribute may have
   *          multiple values, or <CODE>false</CODE> if not.
   */
  public boolean isMultiValued()
  {

    return isMultiValued;
  }



  /**
   * Indicates whether changes to this configuration attribute require
   * administrative action before they will take effect.
   *
   * @return  <CODE>true</CODE> if changes to this configuration attribute
   *          require administrative action before they will take effect, or
   *          <CODE>false</CODE> if changes will take effect immediately.
   */
  public boolean requiresAdminAction()
  {

    return requiresAdminAction;
  }



  /**
   * Retrieves the set of active values for this configuration attribute.  This
   * must not be modified by the caller.
   *
   * @return  The set of active values for this configuration attribute.
   */
  public LinkedHashSet<AttributeValue> getActiveValues()
  {

    return activeValues;
  }



  /**
   * Indicates whether this attribute has been altered and that there are a set
   * of pending values that will take effect after appropriate administrative
   * action.
   *
   * @return  <CODE>true</CODE> if this attribute has pending values, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasPendingValues()
  {

    return hasPendingValues;
  }



  /**
   * Retrieves the set of values that this configuration attribute will have on
   * restart or after any necessary administrative action is performed.  For
   * attributes whose changes take effect immediately, this will always be the
   * same as the set of active values.  This must not be modified by the caller.
   *
   * @return  The set of values that this configuration attribute will have
   *          after any appropriate administrative action is taken.
   */
  public LinkedHashSet<AttributeValue> getPendingValues()
  {

    if (requiresAdminAction)
    {
      return pendingValues;
    }
    else
    {
      return activeValues;
    }
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
  public abstract boolean valueIsAcceptable(AttributeValue value,
                                            StringBuilder rejectReason);



  /**
   * Specifies the set of values for this configuration attribute.  Each value
   * will be validated using the <CODE>valueIsAcceptable</CODE> method, and
   * only a single value will be allowed unless <CODE>isMultiValued</CODE>
   * returns <CODE>true</CODE>.  If the set of values is acceptable, then it
   * will be set either as the active set of values if changes are to take
   * effect immediately, or if not then it will be applied to the set of
   * pending values.
   *
   * @param  values  The set of values to apply to this attribute.
   *
   * @throws  ConfigException  If the provided set of values is not acceptable
   *                           for some reason.
   */
  protected void setValues(LinkedHashSet<AttributeValue> values)
         throws ConfigException
  {


    // If no values are provided, then check to see if this is a required
    // attribute.  If it is, then reject the change.
    if ((values == null) || values.isEmpty())
    {
      if (isRequired)
      {
        int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
        String message = getMessage(msgID, name);
        throw new ConfigException(msgID, message);
      }
      else
      {
        if (requiresAdminAction)
        {
          if (values == null)
          {
            pendingValues = new LinkedHashSet<AttributeValue>();
          }
          else
          {
            pendingValues = values;
          }

          hasPendingValues = true;
        }
        else
        {
          if (values == null)
          {
            activeValues = new LinkedHashSet<AttributeValue>();
          }
          else
          {
            activeValues = values;
          }

          pendingValues    = activeValues;
          hasPendingValues = false;
        }

        return;
      }
    }


    // We know that we have at least one value, so get it and see if it is OK.
    Iterator<AttributeValue> iterator     = values.iterator();
    AttributeValue           value        = iterator.next();
    StringBuilder            rejectReason = new StringBuilder();

    if (! valueIsAcceptable(value, rejectReason))
    {
      int    msgID   = MSGID_CONFIG_ATTR_REJECTED_VALUE;
      String message = getMessage(msgID, value.getStringValue(), name,
                                  rejectReason.toString());
      throw new ConfigException(msgID, message);
    }


    // If this is not a multivalued attribute but there were more values
    // provided, then reject it.
    if ((! isMultiValued) && iterator.hasNext())
    {
      int    msgID   = MSGID_CONFIG_ATTR_SET_VALUES_IS_SINGLE_VALUED;
      String message = getMessage(msgID, name);
      throw new ConfigException(msgID, message);
    }


    // Iterate through the remaining values to see if they are acceptable.
    while (iterator.hasNext())
    {
      value = iterator.next();
      if (! valueIsAcceptable(value, rejectReason))
      {
        int    msgID   = MSGID_CONFIG_ATTR_REJECTED_VALUE;
        String message = getMessage(msgID, value.getStringValue(), name,
                                    rejectReason.toString());
        throw new ConfigException(msgID, message);
      }
    }


    // If we've gotten here, then everything is OK.  Make this the new active or
    // pending value set depending on the configuration.
    if (requiresAdminAction)
    {
      pendingValues    = values;
      hasPendingValues = true;
    }
    else
    {
      activeValues     = values;
      pendingValues    = activeValues;
      hasPendingValues = false;

    }
  }



  /**
   * Specifies the set of active values for this configuration attribute.  No
   * validation will be performed, and no checks will be made to determine if
   * administrative action is required.
   *
   * @param  values  The set of active values for this configuration attribute.
   */
  protected void setActiveValues(LinkedHashSet<AttributeValue> values)
  {

    activeValues = values;
  }



  /**
   * Specifies the set of pending values for this configuration attribute.  No
   * validation will be performed, and no checks will be made to determine if
   * administrative action is required.
   *
   * @param  values  The set of pending values for this configuration attribute.
   */
  protected void setPendingValues(LinkedHashSet<AttributeValue> values)
  {

    pendingValues    = values;
    hasPendingValues = true;
  }



  /**
   * Attempts to add the provided set of values to this configuration attribute.
   * All of the appropriate validity checks will be performed, and the changes
   * will be applied to either the active or pending values, depending on the
   * configuration of this attribute.
   *
   * @param  values  The set of values to add to this configuration attribute.
   *
   * @throws  ConfigException  If a problem occurs while attempting to add the
   *                           provided set of values to this configuration
   *                           attribute.
   */
  protected void addValues(List<AttributeValue> values)
         throws ConfigException
  {


    // If there are no values provided, then do nothing.
    if (values == null)
    {
      return;
    }

    int numValues = values.size();
    if (numValues == 0)
    {
      return;
    }


    // Make sure that the value limit will not be exceeded for a single-valued
    // attribute.
    if (! isMultiValued)
    {
      if ((numValues > 1) || (hasPendingValues && (pendingValues.size() > 0)) ||
          ((! hasPendingValues) && (activeValues.size() > 0)))
      {
        int    msgID   = MSGID_CONFIG_ATTR_ADD_VALUES_IS_SINGLE_VALUED;
        String message = getMessage(msgID, name);
        throw new ConfigException(msgID, message);
      }
    }


    // Create a temporary set of values that we will use for this change.  It
    // may not actually be applied if an error occurs for some reason.
    LinkedHashSet<AttributeValue> tempValues;
    if (requiresAdminAction && hasPendingValues)
    {
      tempValues =
           new LinkedHashSet<AttributeValue>(pendingValues.size() + numValues);
      tempValues.addAll(pendingValues);
    }
    else
    {
      tempValues =
           new LinkedHashSet<AttributeValue>(activeValues.size() + numValues);
      tempValues.addAll(activeValues);
    }


    // Iterate through all of the provided values.  Make sure that each is
    // acceptable for use and that it is not already contained in the value set.
    StringBuilder rejectReason = new StringBuilder();
    for (AttributeValue value : values)
    {
      if (tempValues.contains(value))
      {
        int    msgID   = MSGID_CONFIG_ATTR_ADD_VALUES_ALREADY_EXISTS;
        String message = getMessage(msgID, name, value.getStringValue());
        throw new ConfigException(msgID, message);
      }

      if (! valueIsAcceptable(value, rejectReason))
      {
        int    msgID   = MSGID_CONFIG_ATTR_REJECTED_VALUE;
        String message = getMessage(msgID, value.getStringValue(), name,
                                    rejectReason.toString());
        throw new ConfigException(msgID, message);
      }
    }


    // If we have gotten here, then everything is OK, so go ahead and assign
    // the temporary value set to either the active or pending lists.
    if (requiresAdminAction)
    {
      pendingValues    = tempValues;
      hasPendingValues = true;
    }
    else
    {
      activeValues     = tempValues;
      pendingValues    = tempValues;
      hasPendingValues = false;
    }
  }



  /**
   * Attempts to remove the set of values from this configuration attribute.
   *
   * @param  values  The set of values to remove from this configuration
   *                 attribute.
   *
   * @throws  ConfigException  If any of the provided values are not in the
   *                           value set, or if this is a required attribute and
   *                           the resulting value list would be empty.
   */
  protected void removeValues(List<AttributeValue> values)
         throws ConfigException
  {


    // Create a temporary set of values that we will use for this change.  It
    // may not actually be applied if an error occurs for some reason.
    LinkedHashSet<AttributeValue> tempValues;
    if (requiresAdminAction && hasPendingValues)
    {
      tempValues =
           new LinkedHashSet<AttributeValue>(pendingValues.size());
      tempValues.addAll(pendingValues);
    }
    else
    {
      tempValues =
           new LinkedHashSet<AttributeValue>(activeValues.size());
      tempValues.addAll(activeValues);
    }


    // Iterate through all the provided values and make sure that they are
    // contained in the list.  If not, then throw an exception.  If so, then
    // remove it.
    for (AttributeValue value : values)
    {
      if (! tempValues.remove(value))
      {
        int msgID = MSGID_CONFIG_ATTR_NO_SUCH_VALUE;
        String message = getMessage(msgID, name, value.getStringValue());
        throw new ConfigException(msgID, message);
      }
    }


    // If this is a required attribute, then make sure that it will have at
    // least one value.
    if (isRequired && tempValues.isEmpty())
    {
      int msgID = MSGID_CONFIG_ATTR_IS_REQUIRED;
      String message = getMessage(msgID, name);
      throw new ConfigException(msgID, message);
    }


    // If we have gotten here, then everything is OK, so go ahead and assign
    // the temporary value set to either the active or pending lists.
    if (requiresAdminAction)
    {
      pendingValues    = tempValues;
      hasPendingValues = true;
    }
    else
    {
      activeValues     = tempValues;
      pendingValues    = tempValues;
      hasPendingValues = false;
    }
  }



  /**
   * Removes all values from this configuration attribute.
   *
   * @throws  ConfigException  If this is a required attribute that must have at
   *                           least one value.
   */
  protected void removeAllValues()
         throws ConfigException
  {

    if (isRequired)
    {
      int    msgID   = MSGID_CONFIG_ATTR_IS_REQUIRED;
      String message = getMessage(msgID, name);
      throw new ConfigException(msgID, message);
    }


    if (requiresAdminAction)
    {
      if (pendingValues == null)
      {
        pendingValues = new LinkedHashSet<AttributeValue>();
      }
      else
      {
        pendingValues.clear();
      }

      hasPendingValues = true;
    }
    else
    {
      activeValues.clear();
      pendingValues = activeValues;
      hasPendingValues = false;
    }
  }



  /**
   * Assigns the initial values to this configuration attribute.  This will wipe
   * out any previous active or pending values that may have been assigned, and
   * it will not perform any validation on those values.  This method must only
   * be used to set the initial values for this attribute from the configuration
   * repository and must not be called any other time.
   *
   * @param  values  The initial set of values to assign to this configuration
   *                 attribute.
   */
  public void setInitialValues(LinkedHashSet<AttributeValue> values)
  {

    if (values == null)
    {
      values = new LinkedHashSet<AttributeValue>();
    }

    activeValues     = values;
    pendingValues    = values;
    hasPendingValues = false;
  }



  /**
   * Applies the set of pending values, making them the active values for this
   * configuration attribute.  This will not take any action if there are no
   * pending values.
   */
  public void applyPendingValues()
  {

    if (hasPendingValues)
    {
      activeValues     = pendingValues;
      hasPendingValues = false;
    }
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
  public abstract LinkedHashSet<AttributeValue>
                       stringsToValues(List<String> valueStrings,
                                       boolean allowFailures)
         throws ConfigException;



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
  public abstract List<String> activeValuesToStrings();



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
  public abstract List<String> pendingValuesToStrings();



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
  public abstract ConfigAttribute getConfigAttribute(List<Attribute>
                                                          attributeList)
         throws ConfigException;



  /**
   * Retrieves a JMX attribute containing the active value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the active value set for this
   *          configuration attribute.
   */
  public abstract javax.management.Attribute toJMXAttribute();

  /**
   * Retrieves a JMX attribute containing the pending value set for this
   * configuration attribute.
   *
   * @return  A JMX attribute containing the pending value set for this
   *          configuration attribute.
   */
  public abstract javax.management.Attribute toJMXAttributePending();



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
  public abstract void toJMXAttribute(AttributeList attributeList);



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
  public abstract void toJMXAttributeInfo(List<MBeanAttributeInfo>
                                               attributeInfoList);



  /**
   * Retrieves a JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   * configuration attribute.
   *
   * @return  A JMX <CODE>MBeanParameterInfo</CODE> object that describes this
   *          configuration attribute.
   */
  public abstract MBeanParameterInfo toJMXParameterInfo();



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
  public abstract void setValue(javax.management.Attribute jmxAttribute)
         throws ConfigException;



  /**
   * Creates a duplicate of this configuration attribute.
   *
   * @return  A duplicate of this configuration attribute.
   */
  public abstract ConfigAttribute duplicate();
}

