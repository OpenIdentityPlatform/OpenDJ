/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.AlertHandlerCfg;
import org.forgerock.opendj.server.config.server.JMXAlertHandlerCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.config.JMXMBean;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * This class provides an implementation of a Directory Server alert handler
 * that will send alerts using JMX notifications.
 */
public class JMXAlertHandler
       extends NotificationBroadcasterSupport
       implements AlertHandler<JMXAlertHandlerCfg>,
                  ConfigurationChangeListener<JMXAlertHandlerCfg>, DynamicMBean,
                  DirectoryServerMBean
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.JMXAlertHandler";

  /** The current configuration for this alert handler. */
  private AlertHandlerCfg currentConfig;

  /** The sequence number generator used for this alert handler. */
  private AtomicLong sequenceNumber;

  /** The DN of the configuration entry with which this alert handler is associated. */
  private DN configEntryDN;

  /** The JMX object name used for this JMX alert handler. */
  private ObjectName objectName;

  /**
   * Creates a new instance of this JMX alert handler.  No initialization should
   * be done here, as it should all be performed in the
   * <CODE>initializeAlertHandler</CODE> method.
   */
  public JMXAlertHandler()
  {
    super();
  }

  @Override
  public void initializeAlertHandler(JMXAlertHandlerCfg configuration)
       throws ConfigException, InitializationException
  {
    sequenceNumber = new AtomicLong(1);

    if (configuration == null)
    {
      configEntryDN = null;
    }
    else
    {
      configEntryDN = configuration.dn();
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
        logger.traceException(e);

        LocalizableMessage message = ERR_JMX_ALERT_HANDLER_CANNOT_REGISTER.get(e);
        throw new InitializationException(message, e);
      }
    }

    if (configuration != null)
    {
      configuration.addJMXChangeListener(this);
      currentConfig = configuration;
    }
  }

  @Override
  public AlertHandlerCfg getAlertHandlerConfiguration()
  {
    return currentConfig;
  }

  @Override
  public boolean isConfigurationAcceptable(AlertHandlerCfg configuration,
                                           List<LocalizableMessage> unacceptableReasons)
  {
    JMXAlertHandlerCfg cfg = (JMXAlertHandlerCfg) configuration;
    return isConfigurationChangeAcceptable(cfg, unacceptableReasons);
  }

  @Override
  public void finalizeAlertHandler()
  {
    // No action is required.
  }

  /**
   * Retrieves the JMX object name for this JMX alert handler.
   *
   * @return  The JMX object name for this JMX alert handler.
   */
  @Override
  public ObjectName getObjectName()
  {
    return objectName;
  }

  @Override
  public void sendAlertNotification(AlertGenerator generator, String alertType,
                                    LocalizableMessage alertMessage)
  {
    sendNotification(new Notification(alertType, generator.getClassName(),
                                      sequenceNumber.getAndIncrement(),
                                      System.currentTimeMillis(),
                                      alertMessage.toString()));
  }

  /**
   * Retrieves information about the types of JMX notifications that may be
   * generated.
   *
   * @return  Information about the types of JMX notifications that may be
   *          generated.
   */
  @Override
  public MBeanNotificationInfo[] getNotificationInfo()
  {
    List<MBeanNotificationInfo> notifications = new ArrayList<>();
    for (JMXMBean mBean : DirectoryServer.getJMXMBeans())
    {
      MBeanInfo mBeanInfo = mBean.getMBeanInfo();
      Collections.addAll(notifications, mBeanInfo.getNotifications());
    }

    return notifications.toArray(new MBeanNotificationInfo[notifications.size()]);
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
  @Override
  public Attribute getAttribute(String attribute)
         throws AttributeNotFoundException
  {
    // There are no attributes for this MBean.
    LocalizableMessage message = ERR_CONFIG_JMX_ATTR_NO_ATTR.get(configEntryDN, attribute);
    throw new AttributeNotFoundException(message.toString());
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
  @Override
  public void setAttribute(Attribute attribute)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {
    // There are no attributes for this MBean.
    LocalizableMessage message = ERR_CONFIG_JMX_ATTR_NO_ATTR.get(configEntryDN, attribute);
    throw new AttributeNotFoundException(message.toString());
  }

  /**
   * Get the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of the attributes to be retrieved.
   *
   * @return  The list of attributes retrieved.
   */
  @Override
  public AttributeList getAttributes(String[] attributes)
  {
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
  @Override
  public AttributeList setAttributes(AttributeList attributes)
  {
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
  @Override
  public Object invoke(String actionName, Object[] params, String[] signature)
         throws MBeanException
  {
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

    LocalizableMessage message = ERR_CONFIG_JMX_NO_METHOD.get(buffer, configEntryDN);
    throw new MBeanException(new ConfigException(message));
  }

  /**
   * Provides the exposed attributes and actions of the Dynamic MBean using an
   * MBeanInfo object.
   *
   * @return  An instance of <CODE>MBeanInfo</CODE> allowing all attributes and
   *          actions exposed by this Dynamic MBean to be retrieved.
   */
  @Override
  public MBeanInfo getMBeanInfo()
  {
    return new MBeanInfo(CLASS_NAME, "JMX Alert Handler",
                         new MBeanAttributeInfo[0], new MBeanConstructorInfo[0],
                         new MBeanOperationInfo[0], getNotificationInfo());
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
                      JMXAlertHandlerCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(
                                        JMXAlertHandlerCfg configuration)
  {
    currentConfig = configuration;
    return new ConfigChangeResult();
  }
}
