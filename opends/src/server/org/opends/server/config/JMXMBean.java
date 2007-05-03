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



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.InvokableComponent;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.jmx.Credential;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.InvokableMethod;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.config.ConfigConstants.OPTION_PENDING_VALUES;
import org.opends.server.protocols.jmx.JmxClientConnection;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPAttribute ;
import org.opends.server.protocols.internal.InternalSearchOperation ;
import org.opends.server.core.ModifyOperation ;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ModificationType;



/**
 * This class defines a JMX MBean that can be registered with the Directory
 * Server to provide monitoring and statistical information, provide read and/or
 * read-write access to the configuration, and provide notifications and alerts
 * if a significant event or severe/fatal error occurs.
 */
public class JMXMBean
       implements DynamicMBean, DirectoryServerMBean
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.config.JMXMBean";



  // The set of alert generators for this MBean.
  private CopyOnWriteArrayList<AlertGenerator> alertGenerators;

  // The set of configurable components for this MBean.
  private CopyOnWriteArrayList<ConfigurableComponent> configurableComponents;

  // The set of invokable components for this MBean.
  private CopyOnWriteArrayList<InvokableComponent> invokableComponents;

  // The set of monitor providers for this MBean.
  private CopyOnWriteArrayList<MonitorProvider> monitorProviders;

  // The DN of the configuration entry with which this MBean is associated.
  private DN configEntryDN;

  // The object name for this MBean.
  private ObjectName objectName;


  /**
   * Creates a JMX object name string based on a DN.
   *
   * @param  configEntryDN  The DN of the configuration entry with which
   *                        this ObjectName is associated.
   *
   * @return The string representation of the JMX Object Name
   * associated with the input DN.
   */
  public static String getJmxName (DN configEntryDN)
  {
      String typeStr = null;
      String nameStr = null ;
      try
      {
          String dnString = configEntryDN.toString();
          if ( ! ((dnString == null) || (dnString.length() == 0)))
          {
              StringBuilder buffer = new StringBuilder(dnString.length());
              String rdns[] = dnString.replace(',', ';').split(";");
              for (int j = rdns.length - 1; j >= 0; j--)
              {
                  int rdnIndex = rdns.length - j;
                  buffer.append(",Rdn" + rdnIndex + "=") ;
                  for (int i = 0; i < rdns[j].length(); i++)
                  {
                      char c = rdns[j].charAt(i);
                      if (isAlpha(c) || isDigit(c))
                      {
                          buffer.append(c);
                      } else
                      {
                          switch (c)
                          {
                              case ' ':
                                  buffer.append("_");
                                  break;
                              case '=':
                                  buffer.append("-");
                          }
                      }
                  }
              }

              typeStr = buffer.toString();
          }

          nameStr = MBEAN_BASE_DOMAIN + ":" + "Name=rootDSE" + typeStr;
      } catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

          int msgID = MSGID_CONFIG_JMX_CANNOT_REGISTER_MBEAN;
          String message = getMessage(msgID, configEntryDN.toString(),
                  String.valueOf(e));
          logError(ErrorLogCategory.CONFIGURATION,
                  ErrorLogSeverity.SEVERE_ERROR, message, msgID);
      }
      return nameStr ;
  }

  /**
   * Creates a new dynamic JMX MBean for use with the Directory Server.
   *
   * @param  configEntryDN  The DN of the configuration entry with which this
   *                        MBean is associated.
   */
  public JMXMBean(DN configEntryDN)
    {

        this.configEntryDN = configEntryDN;

        alertGenerators = new CopyOnWriteArrayList<AlertGenerator>();
        configurableComponents =
                           new CopyOnWriteArrayList<ConfigurableComponent>();
        invokableComponents = new CopyOnWriteArrayList<InvokableComponent>();
        monitorProviders = new CopyOnWriteArrayList<MonitorProvider>();

        MBeanServer mBeanServer = DirectoryServer.getJMXMBeanServer();
        if (mBeanServer != null)
        {
            try
            {
                objectName = new ObjectName(getJmxName(configEntryDN)) ;

                try
                {
                  if(mBeanServer.isRegistered(objectName))
                  {
                    mBeanServer.unregisterMBean(objectName);
                  }
                } catch(Exception e)
                {
                  if (debugEnabled())
                  {
                    debugCaught(DebugLogLevel.ERROR, e);
                  }
                }

                mBeanServer.registerMBean(this, objectName);

            } catch (Exception e)
            {
              if (debugEnabled())
              {
                debugCaught(DebugLogLevel.ERROR, e);
              }
                e.printStackTrace();

                int msgID = MSGID_CONFIG_JMX_CANNOT_REGISTER_MBEAN;
                String message = getMessage(msgID, configEntryDN.toString(),
                        String.valueOf(e));
                logError(ErrorLogCategory.CONFIGURATION,
                        ErrorLogSeverity.SEVERE_ERROR, message, msgID);
            }
        }
    }



  /**
   * Retrieves the JMX object name for this JMX MBean.
   *
   * @return  The JMX object name for this JMX MBean.
   */
  public ObjectName getObjectName()
  {
    return objectName;
  }



  /**
   * Retrieves the set of alert generators for this JMX MBean.
   *
   * @return  The set of alert generators for this JMX MBean.
   */
  public CopyOnWriteArrayList<AlertGenerator> getAlertGenerators()
  {
    return alertGenerators;
  }



  /**
   * Adds the provided alert generator to the set of alert generators associated
   * with this JMX MBean.
   *
   * @param  generator  The alert generator to add to the set of alert
   *                    generators for this JMX MBean.
   */
  public void addAlertGenerator(AlertGenerator generator)
  {
    synchronized (alertGenerators)
    {
      if (! alertGenerators.contains(generator))
      {
        alertGenerators.add(generator);
      }
    }
  }



  /**
   * Removes the provided alert generator from the set of alert generators
   * associated with this JMX MBean.
   *
   * @param  generator  The alert generator to remove from the set of alert
   *                    generators for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the alert generator was removed, or
   *          <CODE>false</CODE> if it was not associated with this MBean.
   */
  public boolean removeAlertGenerator(AlertGenerator generator)
  {
    synchronized (alertGenerators)
    {
      return alertGenerators.remove(generator);
    }
  }



  /**
   * Retrieves the set of configurable components associated with this JMX
   * MBean.
   *
   * @return  The set of configurable components associated with this JMX MBean.
   */
  public CopyOnWriteArrayList<ConfigurableComponent> getConfigurableComponents()
  {
    return configurableComponents;
  }



  /**
   * Adds the provided configurable component to the set of components
   * associated with this JMX MBean.
   *
   * @param  component  The component to add to the set of configurable
   *                    components for this JMX MBean.
   */
  public void addConfigurableComponent(ConfigurableComponent component)
  {
    synchronized (configurableComponents)
    {
      if (! configurableComponents.contains(component))
      {
        configurableComponents.add(component);
      }
    }
  }



  /**
   * Removes the provided configurable component from the set of components
   * associated with this JMX MBean.
   *
   * @param  component  The component to remove from the set of configurable
   *                    components for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the specified component was successfully
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeConfigurableComponent(ConfigurableComponent component)
  {
    synchronized (configurableComponents)
    {
      return configurableComponents.remove(component);
    }
  }



  /**
   * Retrieves the set of invokable components associated with this JMX MBean.
   *
   * @return  The set of invokable components associated with this JMX MBean.
   */
  public CopyOnWriteArrayList<InvokableComponent> getInvokableComponents()
  {
    return invokableComponents;
  }



  /**
   * Adds the provided invokable component to the set of components associated
   * with this JMX MBean.
   *
   * @param  component  The component to add to the set of invokable components
   *                    for this JMX MBean.
   */
  public void addInvokableComponent(InvokableComponent component)
  {
    synchronized (invokableComponents)
    {
      if (! invokableComponents.contains(component))
      {
        invokableComponents.add(component);
      }
    }
  }



  /**
   * Removes the provided invokable component from the set of components
   * associated with this JMX MBean.
   *
   * @param  component  The component to remove from the set of invokable
   *                    components for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the specified component was successfully
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeInvokableComponent(InvokableComponent component)
  {
    synchronized (invokableComponents)
    {
      return invokableComponents.remove(component);
    }
  }



  /**
   * Retrieves the set of monitor providers associated with this JMX MBean.
   *
   * @return  The set of monitor providers associated with this JMX MBean.
   */
  public CopyOnWriteArrayList<MonitorProvider> getMonitorProviders()
  {
    return monitorProviders;
  }



  /**
   * Adds the given monitor provider to the set of components associated with
   * this JMX MBean.
   *
   * @param  component  The component to add to the set of monitor providers
   *                    for this JMX MBean.
   */
  public void addMonitorProvider(MonitorProvider component)
  {
    synchronized (monitorProviders)
    {
      if (! monitorProviders.contains(component))
      {
        monitorProviders.add(component);
      }
    }
  }



  /**
   * Removes the given monitor provider from the set of components associated
   * with this JMX MBean.
   *
   * @param  component  The component to remove from the set of monitor
   *                    providers for this JMX MBean.
   *
   * @return  <CODE>true</CODE> if the specified component was successfully
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeMonitorProvider(MonitorProvider component)
  {
    synchronized (monitorProviders)
    {
      return monitorProviders.remove(component);
    }
  }



  /**
   * Retrieves the specified configuration attribute.
   *
   * @param  name  The name of the configuration attribute to retrieve.
   *
   * @return  The specified configuration attribute, or <CODE>null</CODE> if
   *          there is no such attribute.
   */
  private ConfigAttribute getConfigAttribute(String name)
  {
    for (ConfigurableComponent component : configurableComponents)
    {
      for (ConfigAttribute attr : component.getConfigurationAttributes())
      {
        if (attr.getName().equalsIgnoreCase(name))
        {
          return attr;
        }
      }
    }

    return null;
  }



  /**
   * Retrieves the specified configuration attribute.
   *
   * @param  name  The name of the configuration attribute to retrieve.
   *
   * @return  The specified configuration attribute, or <CODE>null</CODE> if
   *          there is no such attribute.
   */
  private Attribute getJmxAttribute(String name)
  {
    String attributeName ;
    String pendingString = ";" + OPTION_PENDING_VALUES ;
    boolean pending = false ;
    if (name.endsWith(pendingString ))
    {
        int index = name.indexOf(pendingString) ;
        attributeName = name.substring(0,index) ;
        pending = true ;
    }
    else
    {
        attributeName = name ;
    }

    for (ConfigurableComponent component : configurableComponents)
    {
      for (ConfigAttribute attr : component.getConfigurationAttributes())
      {
        if (attr.getName().equalsIgnoreCase(attributeName))
        {
          if (pending)
          {
            return attr.toJMXAttributePending();
          }
          else
          {
            return attr.toJMXAttribute() ;
          }
        }
      }
    }

    //
    // It's possible that this is a monitor attribute rather than a
    // configurable one. Check all of those.
    AttributeType attrType =
      DirectoryServer.getAttributeType(name.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(name);
    }
    for (MonitorProvider monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        if (attrType.equals(a.getAttributeType()))
        {
          LinkedHashSet<AttributeValue> values = a.getValues();
          if (values.isEmpty())
          {
            continue;
          }

          Iterator<AttributeValue> iterator = values.iterator();
          AttributeValue value = iterator.next();

          if (iterator.hasNext())
          {
            ArrayList<String> stringValues = new ArrayList<String>();
            stringValues.add(value.getStringValue());

            while (iterator.hasNext())
            {
              value = iterator.next();
              stringValues.add(value.getStringValue());
            }

            String[] valueArray = new String[stringValues.size()];
            stringValues.toArray(valueArray);
            return new Attribute(name, valueArray);
          }
          else
          {
            return new Attribute(name, value.getStringValue());
          }
        }
      }
    }

    return null;
  }



  /**
   * Obtain the value of a specific attribute of the Dynamic MBean.
   *
   * @param  attributeName  The name of the attribute to be retrieved.
   *
   * @return  The requested attribute.
   *
   * @throws  AttributeNotFoundException  If the specified attribute is not
   *                                      associated with this MBean.
   */
  public Attribute getAttribute(String attributeName)
         throws AttributeNotFoundException
  {
    //
    // Get the jmx Client connection
    JmxClientConnection jmxClientConnection = getClientConnection();
    if (jmxClientConnection == null)
    {
      return null;
    }

    //
    // prepare the ldap search

    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    }
    catch (LDAPException e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_JMX_CANNOT_GET_ATTRIBUTE;
      String message = getMessage(msgID, String.valueOf(attributeName),
                                  String.valueOf(configEntryDN),
                                  getExceptionMessage(e));
      throw new AttributeNotFoundException(message);
    }

    //
    // Perform the Ldap operation for
    //  - ACI Check
    //  - Loggin purpose
    InternalSearchOperation op = jmxClientConnection.processSearch(
        new ASN1OctetString(configEntryDN.toString()),
        SearchScope.BASE_OBJECT, filter);

    ResultCode rc = op.getResultCode();
    if (rc != ResultCode.SUCCESS)
    {
      jmxClientConnection = null ;

      int    msgID   = MSGID_CONFIG_JMX_CANNOT_GET_ATTRIBUTE;
      String message = getMessage(msgID, String.valueOf(attributeName),
                                  String.valueOf(configEntryDN),
                                  String.valueOf(op.getErrorMessage()));
      throw new AttributeNotFoundException(message);
    }

    try
    {
      return getJmxAttribute(attributeName);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_JMX_ATTR_NO_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  attributeName);

      logError(
          ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
          message, msgID);
      throw new AttributeNotFoundException(message);
    }
  }


  /**
   * Convert an JMX attribute into an LDAP attribute.
   *
   * @param attribute
   *        The JMX attribute which needs to be converted into an LDAP
   *        attribute
   * @param configEntry
   *        The associated ConfigEntry
   * @return The converted LDAP attribute
   * @throws AttributeNotFoundException
   * @throws InvalidAttributeValueException
   */
