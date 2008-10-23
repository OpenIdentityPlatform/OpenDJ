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
package org.opends.server.loggers;
import org.opends.messages.Message;



import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.admin.std.meta.AccessLogPublisherCfgDefn;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.config.ConfigException;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.
    ERR_CONFIG_LOGGER_CANNOT_CREATE_LOGGER;
import static org.opends.messages.ConfigMessages.
    ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;


/**
 * This class defines the wrapper that will invoke all registered access loggers
 * for each type of request received or response sent.
 */
public class AccessLogger implements
    ConfigurationAddListener<AccessLogPublisherCfg>,
    ConfigurationDeleteListener<AccessLogPublisherCfg>,
    ConfigurationChangeListener<AccessLogPublisherCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of access loggers that have been registered with the server.  It
  // will initially be empty.
  static CopyOnWriteArrayList<AccessLogPublisher<?>> accessPublishers =
      new CopyOnWriteArrayList<AccessLogPublisher<?>>();

  // The singleton instance of this class for configuration purposes.
  static final AccessLogger instance = new AccessLogger();



  /**
   * Retrieve the singleton instance of this class.
   *
   * @return The singleton instance of this logger.
   */
  public static AccessLogger getInstance()
  {
    return instance;
  }

  /**
   * Add an access log publisher to the access logger.
   *
   * @param publisher The access log publisher to add.
   */
  public synchronized static void addAccessLogPublisher(
      AccessLogPublisher<?> publisher)
  {
    accessPublishers.add(publisher);
  }

  /**
   * Remove an access log publisher from the access logger.
   *
   * @param publisher The access log publisher to remove.
   * @return The publisher that was removed or null if it was not found.
   */
  public synchronized static boolean removeAccessLogPublisher(
      AccessLogPublisher<?> publisher)
  {
    boolean removed = accessPublishers.remove(publisher);

    if(removed)
    {
      publisher.close();
    }

    return removed;
  }

  /**
   * Removes all existing access log publishers from the logger.
   */
  public synchronized static void removeAllAccessLogPublishers()
  {
    for(AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.close();
    }

    accessPublishers.clear();
  }

  /**
   * Initializes all the access log publishers.
   *
   * @param configs The access log publisher configurations.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeAccessLogger(List<AccessLogPublisherCfg> configs)
      throws ConfigException, InitializationException
  {
    for(AccessLogPublisherCfg config : configs)
    {
      config.addAccessChangeListener(this);

      if(config.isEnabled())
      {
        AccessLogPublisher<?> AccessLogPublisher = getAccessPublisher(config);

        addAccessLogPublisher(AccessLogPublisher);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      AccessLogPublisherCfg config,
      List<Message> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      AccessLogPublisherCfg config,
      List<Message> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(AccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    config.addAccessChangeListener(this);

    if(config.isEnabled())
    {
      try
      {
        AccessLogPublisher<?> AccessLogPublisher = getAccessPublisher(config);

        addAccessLogPublisher(AccessLogPublisher);
      }
      catch(ConfigException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        messages.add(ERR_CONFIG_LOGGER_CANNOT_CREATE_LOGGER.get(
            String.valueOf(config.dn().toString()),
            stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      AccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    DN dn = config.dn();

    AccessLogPublisher<?> accessLogPublisher = null;
    for(AccessLogPublisher<?> publisher : accessPublishers)
    {
      if(publisher.getDN().equals(dn))
      {
        accessLogPublisher = publisher;
        break;
      }
    }

    if(accessLogPublisher == null)
    {
      if(config.isEnabled())
      {
        // Needs to be added and enabled.
        return applyConfigurationAdd(config);
      }
    }
    else
    {
      if(config.isEnabled())
      {
        // The publisher is currently active, so we don't need to do anything.
        // Changes to the class name cannot be
        // applied dynamically, so if the class name did change then
        // indicate that administrative action is required for that
        // change to take effect.
        String className = config.getJavaClass();
        if(!className.equals(accessLogPublisher.getClass().getName()))
        {
          adminActionRequired = true;
        }
      }
      else
      {
        // The publisher is being disabled so shut down and remove.
        removeAccessLogPublisher(accessLogPublisher);
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      AccessLogPublisherCfg config,
      List<Message> unacceptableReasons)
  {
    DN dn = config.dn();

    AccessLogPublisher<?> accessLogPublisher = null;
    for(AccessLogPublisher<?> publisher : accessPublishers)
    {
      if(publisher.getDN().equals(dn))
      {
        accessLogPublisher = publisher;
        break;
      }
    }

    return accessLogPublisher != null;

  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      AccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    AccessLogPublisher<?> accessLogPublisher = null;
    for(AccessLogPublisher<?> publisher : accessPublishers)
    {
      if(publisher.getDN().equals(config.dn()))
      {
        accessLogPublisher = publisher;
        break;
      }
    }

    if(accessLogPublisher != null)
    {
      removeAccessLogPublisher(accessLogPublisher);
    }
    else
    {
      resultCode = ResultCode.NO_SUCH_OBJECT;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  private boolean isJavaClassAcceptable(AccessLogPublisherCfg config,
                                        List<Message> unacceptableReasons)
  {
    String className = config.getJavaClass();
    AccessLogPublisherCfgDefn d = AccessLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaClassPropertyDefinition();
    // Load the class and cast it to a DebugLogPublisher.
    AccessLogPublisher<?> publisher = null;
    Class<? extends AccessLogPublisher> theClass;
    try {
      theClass = pd.loadClass(className, AccessLogPublisher.class);
      publisher = theClass.newInstance();
    } catch (Exception e) {
      Message message = ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS.get(
          className,
          config.dn().toString(),
          String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // Check that the implementation class implements the correct interface.
    try {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("isConfigurationAcceptable",
          AccessLogPublisherCfg.class,
          List.class);
      Boolean acceptable = (Boolean) method.invoke(publisher, config,
          unacceptableReasons);

      if (! acceptable)
      {
        return false;
      }
    } catch (Exception e) {
      Message message = ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS.get(
          className,
          config.dn().toString(),
          String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
    // The class is valid as far as we can tell.
    return true;
  }

  private AccessLogPublisher<?> getAccessPublisher(AccessLogPublisherCfg config)
      throws ConfigException {
    String className = config.getJavaClass();
    AccessLogPublisherCfgDefn d = AccessLogPublisherCfgDefn.getInstance();
    ClassPropertyDefinition pd =
        d.getJavaClassPropertyDefinition();
    // Load the class and cast it to a AccessLogPublisher.
    Class<? extends AccessLogPublisher> theClass;
    AccessLogPublisher<?> AccessLogPublisher;
    try {
      theClass = pd.loadClass(className, AccessLogPublisher.class);
      AccessLogPublisher = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method = theClass.getMethod("initializeAccessLogPublisher", config
          .configurationClass());
      method.invoke(AccessLogPublisher, config);
    }
    catch (InvocationTargetException ite)
    {
      // Rethrow the exceptions thrown be the invoked method.
      Throwable e = ite.getTargetException();
      Message message = ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS.get(
          className, config.dn().toString(), stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
    catch (Exception e)
    {
      Message message = ERR_CONFIG_LOGGER_INVALID_ACCESS_LOGGER_CLASS.get(
          className, config.dn().toString(), String.valueOf(e));
      throw new ConfigException(message, e);
    }

    // The access publisher has been successfully initialized.
    return AccessLogPublisher;
  }




  /**
   * Writes a message to the access logger with information about a new client
   * connection that has been established, regardless of whether it will be
   * immediately terminated.
   *
   * @param  clientConnection  The client connection that has been established.
   */
  public static void logConnect(ClientConnection clientConnection)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logConnect(clientConnection);
    }
  }



  /**
   * Writes a message to the access logger with information about the
   * termination of an existing client connection.
   *
   * @param  clientConnection  The client connection that has been terminated.
   * @param  disconnectReason  A generic disconnect reason for the connection
   *                           termination.
   * @param  message           A human-readable message that can provide
   *                           additional information about the disconnect.
   */
  public static void logDisconnect(ClientConnection clientConnection,
                                   DisconnectReason disconnectReason,
                                   Message message)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logDisconnect(clientConnection, disconnectReason, message);
    }
  }


  /**
   * Writes a message to the access logger with information about the abandon
   * request associated with the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon request.
   */
  public static void logAbandonRequest(AbandonOperation abandonOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAbandonRequest(abandonOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided abandon operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use
   *          to log the abandon request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logAbandonIntermediateMessage(
      AbandonOperation abandonOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAbandonIntermediateMessage(abandonOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the result of
   * the provided abandon operation.
   *
   * @param  abandonOperation  The abandon operation containing the information
   *                           to use to log the abandon result.
   */
  public static void logAbandonResult(AbandonOperation abandonOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAbandonResult(abandonOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the add
   * request associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add request.
   */
  public static void logAddRequest(AddOperation addOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAddRequest(addOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided add operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param addOperation
   *          The add operation containing the information to use
   *          to log the add request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logAddIntermediateMessage(
      AddOperation addOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAddIntermediateMessage(addOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the add
   * response associated with the provided add operation.
   *
   * @param  addOperation  The add operation containing the information to use
   *                       to log the add response.
   */
  public static void logAddResponse(AddOperation addOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logAddResponse(addOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the bind
   * request associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind request.
   */
  public static void logBindRequest(BindOperation bindOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logBindRequest(bindOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided bind operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param bindOperation
   *          The bind operation containing the information to use
   *          to log the bind request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logBindIntermediateMessage(
      BindOperation bindOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logBindIntermediateMessage(bindOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the bind
   * response associated with the provided bind operation.
   *
   * @param  bindOperation  The bind operation containing the information to use
   *                        to log the bind response.
   */
  public static void logBindResponse(BindOperation bindOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logBindResponse(bindOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the compare
   * request associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare request.
   */
  public static void logCompareRequest(CompareOperation compareOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logCompareRequest(compareOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided compare operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param compareOperation
   *          The compare operation containing the information to use
   *          to log the compare request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logCompareIntermediateMessage(
      CompareOperation compareOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logCompareIntermediateMessage(compareOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the compare
   * response associated with the provided compare operation.
   *
   * @param  compareOperation  The compare operation containing the information
   *                           to use to log the compare response.
   */
  public static void logCompareResponse(CompareOperation compareOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logCompareResponse(compareOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the delete
   * request associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the information to
   *                          use to log the delete request.
   */
  public static void logDeleteRequest(DeleteOperation deleteOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logDeleteRequest(deleteOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided delete operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param deleteOperation
   *          The delete operation containing the information to use
   *          to log the delete request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logDeleteIntermediateMessage(
      DeleteOperation deleteOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logDeleteIntermediateMessage(deleteOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the delete
   * response associated with the provided delete operation.
   *
   * @param  deleteOperation  The delete operation containing the information to
   *                           use to log the delete response.
   */
  public static void logDeleteResponse(DeleteOperation deleteOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logDeleteResponse(deleteOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the extended
   * request associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended request.
   */
  public static void logExtendedRequest(ExtendedOperation extendedOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logExtendedRequest(extendedOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided extended operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param extendedOperation
   *          The extended operation containing the information to use
   *          to log the extended request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logExtendedIntermediateMessage(
      ExtendedOperation extendedOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logExtendedIntermediateMessage(extendedOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the extended
   * response associated with the provided extended operation.
   *
   * @param  extendedOperation  The extended operation containing the
   *                            information to use to log the extended response.
   */
  public static void logExtendedResponse(ExtendedOperation extendedOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logExtendedResponse(extendedOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify
   * request associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the information to
   *                          use to log the modify request.
   */
  public static void logModifyRequest(ModifyOperation modifyOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyRequest(modifyOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided modify operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use
   *          to log the modify request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logModifyIntermediateMessage(
      ModifyOperation modifyOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyIntermediateMessage(modifyOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify
   * response associated with the provided modify operation.
   *
   * @param  modifyOperation  The modify operation containing the information to
   *                          use to log the modify response.
   */
  public static void logModifyResponse(ModifyOperation modifyOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyResponse(modifyOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * request associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN request.
   */
  public static void logModifyDNRequest(ModifyDNOperation modifyDNOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyDNRequest(modifyDNOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided modify DN operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the information to use
   *          to log the modify DN request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logModifyDNIntermediateMessage(
      ModifyDNOperation modifyDNOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyDNIntermediateMessage(modifyDNOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * response associated with the provided modify DN operation.
   *
   * @param  modifyDNOperation  The modify DN operation containing the
   *                            information to use to log the modify DN
   *                            response.
   */
  public static void logModifyDNResponse(ModifyDNOperation modifyDNOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logModifyDNResponse(modifyDNOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the search
   * request associated with the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information to
   *                          use to log the search request.
   */
  public static void logSearchRequest(SearchOperation searchOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logSearchRequest(searchOperation);
    }
  }



  /**
   * Writes a message to the access logger containing additional
   * information associated with the provided search operation.
   * <p>
   * This method will only be called after the request has been logged
   * and before the response. Implementations can choose to ignore
   * intermediate responses or filter them based on their category.
   *
   * @param searchOperation
   *          The search operation containing the information to use
   *          to log the search request.
   * @param category
   *          The category of the intermediate message.
   * @param content
   *          The content of the intermediate message. This comprises
   *          of one or more key/value pairs which form the content of
   *          the intermediate message.
   */
  public static void logSearchIntermediateMessage(
      SearchOperation searchOperation, String category,
      Map<String, String> content)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logSearchIntermediateMessage(searchOperation,
          category, content);
    }
  }



  /**
   * Writes a message to the access logger with information about the search
   * result entry that matches the criteria associated with the provided search
   * operation.
   *
   * @param  searchOperation  The search operation with which the search result
   *                          entry is associated.
   * @param  searchEntry      The search result entry to be logged.
   */
  public static void logSearchResultEntry(SearchOperation searchOperation,
                                          SearchResultEntry searchEntry)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logSearchResultEntry(searchOperation, searchEntry);
    }
  }



  /**
   * Writes a message to the access logger with information about the search
   * result reference returned while processing the associated search operation.
   *
   * @param  searchOperation  The search operation with which the search result
   *                          reference is associated.
   * @param  searchReference  The search result reference to be logged.
   */
  public static void logSearchResultReference(SearchOperation searchOperation,
                          SearchResultReference searchReference)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logSearchResultReference(searchOperation, searchReference);
    }
  }



  /**
   * Writes a message to the access logger with information about the completion
   * of the provided search operation.
   *
   * @param  searchOperation  The search operation containing the information
   *                          to use to log the search result done message.
   */
  public static void logSearchResultDone(SearchOperation searchOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logSearchResultDone(searchOperation);
    }
  }



  /**
   * Writes a message to the access logger with information about the unbind
   * request associated with the provided unbind operation.
   *
   * @param  unbindOperation  The unbind operation containing the information to
   *                          use to log the unbind request.
   */
  public static void logUnbind(UnbindOperation unbindOperation)
  {
    for (AccessLogPublisher<?> publisher : accessPublishers)
    {
      publisher.logUnbind(unbindOperation);
    }
  }
}

