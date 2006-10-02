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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;

import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This interface defines the set of methods that must be implemented for a
 * Directory Server alert handler.  Alert handlers are used to present alert
 * notifications in various forms like JMX, e-mail, or paging.
 */
public class JMXAlertHandler
       extends NotificationBroadcasterSupport
       implements AlertHandler, DynamicMBean, DirectoryServerMBean
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.JMXAlertHandler";



  // The sequence number generator used for this alert handler.
  private AtomicLong sequenceNumber;

  // The DN of the configuration entry with which this alert handler is
  // associated.
  private DN configEntryDN;

  // The JMX object name used for this JMX alert handler.
  private ObjectName objectName;



  /**
   * Creates a new instance of this JMX alert handler.  No initialization should
   * be done here, as it should all be performed in the
   * <CODE>initializeAlertHandler</CODE> method.
   */
  public JMXAlertHandler()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * Initializes this alert handler based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this alert handler.
   *
   * @throws  ConfigException  If the provided entry does not contain a valid
   *                           configuration for this alert handler.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeAlertHandler(ConfigEntry configEntry)
       throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeAlertHandler",
                      String.valueOf(configEntry));

    sequenceNumber = new AtomicLong(1);

    if (configEntry == null)
    {
      configEntryDN = null;
    }
    else
    {
      configEntryDN = configEntry.getDN();
    }

    MBeanServer mBeanServer = DirectoryServer.getJMXMBeanServer();
    if (mBeanServer != null)
    {
      try
      {
        String nameStr = MBEAN_BASE_DOMAIN + ":type=JMXAlertHandler";
        objectName = new ObjectName(nameStr);
        if (mBeanServer.isRegistered(objectName))
        {
          mBeanServer.unregisterMBean(objectName);
        }

        mBeanServer.registerMBean(this, objectName);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeAlertHandler", e);

        int    msgID   = MSGID_JMX_ALERT_HANDLER_CANNOT_REGISTER;
        String message = getMessage(msgID, String.valueOf(e));
        throw new InitializationException(msgID, message, e);
      }
    }
  }



  /**
   * Performs any necessary cleanup that may be necessary when this
   * alert handler is finalized.
   */
  public void finalizeAlertHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeAlertHandler");

    // No action is required.
  }



  /**
   * Retrieves the JMX object name for this JMX alert handler.
   *
   * @return  The JMX object name for this JMX alert handler.
   */
  public ObjectName getObjectName()
  {
    assert debugEnter(CLASS_NAME, "getObjectName");

    return objectName;
  }



  /**
   * Sends an alert notification based on the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertID       The alert ID that uniquely identifies the type of
   *                       alert.
   * @param  alertMessage  A message (possibly <CODE>null</CODE>) that can
   *                       provide more information about this alert.
   */
  public void sendAlertNotification(AlertGenerator generator, String alertType,
                                    int alertID, String alertMessage)
  {
    assert debugEnter(CLASS_NAME, "sendAlertNotification",
                      String.valueOf(generator), String.valueOf(alertID),
                      String.valueOf(alertMessage));

    sendNotification(new Notification(alertType, generator.getClassName(),
                                      sequenceNumber.getAndIncrement(),
                                      System.currentTimeMillis(),
                                      alertMessage));
  }



  /**
   * Retrieves information about the types of JMX notifications that may be
   * generated.
   *
   * @return  Information about the types of JMX notifications that may be
   *          generated.
   */
  public MBeanNotificationInfo[] getNotificationInfo()
  {
    assert debugEnter(CLASS_NAME, "getNotificationInfo");

    ArrayList<MBeanNotificationInfo> notifications =
         new ArrayList<MBeanNotificationInfo>();
    ConcurrentHashMap<DN,JMXMBean> mBeans = DirectoryServer.getJMXMBeans();
    for (JMXMBean mBean : mBeans.values())
    {
      MBeanInfo mBeanInfo = mBean.getMBeanInfo();
      for (MBeanNotificationInfo notification: mBeanInfo.getNotifications())
      {
        notifications.add(notification);
      }
    }

    MBeanNotificationInfo[] notificationArray =
         new MBeanNotificationInfo[notifications.size()];
    notifications.toArray(notificationArray);
    return notificationArray;
  }



  /**
   * Obtain the value of a specific attribute of the Dynamic MBean.
   *
   * @param  attribute  The name of the attribute to be retrieved.
   *
   * @return  The requested MBean attribute.
   *
   * @throws  AttributeNotFoundException  If the specified attribute is not
   *                                      associated with this MBean.
   */
  public Attribute getAttribute(String attribute)
         throws AttributeNotFoundException
  {
    assert debugEnter(CLASS_NAME, "getAttribute");

    // There are no attributes for this MBean.
    int    msgID   = MSGID_CONFIG_JMX_ATTR_NO_ATTR;
    String message = getMessage(msgID, String.valueOf(configEntryDN),
                                attribute);
    throw new AttributeNotFoundException(message);
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
  public void setAttribute(Attribute attribute)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {
    assert debugEnter(CLASS_NAME, "setAttribute", String.valueOf(attribute));


    // There are no attributes for this MBean.
    int    msgID   = MSGID_CONFIG_JMX_ATTR_NO_ATTR;
    String message = getMessage(msgID, String.valueOf(configEntryDN),
                                attribute);
    throw new AttributeNotFoundException(message);
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
    assert debugEnter(CLASS_NAME, "getAttributes", String.valueOf(attributes));


    // There are no attributes for this MBean.
    return new AttributeList();
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
    assert debugEnter(CLASS_NAME, "setAttributes", String.valueOf(attributes));

    // There are no attributes for this MBean.
    return new AttributeList();
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
    assert debugEnter(CLASS_NAME, "invoke", String.valueOf(actionName),
                      String.valueOf(params), String.valueOf(signature));

    // There are no invokable components for this MBean.
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
                                String.valueOf(configEntryDN));
    throw new MBeanException(new ConfigException(msgID, message));
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
    assert debugEnter(CLASS_NAME, "getMBeanInfo");

    return new MBeanInfo(CLASS_NAME, "JMX Alert Handler",
                         new MBeanAttributeInfo[0], new MBeanConstructorInfo[0],
                         new MBeanOperationInfo[0], getNotificationInfo());
  }
}

