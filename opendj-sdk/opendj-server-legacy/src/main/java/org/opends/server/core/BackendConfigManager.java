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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.BackendCfgDefn;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigConstants;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.WritabilityMode;

/**
 * This class defines a utility that will be used to manage the configuration
 * for the set of backends defined in the Directory Server.  It will perform
 * the necessary initialization of those backends when the server is first
 * started, and then will manage any changes to them while the server is
 * running.
 */
public class BackendConfigManager implements
     ConfigurationChangeListener<BackendCfg>,
     ConfigurationAddListener<BackendCfg>,
     ConfigurationDeleteListener<BackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The mapping between configuration entry DNs and their corresponding backend implementations. */
  private final ConcurrentHashMap<DN, Backend<? extends BackendCfg>> registeredBackends = new ConcurrentHashMap<>();
  private final ServerContext serverContext;

  /**
   * Creates a new instance of this backend config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public BackendConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initializes the configuration associated with the Directory Server
   * backends. This should only be called at Directory Server startup.
   *
   * @throws ConfigException
   *           If a critical configuration problem prevents the backend
   *           initialization from succeeding.
   * @throws InitializationException
   *           If a problem occurs while initializing the backends that is not
   *           related to the server configuration.
   */
  public void initializeBackendConfig()
         throws ConfigException, InitializationException
  {
    // Create an internal server management context and retrieve
    // the root configuration.
    ServerManagementContext context = ServerManagementContext.getInstance();
    RootCfg root = context.getRootConfiguration();

    // Register add and delete listeners.
    root.addBackendAddListener(this);
    root.addBackendDeleteListener(this);

    // Get the configuration entry that is at the root of all the backends in
    // the server.
    ConfigEntry backendRoot;
    try
    {
      DN configEntryDN = DN.valueOf(ConfigConstants.DN_BACKEND_BASE);
      backendRoot   = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE.get(getExceptionMessage(e));
      throw new ConfigException(message, e);

    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no backends defined below it.
    if (backendRoot == null)
    {
      LocalizableMessage message = ERR_CONFIG_BACKEND_BASE_DOES_NOT_EXIST.get();
      throw new ConfigException(message);
    }


    // Initialize existing backends.
    for (String name : root.listBackends())
    {
      // Get the handler's configuration.
      // This will decode and validate its properties.
      BackendCfg backendCfg = root.getBackend(name);

      DN backendDN = backendCfg.dn();
      String backendID = backendCfg.getBackendId();

      // Register as a change listener for this backend so that we can be
      // notified when it is disabled or enabled.
      backendCfg.addChangeListener(this);

      // Ignore this handler if it is disabled.
      if (backendCfg.isEnabled())
      {
        // If there is already a backend registered with the specified ID,
        // then log an error and skip it.
        if (DirectoryServer.hasBackend(backendCfg.getBackendId()))
        {
          logger.warn(WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID, backendID, backendDN);
          continue;
        }

        // See if the entry contains an attribute that specifies the class name
        // for the backend implementation.  If it does, then load it and make
        // sure that it's a valid backend implementation.  There is no such
        // attribute, the specified class cannot be loaded, or it does not
        // contain a valid backend implementation, then log an error and skip it.
        String className = backendCfg.getJavaClass();

        Backend<? extends BackendCfg> backend;
        try
        {
          backend = loadBackendClass(className).newInstance();
        }
        catch (Exception e)
        {
          logger.traceException(e);
          logger.error(ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE, className, backendDN, stackTraceToSingleLineString(e));
          continue;
        }


        // If this backend is a configuration manager, then we don't want to do
        // any more with it because the configuration will have already been
        // started.
        if (backend instanceof ConfigHandler)
        {
          continue;
        }

        WritabilityMode writabilityMode = toWritabilityMode(backendCfg.getWritabilityMode());

        // Set the backend ID and writability mode for this backend.
        backend.setBackendID(backendID);
        backend.setWritabilityMode(writabilityMode);


        // Acquire a shared lock on this backend.  This will prevent operations
        // like LDIF import or restore from occurring while the backend is
        // active.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
          {
            logger.error(ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK, backendID, failureReason);
            // FIXME -- Do we need to send an admin alert?
            continue;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
          logger.error(ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK, backendID, stackTraceToSingleLineString(e));
          // FIXME -- Do we need to send an admin alert?
          continue;
        }


        // Perform the necessary initialization for the backend entry.
        try
        {
          initializeBackend(backend, backendCfg);
        }
        catch (Exception e)
        {
          logger.traceException(e);
          logger.error(ERR_CONFIG_BACKEND_CANNOT_INITIALIZE, className, backendDN, stackTraceToSingleLineString(e));

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(backend);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backendID, failureReason);
              // FIXME -- Do we need to send an admin alert?
            }
          }
          catch (Exception e2)
          {
            logger.traceException(e2);

            logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backendID, stackTraceToSingleLineString(e2));
            // FIXME -- Do we need to send an admin alert?
          }

          continue;
        }


        for (BackendInitializationListener listener : getBackendInitializationListeners())
        {
          listener.performBackendInitializationProcessing(backend);
        }


        // Register the backend with the server.
        try
        {
          DirectoryServer.registerBackend(backend);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          logger.warn(WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND, backendID, getExceptionMessage(e));
          // FIXME -- Do we need to send an admin alert?
        }


        // Put this backend in the hash so that we will be able to find it if it
        // is altered.
        registeredBackends.put(backendDN, backend);

      }
      else
      {
        // The backend is explicitly disabled.  Log a mild warning and continue.
        logger.debug(INFO_CONFIG_BACKEND_DISABLED, backendDN);
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
       BackendCfg configEntry,
       List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    Set<DN> baseDNs = configEntry.getBaseDN();

    // See if the backend is registered with the server.  If it is, then
    // see what's changed and whether those changes are acceptable.
    Backend<?> backend = registeredBackends.get(backendDN);
    if (backend != null)
    {
      LinkedHashSet<DN> removedDNs = newLinkedHashSet(backend.getBaseDNs());
      LinkedHashSet<DN> addedDNs = new LinkedHashSet<>(baseDNs);
      Iterator<DN> iterator = removedDNs.iterator();
      while (iterator.hasNext())
      {
        DN dn = iterator.next();
        if (addedDNs.remove(dn))
        {
          iterator.remove();
        }
      }

      // Copy the directory server's base DN registry and make the
      // requested changes to see if it complains.
      BaseDnRegistry reg = DirectoryServer.copyBaseDnRegistry();
      for (DN dn : removedDNs)
      {
        try
        {
          reg.deregisterBaseDN(dn);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          unacceptableReason.add(de.getMessageObject());
          return false;
        }
      }

      for (DN dn : addedDNs)
      {
        try
        {
          reg.registerBaseDN(dn, backend, false);
        }
        catch (DirectoryException de)
        {
          logger.traceException(de);

          unacceptableReason.add(de.getMessageObject());
          return false;
        }
      }
    }
    else if (configEntry.isEnabled())
    {
      /*
       * If the backend was not enabled, it has not been registered with directory server, so
       * no listeners will be registered at the lower layers. Verify as it was an add.
       */
      String className = configEntry.getJavaClass();
      try
      {
        Class<Backend<BackendCfg>> backendClass = loadBackendClass(className);
        if (! Backend.class.isAssignableFrom(backendClass))
        {
          unacceptableReason.add(ERR_CONFIG_BACKEND_CLASS_NOT_BACKEND.get(className, backendDN));
          return false;
        }

        Backend<BackendCfg> b = backendClass.newInstance();
        if (! b.isConfigurationAcceptable(configEntry, unacceptableReason, serverContext))
        {
          return false;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        unacceptableReason.add(
            ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(className, backendDN, stackTraceToSingleLineString(e)));
        return false;
      }
    }

    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // backend, then the backend itself will need to make that determination.
    return true;
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(BackendCfg cfg)
  {
    DN backendDN = cfg.dn();
    Backend<? extends BackendCfg> backend = registeredBackends.get(backendDN);
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.
    boolean needToEnable = false;
    try
    {
      if (cfg.isEnabled())
      {
        // The backend is marked as enabled.  See if that is already true.
        if (backend == null)
        {
          needToEnable = true;
        } // else already enabled, no need to do anything.
      }
      else
      {
        // The backend is marked as disabled.  See if that is already true.
        if (backend != null)
        {
          // It isn't disabled, so we will do so now and deregister it from the
          // Directory Server.
          registeredBackends.remove(backendDN);
          DirectoryServer.deregisterBackend(backend);

          for (BackendInitializationListener listener : getBackendInitializationListeners())
          {
            listener.performBackendFinalizationProcessing(backend);
          }

          backend.finalizeBackend();

          // Remove the shared lock for this backend.
          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(backend);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend.getBackendID(), failureReason);
              // FIXME -- Do we need to send an admin alert?
            }
          }
          catch (Exception e2)
          {
            logger.traceException(e2);
            logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend
                .getBackendID(), stackTraceToSingleLineString(e2));
            // FIXME -- Do we need to send an admin alert?
          }

          return ccr;
        } // else already disabled, no need to do anything.
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE.get(backendDN,
          stackTraceToSingleLineString(e)));
      return ccr;
    }


    String backendID = cfg.getBackendId();
    WritabilityMode writabilityMode = toWritabilityMode(cfg.getWritabilityMode());

    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = cfg.getJavaClass();


    // See if this backend is currently active and if so if the name of the
    // class is the same.
    if (backend != null && !className.equals(backend.getClass().getName()))
    {
      // It is not the same.  Try to load it and see if it is a valid backend
      // implementation.
      try
      {
        Class<?> backendClass = DirectoryServer.loadClass(className);
        if (Backend.class.isAssignableFrom(backendClass))
        {
          // It appears to be a valid backend class.  We'll return that the
          // change is successful, but indicate that some administrative
          // action is required.
          ccr.addMessage(NOTE_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS.get(
              backendDN, backend.getClass().getName(), className));
          ccr.setAdminActionRequired(true);
        }
        else
        {
          // It is not a valid backend class.  This is an error.
          ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          ccr.addMessage(ERR_CONFIG_BACKEND_CLASS_NOT_BACKEND.get(className, backendDN));
        }
        return ccr;
      }
      catch (Exception e)
      {
        logger.traceException(e);

        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(
                className, backendDN, stackTraceToSingleLineString(e)));
        return ccr;
      }
    }


    // If we've gotten here, then that should mean that we need to enable the
    // backend.  Try to do so.
    if (needToEnable)
    {
      try
      {
        backend = loadBackendClass(className).newInstance();
      }
      catch (Exception e)
      {
        // It is not a valid backend class.  This is an error.
        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.addMessage(ERR_CONFIG_BACKEND_CLASS_NOT_BACKEND.get(className, backendDN));
        return ccr;
      }


      // Set the backend ID and writability mode for this backend.
      backend.setBackendID(backendID);
      backend.setWritabilityMode(writabilityMode);


      // Acquire a shared lock on this backend.  This will prevent operations
      // like LDIF import or restore from occurring while the backend is active.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
        {
          LocalizableMessage message = ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK.get(backendID,failureReason);
          logger.error(message);
          // FIXME -- Do we need to send an admin alert?

          ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
          ccr.setAdminActionRequired(true);
          ccr.addMessage(message);
          return ccr;
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK.get(backendID,
            stackTraceToSingleLineString(e));
        logger.error(message);
        // FIXME -- Do we need to send an admin alert?

        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.setAdminActionRequired(true);
        ccr.addMessage(message);
        return ccr;
      }

      if (!initializeBackend(backend, cfg, ccr))
      {
        return ccr;
      }

      for (BackendInitializationListener listener : getBackendInitializationListeners())
      {
        listener.performBackendInitializationProcessing(backend);
      }


      // Register the backend with the server.
      try
      {
        DirectoryServer.registerBackend(backend);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(
                backendID, getExceptionMessage(e));
        logger.warn(message);

        // FIXME -- Do we need to send an admin alert?
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(message);
        return ccr;
      }


      registeredBackends.put(backendDN, backend);
    }
    else if (ccr.getResultCode() == ResultCode.SUCCESS && backend != null)
    {
      backend.setWritabilityMode(writabilityMode);
    }

    return ccr;
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAddAcceptable(
       BackendCfg configEntry,
       List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID = configEntry.getBackendId();
    if (DirectoryServer.hasBackend(backendID))
    {
      unacceptableReason.add(WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID.get(backendDN, backendID));
      return false;
    }


    // See if the entry contains an attribute that specifies the set of base DNs
    // for the backend.  If it does not, then skip it.
    Set<DN> baseList = configEntry.getBaseDN();
    DN[] baseDNs = new DN[baseList.size()];
    baseList.toArray(baseDNs);


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = configEntry.getJavaClass();

    Backend<BackendCfg> backend;
    try
    {
      backend = loadBackendClass(className).newInstance();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      unacceptableReason.add(ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(
              className, backendDN, stackTraceToSingleLineString(e)));
      return false;
    }


    // Make sure that all of the base DNs are acceptable for use in the server.
    BaseDnRegistry reg = DirectoryServer.copyBaseDnRegistry();
    for (DN baseDN : baseDNs)
    {
      try
      {
        reg.registerBaseDN(baseDN, backend, false);
      }
      catch (DirectoryException de)
      {
        unacceptableReason.add(de.getMessageObject());
        return false;
      }
      catch (Exception e)
      {
        unacceptableReason.add(getExceptionMessage(e));
        return false;
      }
    }

    return backend.isConfigurationAcceptable(configEntry, unacceptableReason, serverContext);
  }



  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationAdd(BackendCfg cfg)
  {
    DN                backendDN           = cfg.dn();
    final ConfigChangeResult ccr = new ConfigChangeResult();


    // Register as a change listener for this backend entry so that we will
    // be notified of any changes that may be made to it.
    cfg.addChangeListener(this);


    // See if the entry contains an attribute that indicates whether the backend should be enabled.
    // If it does not, or if it is not set to "true", then skip it.
    if (!cfg.isEnabled())
    {
      // The backend is explicitly disabled.  We will log a message to
      // indicate that it won't be enabled and return.
      LocalizableMessage message = INFO_CONFIG_BACKEND_DISABLED.get(backendDN);
      logger.debug(message);
      ccr.addMessage(message);
      return ccr;
    }



    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID = cfg.getBackendId();
    if (DirectoryServer.hasBackend(backendID))
    {
      LocalizableMessage message = WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID.get(backendDN, backendID);
      logger.warn(message);
      ccr.addMessage(message);
      return ccr;
    }


    WritabilityMode writabilityMode = toWritabilityMode(cfg.getWritabilityMode());

    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = cfg.getJavaClass();

    Backend<? extends BackendCfg> backend;
    try
    {
      backend = loadBackendClass(className).newInstance();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(
          className, backendDN, stackTraceToSingleLineString(e)));
      return ccr;
    }


    // Set the backend ID and writability mode for this backend.
    backend.setBackendID(backendID);
    backend.setWritabilityMode(writabilityMode);


    // Acquire a shared lock on this backend.  This will prevent operations
    // like LDIF import or restore from occurring while the backend is active.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        LocalizableMessage message =
            ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK.get(backendID, failureReason);
        logger.error(message);
        // FIXME -- Do we need to send an admin alert?

        ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        ccr.setAdminActionRequired(true);
        ccr.addMessage(message);
        return ccr;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK.get(
          backendID, stackTraceToSingleLineString(e));
      logger.error(message);
      // FIXME -- Do we need to send an admin alert?

      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      ccr.setAdminActionRequired(true);
      ccr.addMessage(message);
      return ccr;
    }


    // Perform the necessary initialization for the backend entry.
    if (!initializeBackend(backend, cfg, ccr))
    {
      return ccr;
    }

    for (BackendInitializationListener listener : getBackendInitializationListeners())
    {
      listener.performBackendInitializationProcessing(backend);
    }

    // At this point, the backend should be online.  Add it as one of the
    // registered backends for this backend config manager.
    try
    {
      DirectoryServer.registerBackend(backend);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(
              backendID, getExceptionMessage(e));
      logger.error(message);

      // FIXME -- Do we need to send an admin alert?
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(message);
      return ccr;
    }

    registeredBackends.put(backendDN, backend);
    return ccr;
  }

  private boolean initializeBackend(Backend<? extends BackendCfg> backend, BackendCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      initializeBackend(backend, cfg);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_BACKEND_CANNOT_INITIALIZE.get(
          cfg.getJavaClass(), cfg.dn(), stackTraceToSingleLineString(e)));

      String backendID = cfg.getBackendId();
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backendID, failureReason);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e2)
      {
        logger.traceException(e2);

        logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backendID, stackTraceToSingleLineString(e2));
        // FIXME -- Do we need to send an admin alert?
      }

      return false;
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private Class<Backend<BackendCfg>> loadBackendClass(String className) throws Exception
  {
    return (Class<Backend<BackendCfg>>) DirectoryServer.loadClass(className);
  }

  private WritabilityMode toWritabilityMode(BackendCfgDefn.WritabilityMode writabilityMode)
  {
    switch (writabilityMode)
    {
    case DISABLED:
      return WritabilityMode.DISABLED;
    case ENABLED:
      return WritabilityMode.ENABLED;
    case INTERNAL_ONLY:
      return WritabilityMode.INTERNAL_ONLY;
    default:
      return WritabilityMode.ENABLED;
    }
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationDeleteAcceptable(
       BackendCfg configEntry,
       List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.  If we
    // do know about it, then that means that it is enabled and we will not
    // allow removing a backend that is enabled.
    Backend<?> backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return true;
    }


    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend<?>[] subBackends = backend.getSubordinateBackends();
    if (subBackends != null && subBackends.length != 0)
    {
      unacceptableReason.add(NOTE_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES.get(backendDN));
      return false;
    }
    return true;
  }


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationDelete(BackendCfg configEntry)
  {
    DN                backendDN           = configEntry.dn();
    final ConfigChangeResult ccr = new ConfigChangeResult();

    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.
    Backend<?> backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return ccr;
    }

    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend<?>[] subBackends = backend.getSubordinateBackends();
    if (subBackends != null && subBackends.length > 0)
    {
      ccr.setResultCode(UNWILLING_TO_PERFORM);
      ccr.addMessage(NOTE_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES.get(backendDN));
      return ccr;
    }

    registeredBackends.remove(backendDN);

    try
    {
      backend.finalizeBackend();
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    for (BackendInitializationListener listener : getBackendInitializationListeners())
    {
      listener.performBackendFinalizationProcessing(backend);
    }

    DirectoryServer.deregisterBackend(backend);
    configEntry.removeChangeListener(this);

    // Remove the shared lock for this backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend.getBackendID(), failureReason);
        // FIXME -- Do we need to send an admin alert?
      }
    }
    catch (Exception e2)
    {
      logger.traceException(e2);
      logger.warn(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend
          .getBackendID(), stackTraceToSingleLineString(e2));
      // FIXME -- Do we need to send an admin alert?
    }

    return ccr;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void initializeBackend(Backend backend, BackendCfg cfg)
       throws ConfigException, InitializationException
  {
    backend.configureBackend(cfg, serverContext);
    backend.openBackend();
  }
}