private LDAPAttribute getLdapAttributeFromJmx(
      javax.management.Attribute attribute, ConfigEntry configEntry)
      throws AttributeNotFoundException, InvalidAttributeValueException
  {
    String name = attribute.getName() ;
    //
    // Get a duplicated version of the config attribute
    ConfigAttribute configAttribute;
    try
    {
      configAttribute = getConfigAttribute(name).duplicate();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_JMX_ATTR_NO_ATTR;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(name));

      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.MILD_ERROR, message, msgID);
      throw new AttributeNotFoundException(message);
    }

    //
    // Update the config Attribute value
    try
    {
      configAttribute.setValue(attribute);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }
      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.MILD_ERROR,
          MSGID_CONFIG_JMX_ATTR_INVALID_VALUE,
          configEntryDN.toString(),
          String.valueOf(e));
      throw new InvalidAttributeValueException();
    }

    //
    // Update the config entry (and the entry)
    configEntry.putConfigAttribute(configAttribute);

    //
    // Get the Ldap attribute associated with this name
    AttributeType attrType =
         DirectoryServer.getAttributeType(name.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(name, configAttribute
          .getSyntax());
    }

    return new LDAPAttribute(configEntry.getEntry().getAttribute(attrType).get(
        0));
  }

  /**
   * Set the value of a specific attribute of the Dynamic MBean.
   *
   * @param  attribute  The identification of the attribute to be set and the
   *                    value it is to be set to.
   *
   * @throws  AttributeNotFoundException  If the specified attribute is not
   *                                       associated with this MBean.
   *
   * @throws  InvalidAttributeValueException  If the provided value is not
   *                                          acceptable for this MBean.
   */
  public void setAttribute(javax.management.Attribute attribute)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {
    ConfigEntry configEntry;
    ConfigEntry newConfigEntry ;

    //
    // Get the associated ConfigEntry, and duplicate it
    try
    {
      configEntry = DirectoryServer.getConfigHandler().getConfigEntry(
          configEntryDN);
      newConfigEntry = configEntry.duplicate();
    } catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_JMX_CANNOT_GET_CONFIG_ENTRY;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(e));

      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.MILD_ERROR, message, msgID);
      throw new AttributeNotFoundException(message);
    }

    //
    // Get the jmx Client connection
    JmxClientConnection jmxClientConnection = getClientConnection();
    if (jmxClientConnection == null)
    {
      int    msgID   = MSGID_CONFIG_JMX_SET_ATTR_NO_CONNECTION;
      String message = getMessage(msgID, attribute.getName(),
                                  String.valueOf(configEntry.getDN()));
      throw new AttributeNotFoundException(message);
    }

    //
    // prepare the ldap modify
    LDAPModification ldapModification = new LDAPModification(
        ModificationType.REPLACE, getLdapAttributeFromJmx(
            attribute,
            newConfigEntry));
    ArrayList<RawModification> ldapModList = new ArrayList<RawModification>();
    ldapModList.add(ldapModification);

    //
    // Process the modify
    ModifyOperation op = jmxClientConnection.processModify(
          new ASN1OctetString(configEntryDN.toString()),
          ldapModList);

    ResultCode rc = op.getResultCode();
    if (rc != ResultCode.SUCCESS)
    {
      jmxClientConnection = null ;
      throw new InvalidAttributeValueException();
    }
    //
    // return part
    jmxClientConnection = null ;
    return ;
  }

  /**
   * Get the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of the attributes to be retrieved.
   *
   * @return  The list of attributes retrieved.
   */
  public AttributeList getAttributes(String[] attributes)
    {

    //
    // Get the jmx Client connection
    JmxClientConnection jmxClientConnection = getClientConnection();
    if (jmxClientConnection == null)
    {
      return null;
    }

    //
    // prepare the ldap search
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    }
    catch (LDAPException e)
    {
      return null;
    }

    //
    // Perform the Ldap operation for
    //  - ACI Check
    //  - Loggin purpose
    InternalSearchOperation op = jmxClientConnection.processSearch(
        new ASN1OctetString(configEntryDN.toString()),
        SearchScope.BASE_OBJECT, filter);

    ResultCode rc = op.getResultCode();
    if (rc != ResultCode.SUCCESS)
    {
      jmxClientConnection = null ;
      return null;
    }


    AttributeList attrList = new AttributeList(attributes.length);
    Attribute attr;
    for (String name : attributes)
    {
      try
      {
        if ((attr = getJmxAttribute(name)) != null)
        {
          attrList.add(attr);
          continue;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      // It's possible that this is a monitor attribute rather than a
      // configurable one. Check all of those.
      AttributeType attrType =
        DirectoryServer.getAttributeType(name.toLowerCase());
      if (attrType == null)
      {
        attrType = DirectoryServer.getDefaultAttributeType(name);
      }

      monitorLoop: for (MonitorProvider monitor : monitorProviders)
      {
        for (org.opends.server.types.Attribute a : monitor.getMonitorData())
        {
          if (attrType.equals(a.getAttributeType()))
          {
            LinkedHashSet<AttributeValue> values = a.getValues();
            if (values.isEmpty())
            {
              continue;
            }

            Iterator<AttributeValue> iterator = values.iterator();
            AttributeValue value = iterator.next();

            if (iterator.hasNext())
            {
              ArrayList<String> stringValues = new ArrayList<String>();
              stringValues.add(value.getStringValue());

              while (iterator.hasNext())
              {
                value = iterator.next();
                stringValues.add(value.getStringValue());
              }

              String[] valueArray = new String[stringValues.size()];
              stringValues.toArray(valueArray);
              attrList.add(new Attribute(name, valueArray));
              break monitorLoop;
            }
            else
            {
              attrList.add(new Attribute(name, value.getStringValue()));
              break monitorLoop;
            }
          }
        }
      }
    }

    return attrList;

  }

  /**
   * Sets the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of attributes:  The identification of the
   *                     attributes to be set and the values they are to be set
   *                     to.
   *
   * @return  The list of attributes that were set with their new values.
   */
  public AttributeList setAttributes(AttributeList attributes)
  {
    AttributeList setAttrs = new AttributeList();

    //
    ConfigEntry configEntry;
    ConfigEntry newConfigEntry ;

    //
    // Get the associated ConfigEntry, and duplicate it
    try
    {
      configEntry = DirectoryServer.getConfigHandler().getConfigEntry(
          configEntryDN);
      newConfigEntry = configEntry.duplicate();
    } catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      logError(
          ErrorLogCategory.CONFIGURATION,
          ErrorLogSeverity.MILD_ERROR,
          MSGID_CONFIG_JMX_CANNOT_GET_CONFIG_ENTRY,
          configEntryDN.toString(),
          String.valueOf(e));
      return setAttrs;
    }

    //
    // Get the jmx Client connection
    JmxClientConnection jmxClientConnection = getClientConnection();
    if (jmxClientConnection == null)
    {
      return setAttrs;
    }

    //
    // prepare the ldap modify
    ArrayList<RawModification> ldapModList = new ArrayList<RawModification>();
    for (Object o : attributes)
    {
      Attribute attribute = (Attribute) o;
      try
      {
        LDAPModification ldapModification = new LDAPModification(
            ModificationType.REPLACE, getLdapAttributeFromJmx(
                attribute, newConfigEntry));
                ldapModList.add(ldapModification);
      }
      catch (Exception e)
      {
        continue ;
      }
    }

    //
    // Process the modify
    // TODO What about the return code?

    jmxClientConnection.processModify(
        new ASN1OctetString(configEntryDN.toString()),
        ldapModList);

    //
    // return part
    jmxClientConnection = null ;
    for (Object o : attributes)
    {
      Attribute attribute = (Attribute) o;
      ConfigAttribute configAttribute;
      try
      {
        configAttribute = getConfigAttribute(attribute.getName());
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_ERROR,
                 MSGID_CONFIG_JMX_ATTR_NO_ATTR, configEntryDN.toString(),
                 attribute.getName());
        continue;
      }
      configAttribute.toJMXAttribute(setAttrs);
    }
    return setAttrs;
  }



  /**
   * Allows an action to be invoked on the Dynamic MBean.
   *
   * @param  actionName  The name of the action to be invoked.
   * @param  params      An array containing the parameters to be set when the
   *                     action is invoked.
   * @param  signature   An array containing the signature of the action.  The
   *                     class objects will be loaded through the same class
   *                     loader as the one used for loading the MBean on which
   *                     action is invoked.
   *
   * @return  The object returned by the action, which represents the result of
   *          invoking the action on the MBean specified.
   *
   * @throws  MBeanException  If a problem is encountered while invoking the
   *                          method.
   */
  public Object invoke(String actionName, Object[] params, String[] signature)
         throws MBeanException
  {
    for (InvokableComponent component : invokableComponents)
    {
      for (InvokableMethod method : component.getOperationSignatures())
      {
        if (method.hasSignature(actionName, signature))
        {
          try
          {
            method.invoke(component, params);
          }
          catch (MBeanException me)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, me);
            }

            throw me;
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            throw new MBeanException(e);
          }
        }
      }
    }


    // If we've gotten here, then there is no such method so throw an exception.
    StringBuilder buffer = new StringBuilder();
    buffer.append(actionName);
    buffer.append("(");

    if (signature.length > 0)
    {
      buffer.append(signature[0]);

      for (int i=1; i < signature.length; i++)
      {
        buffer.append(", ");
        buffer.append(signature[i]);
      }
    }

    buffer.append(")");

    int    msgID   = MSGID_CONFIG_JMX_NO_METHOD;
    String message = getMessage(msgID, buffer.toString(),
                                configEntryDN.toString());
    throw new MBeanException(
                   new DirectoryException(ResultCode.NO_SUCH_OPERATION,
                                          message,
                                          msgID));
  }



  /**
   * Provides the exposed attributes and actions of the Dynamic MBean using an
   * MBeanInfo object.
   *
   * @return  An instance of <CODE>MBeanInfo</CODE> allowing all attributes and
   *          actions exposed by this Dynamic MBean to be retrieved.
   */
  public MBeanInfo getMBeanInfo()
  {
    JmxClientConnection jmxClientConnection = getClientConnection();
    if (jmxClientConnection == null)
    {
      return new MBeanInfo(CLASS_NAME, null, null, null, null, null);
    }

    ArrayList<MBeanAttributeInfo> attrs = new ArrayList<MBeanAttributeInfo>();
    for (ConfigurableComponent component : configurableComponents)
    {
      for (ConfigAttribute attr : component.getConfigurationAttributes())
      {
        attr.toJMXAttributeInfo(attrs);
      }
    }

    for (MonitorProvider monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        attrs.add(new MBeanAttributeInfo(a.getName(), String.class.getName(),
                                         null, true, false, false));
      }
    }

    MBeanAttributeInfo[] mBeanAttributes = new MBeanAttributeInfo[attrs.size()];
    attrs.toArray(mBeanAttributes);


    ArrayList<MBeanNotificationInfo> notifications =
         new ArrayList<MBeanNotificationInfo>();
    for (AlertGenerator generator : alertGenerators)
    {
      String className = generator.getClassName();

      LinkedHashMap<String,String> alerts = generator.getAlerts();
      for (String type : alerts.keySet())
      {
        String[] types       = { type };
        String   description = alerts.get(type);
        notifications.add(new MBeanNotificationInfo(types, className,
                                                    description));
      }
    }


    MBeanNotificationInfo[] mBeanNotifications =
         new MBeanNotificationInfo[notifications.size()];
    notifications.toArray(mBeanNotifications);


    ArrayList<MBeanOperationInfo> ops = new ArrayList<MBeanOperationInfo>();
    for (InvokableComponent component : invokableComponents)
    {
      for (InvokableMethod method : component.getOperationSignatures())
      {
        ops.add(method.toOperationInfo());
      }
    }

    MBeanOperationInfo[] mBeanOperations = new MBeanOperationInfo[ops.size()];
    ops.toArray(mBeanOperations);


    MBeanConstructorInfo[]  mBeanConstructors  = new MBeanConstructorInfo[0];
    return new MBeanInfo(CLASS_NAME,
                         "Configurable Attributes for " +
                              configEntryDN.toString(),
                         mBeanAttributes, mBeanConstructors, mBeanOperations,
                         mBeanNotifications);
  }

  /**
   * Get the client JMX connection to use. Returns null if an Exception is
   * caught or if the AccessControlContext subject is null.
   *
   * @return The JmxClientConnection.
   */
  private JmxClientConnection getClientConnection()
  {
      JmxClientConnection jmxClientConnection=null;
      java.security.AccessControlContext acc = java.security.AccessController
      .getContext();
      try
      {
          javax.security.auth.Subject subject = javax.security.auth.Subject
          .getSubject(acc);
          if(subject != null) {
            Set privateCreds = subject.getPrivateCredentials(Credential.class);
            jmxClientConnection = ((Credential) privateCreds
                    .iterator().next()).getClientConnection();
          }
      }
      catch (Exception e) {}
      return jmxClientConnection;
  }
}


