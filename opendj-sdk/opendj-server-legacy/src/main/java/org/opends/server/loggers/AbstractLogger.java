/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg3;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.LogPublisherCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
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
    private Collection<P> logPublishers = new CopyOnWriteArrayList<>();


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
      StaticUtils.close(logPublishers);
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
   * Returns the log publishers.
   *
   * @return the collection of {@link LogPublisher}s
   */
  protected abstract Collection<P> getLogPublishers();

  /**
   * Add a log publisher to the logger.
   *
   * @param publisher
   *          The log publisher to add.
   */
  public abstract void addLogPublisher(P publisher);

  /**
   * Remove a log publisher from the logger.
   *
   * @param publisher
   *          The log publisher to remove.
   * @return True if the log publisher is removed or false otherwise.
   */
  public abstract boolean removeLogPublisher(P publisher);

  /**
   * Removes all existing log publishers from the logger.
   */
  public abstract void removeAllLogPublishers();

  /**
   * Returns the java {@link ClassPropertyDefinition} for the current logger.
   *
   * @return the java {@link ClassPropertyDefinition} for the current logger.
   */
  protected abstract ClassPropertyDefinition getJavaClassPropertyDefinition();

  private final Class<P> logPublisherClass;

  private final Arg3<Object, Object, Object>
      invalidLoggerClassErrorMessage;

  ServerContext serverContext;

  /**
   * The constructor for this class.
   *
   * @param logPublisherClass
   *          the log publisher class
   * @param invalidLoggerClassErrorMessage
   *          the error message to use if the logger class in invalid
   */
  AbstractLogger(
      final Class<P> logPublisherClass,
      final Arg3<Object, Object, Object>
          invalidLoggerClassErrorMessage)
  {
    this.logPublisherClass = logPublisherClass;
    this.invalidLoggerClassErrorMessage = invalidLoggerClassErrorMessage;
  }

  /**
   * Initializes all the log publishers.
   *
   * @param configs The log publisher configurations.
   * @param serverContext
   *            The server context.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public void initializeLogger(List<C> configs, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    for (C config : configs)
    {
      config.addChangeListener((ConfigurationChangeListener) this);

      if(config.isEnabled())
      {
        addLogPublisher(getLogPublisher(config));
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(C config,
      List<LocalizableMessage> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(C config,
      List<LocalizableMessage> unacceptableReasons)
  {
    return !config.isEnabled() ||
        isJavaClassAcceptable(config, unacceptableReasons);
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(C config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    config.addChangeListener((ConfigurationChangeListener) this);

    if(config.isEnabled())
    {
      try
      {
        addLogPublisher(getLogPublisher(config));
      }
      catch(ConfigException e)
      {
        LocalizedLogger.getLoggerForThisClass().traceException(e);
        ccr.addMessage(e.getMessageObject());
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
      catch (Exception e)
      {
        LocalizedLogger.getLoggerForThisClass().traceException(e);
        ccr.addMessage(ERR_CONFIG_LOGGER_CANNOT_CREATE_LOGGER.get(
            config.dn(), stackTraceToSingleLineString(e)));
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      }
    }
    return ccr;
  }

  private P findLogPublisher(DN dn)
  {
    for (P publisher : getLogPublishers())
    {
      if (publisher.getDN().equals(dn))
      {
        return publisher;
      }
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(C config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

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
          ccr.setAdminActionRequired(true);
        }
      }
      else
      {
        // The publisher is being disabled so shut down and remove.
        removeLogPublisher(logPublisher);
      }
    }

    return ccr;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(C config,
      List<LocalizableMessage> unacceptableReasons)
  {
    return findLogPublisher(config.dn()) != null;
  }

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(C config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    P logPublisher = findLogPublisher(config.dn());
    if(logPublisher != null)
    {
      removeLogPublisher(logPublisher);
    }
    else
    {
      ccr.setResultCode(ResultCode.NO_SUCH_OBJECT);
    }

    return ccr;
  }

  private boolean isJavaClassAcceptable(C config,
                                        List<LocalizableMessage> unacceptableReasons)
  {
    String className = config.getJavaClass();
    ClassPropertyDefinition pd = getJavaClassPropertyDefinition();
    try {
      P publisher = pd.loadClass(className, logPublisherClass).newInstance();
      return publisher.isConfigurationAcceptable(config, unacceptableReasons);
    } catch (Exception e) {
      unacceptableReasons.add(invalidLoggerClassErrorMessage.get(className, config.dn(), e));
      return false;
    }
  }

  private P getLogPublisher(C config) throws ConfigException
  {
    String className = config.getJavaClass();
    ClassPropertyDefinition pd = getJavaClassPropertyDefinition();
    try {
      P logPublisher = pd.loadClass(className, logPublisherClass).newInstance();
      logPublisher.initializeLogPublisher(config, serverContext);
      return logPublisher;
    }
    catch (Exception e)
    {
      throw new ConfigException(
          invalidLoggerClassErrorMessage.get(className, config.dn(), e), e);
    }
  }

}
