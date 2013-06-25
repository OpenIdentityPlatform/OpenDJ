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
 *      Portions copyright 2011-2013 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opends.messages.Message;
import org.opends.messages.MessageDescriptor.Arg3;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.LogPublisherCfg;
import org.opends.server.api.LogPublisher;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;

/**
 * This class defines the wrapper that will invoke all registered loggers for
 * each type of request received or response sent. If no log publishers are
 * registered, messages will be directed to standard out.
 *
 * @param <P>
 *          The type of the LogPublisher corresponding to this logger
 * @param <C>
 *          The type of the LogPublisherCfg corresponding to this logger
 */
public abstract class AbstractLogger
    <P extends LogPublisher<C>, C extends LogPublisherCfg>
    implements ConfigurationAddListener<C>, ConfigurationDeleteListener<C>,
    ConfigurationChangeListener<C>
{

  /**
   * The storage designed to store log publishers. It is helpful in abstracting
   * away the methods used to manage the collection.
   *
   * @param <P>
   *          The concrete {@link LogPublisher} type
   * @param <C>
   *          The concrete {@link LogPublisherCfg} type
   */
  protected static class LoggerStorage<P extends LogPublisher<C>,
      C extends LogPublisherCfg>
  {
    /**
     * Defined as public to allow subclasses of {@link AbstractLogger} to
     * instantiate it.
     */
    public LoggerStorage()
    {
      super();
    }

    /**
     * The set of loggers that have been registered with the server. It will
     * initially be empty.
     */
    private Collection<P> logPublishers = new CopyOnWriteArrayList<P>();


    /**
     * Add a log publisher to the logger.
     *
     * @param publisher
     *          The log publisher to add.
     */
    public synchronized void addLogPublisher(P publisher)
    {
      logPublishers.add(publisher);
    }

    /**
     * Remove a log publisher from the logger.
     *
     * @param publisher
     *          The log publisher to remove.
     * @return True if the log publisher is removed or false otherwise.
     */
    public synchronized boolean removeLogPublisher(P publisher)
    {
      boolean removed = logPublishers.remove(publisher);

      if (removed)
      {
        publisher.close();
      }

      return removed;
    }

    /**
     * Removes all existing log publishers from the logger.
     */
    public synchronized void removeAllLogPublishers()
    {
      StaticUtils.close((Collection) logPublishers);
      logPublishers.clear();
    }

    /**
     * Returns the logPublishers.
     *
     * @return the collection of {@link LogPublisher}s
     */
    public Collection<P> getLogPublishers()
    {
      return logPublishers;
    }
  }

  /**
   * Returns the logger storage for the current logger.
   *
   * @return the logger storage for the current logger
   */
  protected abstract LoggerStorage<P, C> getStorage();

  /**
   * Returns the java {@link ClassPropertyDefinition} for the current logger.
   *
   * @return the java {@link ClassPropertyDefinition} for the current logger.
   */
  protected abstract ClassPropertyDefinition getJavaClassPropertyDefinition();

  private final Class<P> logPublisherClass;

  private final Arg3<CharSequence, CharSequence, CharSequence>
      invalidLoggerClassErrorMessage;

  /**
   * The constructor for this class.
   *
   * @param logPublisherClass
   *          the log publisher class
   * @param invalidLoggerClassErrorMessage
   *          the error message to use if the logger class in invalid
   */
  public AbstractLogger(
      final Class<P> logPublisherClass,
      final Arg3<CharSequence, CharSequence, CharSequence>
          invalidLoggerClassErrorMessage)
  {
    this.logPublisherClass = logPublisherClass;
    this.invalidLoggerClassErrorMessage = invalidLoggerClassErrorMessage;
  }

  /**
   * Initializes all the log publishers.
   *
   * @param configs The log publisher configurations.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeLogger(List<C> configs)
      throws ConfigException, InitializationException
  {
    for (C config : configs)
    {
      config.addChangeListener((ConfigurationChangeListener) this);

      if(config.isEnabled())
      {
        getStorage().addLogPublisher(getLogPublisher(config));
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAddAcceptable(C config,
      List<Message> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(C config,
      List<Message> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationAdd(C config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    config.addChangeListener((ConfigurationChangeListener) this);

    if(config.isEnabled())
    {
      try
      {
        getStorage().addLogPublisher(getLogPublisher(config));
      }
      catch(ConfigException e)
      {
        debugCaught(DebugLogLevel.ERROR, e);
        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (Exception e)
      {
        debugCaught(DebugLogLevel.ERROR, e);
        messages.add(ERR_CONFIG_LOGGER_CANNOT_CREATE_LOGGER.get(
                String.valueOf(config.dn().toString()),
                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
    }
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  private void debugCaught(LogLevel error, Exception e)
  {
    if (DebugLogger.debugEnabled())
    {
      DebugLogger.getTracer().debugCaught(DebugLogLevel.ERROR, e);
    }
  }

  private P findLogPublisher(DN dn)
  {
    Collection<P> logPublishers = getStorage().getLogPublishers();
    for (P publisher : logPublishers)
    {
      if (publisher.getDN().equals(dn))
      {
        return publisher;
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(C config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    P logPublisher = findLogPublisher(config.dn());
    if(logPublisher == null)
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
        if(!className.equals(logPublisher.getClass().getName()))
        {
          adminActionRequired = true;
        }
      }
      else
      {
        // The publisher is being disabled so shut down and remove.
        getStorage().removeLogPublisher(logPublisher);
      }
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationDeleteAcceptable(C config,
      List<Message> unacceptableReasons)
  {
    return findLogPublisher(config.dn()) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationDelete(C config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;

    P logPublisher = findLogPublisher(config.dn());
    if(logPublisher != null)
    {
      getStorage().removeLogPublisher(logPublisher);
    }
    else
    {
      resultCode = ResultCode.NO_SUCH_OBJECT;
    }

    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  private boolean isJavaClassAcceptable(C config,
                                        List<Message> unacceptableReasons)
  {
    String className = config.getJavaClass();
    ClassPropertyDefinition pd = getJavaClassPropertyDefinition();
    try {
      // Load the class and cast it to a LogPublisher.
      P publisher = pd.loadClass(className, logPublisherClass).newInstance();
      // The class is valid as far as we can tell.
      return publisher.isConfigurationAcceptable(config, unacceptableReasons);
    } catch (Exception e) {
      Message message =
          invalidLoggerClassErrorMessage.get(className, config.dn().toString(),
              String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }
  }

  private P getLogPublisher(C config) throws ConfigException
  {
    String className = config.getJavaClass();
    ClassPropertyDefinition pd = getJavaClassPropertyDefinition();
    try {
      // Load the class and cast it to a LogPublisher.
      P logPublisher = pd.loadClass(className, logPublisherClass).newInstance();
      logPublisher.initializeLogPublisher(config);
      // The log publisher has been successfully initialized.
      return logPublisher;
    }
    catch (Exception e)
    {
      Message message =
          invalidLoggerClassErrorMessage.get(className, config.dn().toString(),
              String.valueOf(e));
      throw new ConfigException(message, e);
    }
  }

}
