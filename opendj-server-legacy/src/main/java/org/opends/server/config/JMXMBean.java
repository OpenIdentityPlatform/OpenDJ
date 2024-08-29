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
 * Portions Copyright 2006-2007-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2023-2024 3A Systems LLC.
 */
package org.opends.server.config;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.forgerock.util.Utils;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.jmx.Credential;
import org.opends.server.protocols.jmx.JmxClientConnection;
import org.opends.server.types.DirectoryException;

import javax.management.Attribute;
import javax.management.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import static javax.xml.bind.DatatypeConverter.printDateTime;
import static org.forgerock.opendj.ldap.Functions.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.opends.server.util.CollectionUtils.newArrayList;
import static org.opends.server.util.ServerConstants.MBEAN_BASE_DOMAIN;
import static org.opends.server.util.StaticUtils.isAlpha;
import static org.opends.server.util.StaticUtils.isDigit;
import static org.opends.server.schema.SchemaConstants.SYNTAX_INTEGER_OID;

/**
 * This class defines a JMX MBean that can be registered with the Directory
 * Server to provide monitoring and statistical information, provide read and/or
 * read-write access to the configuration, and provide notifications and alerts
 * if a significant event or severe/fatal error occurs.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class JMXMBean
       implements DynamicMBean, DirectoryServerMBean
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The fully-qualified name of this class. */
  private static final String CLASS_NAME = "org.opends.server.config.JMXMBean";


  /** The set of alert generators for this MBean. */
  private List<AlertGenerator> alertGenerators;
  /** The set of monitor providers for this MBean. */
  private List<MonitorProvider<? extends MonitorProviderCfg>> monitorProviders;
  /** The DN of the configuration entry with which this MBean is associated. */
  private DN configEntryDN;
  /** The object name for this MBean. */
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
      try
      {
          String typeStr = null;
          String dnString = configEntryDN.toString();
          if (dnString != null && dnString.length() != 0)
          {
              StringBuilder buffer = new StringBuilder(dnString.length());
              String rdns[] = dnString.replace(',', ';').split(";");
              for (int j = rdns.length - 1; j >= 0; j--)
              {
                  int rdnIndex = rdns.length - j;
                  buffer.append(",Rdn").append(rdnIndex).append("=") ;
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

          return MBEAN_BASE_DOMAIN + ":" + "Name=rootDSE" + typeStr;
      } catch (Exception e)
      {
        logger.traceException(e);
        logger.error(ERR_CONFIG_JMX_CANNOT_REGISTER_MBEAN, configEntryDN, e);
        return null;
      }
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

        alertGenerators = new CopyOnWriteArrayList<>();
        monitorProviders = new CopyOnWriteArrayList<>();

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
                }
                catch(Exception e)
                {
                  logger.traceException(e);
                }

                mBeanServer.registerMBean(this, objectName);

            }
            catch (Exception e)
            {
              logger.traceException(e);
              logger.error(ERR_CONFIG_JMX_CANNOT_REGISTER_MBEAN, configEntryDN, e);
            }
        }
    }



  /**
   * Retrieves the JMX object name for this JMX MBean.
   *
   * @return  The JMX object name for this JMX MBean.
   */
  @Override
  public ObjectName getObjectName()
  {
    return objectName;
  }



  /**
   * Retrieves the set of alert generators for this JMX MBean.
   *
   * @return  The set of alert generators for this JMX MBean.
   */
  public List<AlertGenerator> getAlertGenerators()
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
   * @return  {@code true} if the alert generator was removed,
   *          or {@code false} if it was not associated with this MBean.
   */
  public boolean removeAlertGenerator(AlertGenerator generator)
  {
    synchronized (alertGenerators)
    {
      return alertGenerators.remove(generator);
    }
  }

  /**
   * Retrieves the set of monitor providers associated with this JMX MBean.
   *
   * @return  The set of monitor providers associated with this JMX MBean.
   */
  public List<MonitorProvider<? extends MonitorProviderCfg>>
              getMonitorProviders()
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
  public void addMonitorProvider(MonitorProvider<? extends MonitorProviderCfg> component)
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
   * @return  {@code true} if the specified component was successfully removed,
   *          or {@code false} if not.
   */
  public boolean removeMonitorProvider(MonitorProvider<?> component)
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
   * @return  The specified configuration attribute, or {@code null} if
   *          there is no such attribute.
   */
  private Attribute getJmxAttribute(String name)
  {
    // It's possible that this is a monitor attribute rather than a configurable
    // one. Check all of those.
    AttributeType attrType = DirectoryServer.getInstance().getServerContext().getSchema().getAttributeType(name);
    for (MonitorProvider<? extends MonitorProviderCfg> monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        if (attrType.equals(a.getAttributeDescription().getAttributeType()))
        {
          if (a.isEmpty())
          {
            continue;
          }

          Iterator<ByteString> iterator = a.iterator();
          ByteString firstValue = iterator.next();

          if (iterator.hasNext())
          {
            List<Object> valueList = newArrayList(getAttributeValue(a.getAttributeDescription(), firstValue));
            while (iterator.hasNext())
            {
              ByteString value = iterator.next();
              valueList.add(getAttributeValue(a.getAttributeDescription(), value));
            }

            Object[] valueArray = valueList.toArray(new Object[0]);
            return new Attribute(name, valueArray);
          }
          else
          {
            return new Attribute(name, getAttributeValue(a.getAttributeDescription(), firstValue));
          }
        }
      }
    }
    return null;
  }

  private Object getAttributeValue(AttributeDescription ad, ByteString value) {
    final Syntax syntax = ad.getAttributeType().getSyntax();
    if (syntax.equals(CoreSchema.getBooleanSyntax())) {
      return byteStringToBoolean().apply(value);
    } else if (syntax.equals(CoreSchema.getIntegerSyntax())) {
      return byteStringToLong().apply(value);
    } else if (syntax.equals(CoreSchema.getGeneralizedTimeSyntax())) {
      return printDateTime(byteStringToGeneralizedTime().apply(value).toCalendar());
    } else {
      return byteStringToString().apply(value);
    }
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
  @Override
  public Object getAttribute(String attributeName)
         throws AttributeNotFoundException
  {
    // Get the jmx Client connection
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return null;
    }

    // prepare the ldap search
    try
    {
      // Perform the Ldap operation for
      //  - ACI Check
      //  - Loggin purpose
      InternalSearchOperation op = searchMBeanConfigEntry(clientConnection);
      // BUG : op may be null
      ResultCode rc = op.getResultCode();
      if (rc != ResultCode.SUCCESS) {
        LocalizableMessage message = ERR_CONFIG_JMX_CANNOT_GET_ATTRIBUTE.
            get(attributeName, configEntryDN, op.getErrorMessage());
        throw new AttributeNotFoundException(message.toString());
      }
      Attribute attr=getJmxAttribute(attributeName);
      if (attr==null) {
        throw new AttributeNotFoundException(attributeName);
      }
      return attr.getValue();
    }
    catch (AttributeNotFoundException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_JMX_ATTR_NO_ATTR.get(configEntryDN, attributeName);
      logger.error(message);
      throw new AttributeNotFoundException(message.toString());
    }
  }

  /**
   * Set the value of a specific attribute of the Dynamic MBean.  In this case,
   * it will always throw {@code InvalidAttributeValueException} because setting
   * attribute values over JMX is currently not allowed.
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
  public void setAttribute(javax.management.Attribute attribute)
         throws AttributeNotFoundException, InvalidAttributeValueException
  {
    throw new InvalidAttributeValueException();
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
    // Get the jmx Client connection
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return null;
    }

    // Perform the Ldap operation for
    //  - ACI Check
    //  - Loggin purpose
    InternalSearchOperation op = searchMBeanConfigEntry(clientConnection);
    if (op == null)
    {
      return null;
    }

    ResultCode rc = op.getResultCode();
    if (rc != ResultCode.SUCCESS)
    {
      return null;
    }


    AttributeList attrList = new AttributeList(attributes.length);
    for (String name : attributes)
    {
      try
      {
        Attribute attr = getJmxAttribute(name);
        if (attr != null)
        {
          attrList.add(attr);
          continue;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
      Attribute attr = getJmxAttribute(name);
      if (attr != null)
      {
        attrList.add(attr);
      }
    }

    return attrList;
  }

  private InternalSearchOperation searchMBeanConfigEntry(ClientConnection clientConnection)
  {
    SearchRequest request = newSearchRequest(configEntryDN, SearchScope.BASE_OBJECT);
    if (clientConnection instanceof JmxClientConnection) {
      return ((JmxClientConnection) clientConnection).processSearch(request);
    }
    else if (clientConnection instanceof InternalClientConnection) {
      return ((InternalClientConnection) clientConnection).processSearch(request);
    }
    return null;
  }

  /**
   * Sets the values of several attributes of the Dynamic MBean.
   *
   * @param  attributes  A list of attributes:  The identification of the
   *                     attributes to be set and the values they are to be set
   *                     to.
   *
   * @return  The list of attributes that were set with their new values.  In
   *          this case, the list will always be empty because we do not support
   *          setting attribute values over JMX.
   */
  @Override
  public AttributeList setAttributes(AttributeList attributes)
  {
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
    // If we've gotten here, then there is no such method so throw an exception.
    StringBuilder buffer = new StringBuilder();
    buffer.append(actionName);
    buffer.append("(");
    Utils.joinAsString(buffer, ", ", (Object[]) signature);
    buffer.append(")");

    LocalizableMessage message = ERR_CONFIG_JMX_NO_METHOD.get(buffer, configEntryDN);
    throw new MBeanException(
        new DirectoryException(ResultCode.NO_SUCH_OPERATION, message));
  }



  /**
   * Provides the exposed attributes and actions of the Dynamic MBean using an
   * MBeanInfo object.
   *
   * @return  An instance of {@code MBeanInfo} allowing all attributes and
   *          actions exposed by this Dynamic MBean to be retrieved.
   */
  @Override
  public MBeanInfo getMBeanInfo()
  {
    ClientConnection clientConnection = getClientConnection();
    if (clientConnection == null)
    {
      return new MBeanInfo(CLASS_NAME, null, null, null, null, null);
    }

    List<MBeanAttributeInfo> attrs = new ArrayList<>();
    for (MonitorProvider<? extends MonitorProviderCfg> monitor : monitorProviders)
    {
      for (org.opends.server.types.Attribute a : monitor.getMonitorData())
      {
        final String className = getAttributeClass(a.getAttributeDescription()).getName();
        attrs.add(new MBeanAttributeInfo(a.getAttributeDescription().getNameOrOID(), className,
                                         null, true, false, false));
      }
    }

    MBeanAttributeInfo[] mBeanAttributes = attrs.toArray(new MBeanAttributeInfo[attrs.size()]);

    List<MBeanNotificationInfo> notifications = new ArrayList<>();
    for (AlertGenerator generator : alertGenerators)
    {
      String className = generator.getClassName();

      Map<String, String> alerts = generator.getAlerts();
      for (Entry<String, String> mapEntry : alerts.entrySet())
      {
        String[] types       = { mapEntry.getKey() };
        String   description = mapEntry.getValue();
        notifications.add(new MBeanNotificationInfo(types, className, description));
      }
    }

    MBeanConstructorInfo[] mBeanConstructors = new MBeanConstructorInfo[0];
    MBeanOperationInfo[] mBeanOperations = new MBeanOperationInfo[0];

    MBeanNotificationInfo[] mBeanNotifications = new MBeanNotificationInfo[notifications.size()];
    notifications.toArray(mBeanNotifications);

    return new MBeanInfo(CLASS_NAME,
                         "Configurable Attributes for " + configEntryDN,
                         mBeanAttributes, mBeanConstructors, mBeanOperations,
                         mBeanNotifications);
  }

  private Class<?> getAttributeClass(AttributeDescription ad) {
    final Syntax syntax = ad.getAttributeType().getSyntax();
    if (syntax.equals(CoreSchema.getBooleanSyntax())) {
      return Boolean.class;
    } else if (syntax.equals(CoreSchema.getIntegerSyntax())) {
      return Long.class;
    } else {
      return String.class;
    }
  }

  /**
   * Get the client JMX connection to use. Returns null if an Exception is
   * caught or if the AccessControlContext subject is null.
   *
   * @return The JmxClientConnection.
   */
  private ClientConnection getClientConnection()
  {
    java.security.AccessControlContext acc = java.security.AccessController.getContext();
    try
    {
      javax.security.auth.Subject subject = javax.security.auth.Subject.getSubject(acc);
      if (subject != null)
      {
        Set<?> privateCreds = subject.getPrivateCredentials(Credential.class);
        return ((Credential) privateCreds.iterator().next()).getClientConnection();
      }
    }
    catch (Exception e)
    {
    }
    return null;
  }
}
