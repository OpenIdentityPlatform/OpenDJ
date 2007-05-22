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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigConstants;

import org.opends.server.types.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.util.StaticUtils.*;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.meta.BackendCfgDefn;


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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The mapping between configuration entry DNs and their corresponding
  // backend implementations.
  private ConcurrentHashMap<DN,Backend> registeredBackends;



  /**
   * Creates a new instance of this backend config manager.
   */
  public BackendConfigManager()
  {
    // No implementation is required.
  }



  /**
   * Initializes the configuration associated with the Directory Server
   * backends.  This should only be called at Directory Server startup.
   *
   * @throws  ConfigException  If a critical configuration problem prevents the
   *                           backend initialization from succeeding.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the backends that is not related to the
   *                                   server configuration.
   */
  public void initializeBackendConfig()
         throws ConfigException, InitializationException
  {
    registeredBackends = new ConcurrentHashMap<DN,Backend>();


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
      DN configEntryDN = DN.decode(ConfigConstants.DN_BACKEND_BASE);
      backendRoot   = DirectoryServer.getConfigEntry(configEntryDN);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_CONFIG_BACKEND_CANNOT_GET_CONFIG_BASE;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new ConfigException(msgID, message, e);

    }


    // If the configuration root entry is null, then assume it doesn't exist.
    // In that case, then fail.  At least that entry must exist in the
    // configuration, even if there are no backends defined below it.
    if (backendRoot == null)
    {
      int    msgID   = MSGID_CONFIG_BACKEND_BASE_DOES_NOT_EXIST;
      String message = getMessage(msgID);
      throw new ConfigException(msgID, message);
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
      if (backendCfg.isBackendEnabled())
      {
        // If there is already a backend registered with the specified ID,
        // then log an error and skip it.
        if (DirectoryServer.hasBackend(backendCfg.getBackendId()))
        {
          int msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(backendDN));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          continue;
        }

        // See if the entry contains an attribute that specifies the class name
        // for the backend implementation.  If it does, then load it and make
        // sure that it's a valid backend implementation.  There is no such
        // attribute, the specified class cannot be loaded, or it does not
        // contain a valid backend implementation, then log an error and skip
        // it.
        String className = backendCfg.getBackendClass();
        Class backendClass;

        Backend backend;
        try
        {
          backendClass = DirectoryServer.loadClass(className);
          backend = (Backend) backendClass.newInstance();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
          String message = getMessage(msgID, String.valueOf(className),
                                      String.valueOf(backendDN),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          continue;
        }


        // If this backend is a configuration manager, then we don't want to do
        // any more with it because the configuration will have already been
        // started.
        if (backend instanceof ConfigHandler)
        {
          continue;
        }


        // See if the entry contains an attribute that specifies the writability
        // mode.
        WritabilityMode writabilityMode = WritabilityMode.ENABLED;
        BackendCfgDefn.BackendWritabilityMode bwm =
             backendCfg.getBackendWritabilityMode();
        switch (bwm)
        {
          case DISABLED:
            writabilityMode = WritabilityMode.DISABLED;
            break;
          case ENABLED:
            writabilityMode = WritabilityMode.ENABLED;
            break;
          case INTERNAL_ONLY:
            writabilityMode = WritabilityMode.INTERNAL_ONLY;
            break;
        }

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
            int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
            String message = getMessage(msgID, backendID,
                                        String.valueOf(failureReason));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
            continue;
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
          String message = getMessage(msgID, String.valueOf(className),
                                      String.valueOf(backendDN),
                                      stackTraceToSingleLineString(e));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);

          try
          {
            String lockFile = LockFileManager.getBackendLockFileName(backend);
            StringBuilder failureReason = new StringBuilder();
            if (! LockFileManager.releaseLock(lockFile, failureReason))
            {
              msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
              message = getMessage(msgID, backendID,
                                   String.valueOf(failureReason));
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              // FIXME -- Do we need to send an admin alert?
            }
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }

            msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            message = getMessage(msgID, backendID,
                                 stackTraceToSingleLineString(e2));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }

          continue;
        }


        // Notify any backend initialization listeners.
        for (BackendInitializationListener listener :
             DirectoryServer.getBackendInitializationListeners())
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
          String message = getMessage(msgID, backendID,
                                      getExceptionMessage(e));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }


        // Put this backend in the hash so that we will be able to find it if it
        // is altered.
        registeredBackends.put(backendDN, backend);

      }
      else
      {
        // The backend is explicitly disabled.  Log a mild warning and
        // continue.
        int msgID = MSGID_CONFIG_BACKEND_DISABLED;
        String message = getMessage(msgID, String.valueOf(backendDN));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.INFORMATIONAL, message, msgID);
      }
    }
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
       BackendCfg configEntry,
       List<String> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    Set<DN> baseDNs = configEntry.getBackendBaseDN();

    // See if the backend is registered with the server.  If it is, then
    // see what's changed and whether those changes are acceptable.
    Backend backend = registeredBackends.get(backendDN);
    if (backend != null)
    {
      LinkedHashSet<DN> removedDNs = new LinkedHashSet<DN>();
      for (DN dn : backend.getBaseDNs())
      {
        removedDNs.add(dn);
      }

      LinkedHashSet<DN> addedDNs = new LinkedHashSet<DN>();
      for (DN dn : baseDNs)
      {
        addedDNs.add(dn);
      }

      Iterator<DN> iterator = removedDNs.iterator();
      while (iterator.hasNext())
      {
        DN dn = iterator.next();
        if (addedDNs.remove(dn))
        {
          iterator.remove();
        }
      }

      for (DN dn : addedDNs)
      {
        try
        {
          DirectoryServer.registerBaseDN(dn, backend, false, true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          unacceptableReason.add(de.getMessage());
          return false;
        }
      }

      for (DN dn : removedDNs)
      {
        try
        {
          DirectoryServer.deregisterBaseDN(dn, true);
        }
        catch (DirectoryException de)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, de);
          }

          unacceptableReason.add(de.getMessage());
          return false;
        }
      }
    }


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = configEntry.getBackendClass();
    try
    {
      Class backendClass = DirectoryServer.loadClass(className);
      if (! Backend.class.isAssignableFrom(backendClass))
      {
        int msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
        unacceptableReason.add(getMessage(msgID, String.valueOf(className),
                                          String.valueOf(backendDN)));
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      unacceptableReason.add(getMessage(msgID, String.valueOf(className),
                                        String.valueOf(backendDN),
                                        stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // backend, then the backend itself will need to make that determination.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(BackendCfg cfg)
  {
    DN                backendDN           = cfg.dn();
    Backend           backend             = registeredBackends.get(backendDN);
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.
    boolean needToEnable = false;
    try
    {
      if (cfg.isBackendEnabled())
      {
        // The backend is marked as enabled.  See if that is already true.
        if (backend == null)
        {
          needToEnable = true;
        }
        else
        {
          // It's already enabled, so we don't need to do anything.
        }
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

          for (BackendInitializationListener listener :
               DirectoryServer.getBackendInitializationListeners())
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
              int msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
              String message = getMessage(msgID, backend.getBackendID(),
                                          String.valueOf(failureReason));
              logError(ErrorLogCategory.CONFIGURATION,
                       ErrorLogSeverity.SEVERE_WARNING, message, msgID);
              // FIXME -- Do we need to send an admin alert?
            }
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }

            int msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            String message = getMessage(msgID, backend.getBackendID(),
                                        stackTraceToSingleLineString(e2));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }

          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
        else
        {
          // It's already disabled, so we don't need to do anything.
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE;
      messages.add(getMessage(msgID, String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // See if the entry contains an attribute that specifies the backend ID for
    // the backend.
    String backendID = cfg.getBackendId();

    // See if the entry contains an attribute that specifies the writability
    // mode.
    WritabilityMode writabilityMode = WritabilityMode.ENABLED;
    BackendCfgDefn.BackendWritabilityMode bwm =
         cfg.getBackendWritabilityMode();
    switch (bwm)
    {
      case DISABLED:
        writabilityMode = WritabilityMode.DISABLED;
        break;
      case ENABLED:
        writabilityMode = WritabilityMode.ENABLED;
        break;
      case INTERNAL_ONLY:
        writabilityMode = WritabilityMode.INTERNAL_ONLY;
        break;
    }


    // See if the entry contains an attribute that specifies the base DNs for
    // the backend.
    Set<DN> baseList = cfg.getBackendBaseDN();
    DN[] baseDNs = new DN[baseList.size()];
    baseList.toArray(baseDNs);


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = cfg.getBackendClass();


    // See if this backend is currently active and if so if the name of the
    // class is the same.
    if (backend != null)
    {
      if (! className.equals(backend.getClass().getName()))
      {
        // It is not the same.  Try to load it and see if it is a valid backend
        // implementation.
        try
        {
          Class backendClass = DirectoryServer.loadClass(className);
          if (Backend.class.isAssignableFrom(backendClass))
          {
            // It appears to be a valid backend class.  We'll return that the
            // change is successful, but indicate that some administrative
            // action is required.
            int msgID = MSGID_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS;
            messages.add(getMessage(msgID, String.valueOf(backendDN),
                                    backend.getClass().getName(), className));
            adminActionRequired = true;
            return new ConfigChangeResult(resultCode, adminActionRequired,
                                          messages);
          }
          else
          {
            // It is not a valid backend class.  This is an error.
            int msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
            messages.add(getMessage(msgID, String.valueOf(className),
                                    String.valueOf(backendDN)));
            resultCode = ResultCode.CONSTRAINT_VIOLATION;
            return new ConfigChangeResult(resultCode, adminActionRequired,
                                          messages);
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          int msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
          messages.add(getMessage(msgID, String.valueOf(className),
                                  String.valueOf(backendDN),
                                  stackTraceToSingleLineString(e)));
          resultCode = DirectoryServer.getServerErrorResultCode();
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
    }


    // If we've gotten here, then that should mean that we need to enable the
    // backend.  Try to do so.
    if (needToEnable)
    {
      Class backendClass;
      try
      {
        backendClass = DirectoryServer.loadClass(className);
        backend = (Backend) backendClass.newInstance();
      }
      catch (Exception e)
      {
        // It is not a valid backend class.  This is an error.
        int msgID = MSGID_CONFIG_BACKEND_CLASS_NOT_BACKEND;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(backendDN)));
        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
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
          int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?

          resultCode = ResultCode.CONSTRAINT_VIOLATION;
          adminActionRequired = true;
          messages.add(message);
          return new ConfigChangeResult(resultCode, adminActionRequired,
                                        messages);
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        adminActionRequired = true;
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      try
      {
        initializeBackend(backend, cfg);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
        messages.add(getMessage(msgID, String.valueOf(className),
                                String.valueOf(backendDN),
                                stackTraceToSingleLineString(e)));
        resultCode = DirectoryServer.getServerErrorResultCode();

        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
            String message = getMessage(msgID, backendID,
                                        String.valueOf(failureReason));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }

          msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }

        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      // Notify any backend initialization listeners.
      for (BackendInitializationListener listener :
           DirectoryServer.getBackendInitializationListeners())
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        int msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
        String message = getMessage(msgID, backendID,
                                    getExceptionMessage(e));

        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(message);

        logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
                 message, msgID);
        // FIXME -- Do we need to send an admin alert?

        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }


      registeredBackends.put(backendDN, backend);
    }
    else if ((resultCode == ResultCode.SUCCESS) && (backend != null))
    {
      // The backend is already enabled, so we may need to apply a
      // configuration change.  Check to see if the writability mode has been
      // changed.
      if (writabilityMode != backend.getWritabilityMode())
      {
        backend.setWritabilityMode(writabilityMode);
      }
    }


    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
       BackendCfg configEntry,
       List<String> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID = configEntry.getBackendId();
    if (DirectoryServer.hasBackend(backendID))
    {
      int msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
      unacceptableReason.add(getMessage(msgID,
                                        String.valueOf(backendDN)));
      return false;
    }


    // See if the entry contains an attribute that specifies the set of base DNs
    // for the backend.  If it does not, then skip it.
    Set<DN> baseList = configEntry.getBackendBaseDN();
    DN[] baseDNs = new DN[baseList.size()];
    baseList.toArray(baseDNs);


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = configEntry.getBackendClass();

    Backend backend;
    try
    {
      Class backendClass = DirectoryServer.loadClass(className);
      backend = (Backend) backendClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      unacceptableReason.add(getMessage(msgID, String.valueOf(className),
                                        String.valueOf(backendDN),
                                        stackTraceToSingleLineString(e)));
      return false;
    }


    // If the backend is a configurable component, then make sure that its
    // configuration is valid.
//    if (backend instanceof ConfigurableComponent)
//    {
//      ConfigurableComponent cc = (ConfigurableComponent) backend;
//      LinkedList<String> errorMessages = new LinkedList<String>();
//      if (! cc.hasAcceptableConfiguration(configEntry, errorMessages))
//      {
//        if (errorMessages.isEmpty())
//        {
//          int msgID = MSGID_CONFIG_BACKEND_UNACCEPTABLE_CONFIG;
//          unacceptableReason.add(getMessage(msgID,
//                                            String.valueOf(configEntryDN)));
//        }
//        else
//        {
//          Iterator<String> iterator = errorMessages.iterator();
//          unacceptableReason.add(iterator.next());
//          while (iterator.hasNext())
//          {
//            unacceptableReason.add("  ");
//            unacceptableReason.add(iterator.next());
//          }
//        }
//
//        return false;
//      }
//    }


    // Make sure that all of the base DNs are acceptable for use in the server.
    for (DN baseDN : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(baseDN, backend, false, true);
      }
      catch (DirectoryException de)
      {
        unacceptableReason.add(de.getMessage());
        return false;
      }
      catch (Exception e)
      {
        unacceptableReason.add(getExceptionMessage(e));
        return false;
      }
    }


    // If we've gotten to this point, then it is acceptable as far as we are
    // concerned.  If it is unacceptable according to the configuration for that
    // backend, then the backend itself will need to make that determination.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(BackendCfg cfg)
  {
    DN                backendDN           = cfg.dn();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Register as a change listener for this backend entry so that we will
    // be notified of any changes that may be made to it.
    cfg.addChangeListener(this);


    // See if the entry contains an attribute that indicates whether the
    // backend should be enabled.  If it does not, or if it is not set to
    // "true", then skip it.
    if (!cfg.isBackendEnabled())
    {
      // The backend is explicitly disabled.  We will log a message to
      // indicate that it won't be enabled and return.
      int msgID = MSGID_CONFIG_BACKEND_DISABLED;
      String message = getMessage(msgID, String.valueOf(backendDN));
      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.INFORMATIONAL, message, msgID);
      messages.add(message);
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }



    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID = cfg.getBackendId();
    if (DirectoryServer.hasBackend(backendID))
    {
      int msgID = MSGID_CONFIG_BACKEND_DUPLICATE_BACKEND_ID;
      String message = getMessage(msgID, String.valueOf(backendDN));
      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
      messages.add(message);
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // See if the entry contains an attribute that specifies the writability
    // mode.
    WritabilityMode writabilityMode = WritabilityMode.ENABLED;
    BackendCfgDefn.BackendWritabilityMode bwm =
         cfg.getBackendWritabilityMode();
    switch (bwm)
    {
      case DISABLED:
        writabilityMode = WritabilityMode.DISABLED;
        break;
      case ENABLED:
        writabilityMode = WritabilityMode.ENABLED;
        break;
      case INTERNAL_ONLY:
        writabilityMode = WritabilityMode.INTERNAL_ONLY;
        break;
    }


    // See if the entry contains an attribute that specifies the base DNs for
    // the entry.  If it does not, then skip it.
    Set<DN> dnList = cfg.getBackendBaseDN();
    DN[] baseDNs = new DN[dnList.size()];
    dnList.toArray(baseDNs);


    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = cfg.getBackendClass();
    Class backendClass;

    Backend backend;
    try
    {
      backendClass = DirectoryServer.loadClass(className);
      backend = (Backend) backendClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_INSTANTIATE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
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
        int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    String.valueOf(failureReason));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
        adminActionRequired = true;
        messages.add(message);
        return new ConfigChangeResult(resultCode, adminActionRequired,
                                      messages);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK;
      String message = getMessage(msgID, backendID,
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.CONFIGURATION,
               ErrorLogSeverity.SEVERE_WARNING, message, msgID);
      // FIXME -- Do we need to send an admin alert?

      resultCode = ResultCode.CONSTRAINT_VIOLATION;
      adminActionRequired = true;
      messages.add(message);
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // Perform the necessary initialization for the backend entry.
    try
    {
      initializeBackend(backend, cfg);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_INITIALIZE;
      messages.add(getMessage(msgID, String.valueOf(className),
                              String.valueOf(backendDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();

      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          String message = getMessage(msgID, backendID,
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }

        msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
        String message = getMessage(msgID, backendID,
                                    stackTraceToSingleLineString(e2));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Notify any backend initialization listeners.
    for (BackendInitializationListener listener :
         DirectoryServer.getBackendInitializationListeners())
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND;
      String message = getMessage(msgID, backendID,
                                  getExceptionMessage(e));

      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
      // FIXME -- Do we need to send an admin alert?

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }

    registeredBackends.put(backendDN, backend);
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }


  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
       BackendCfg configEntry,
       List<String> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.  If we
    // do know about it, then that means that it is enabled and we will not
    // allow removing a backend that is enabled.
    Backend backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return true;
    }


    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend[] subBackends = backend.getSubordinateBackends();
    if ((subBackends == null) || (subBackends.length == 0))
    {
      return true;
    }
    else
    {
      int msgID = MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES;
      unacceptableReason.add(getMessage(msgID, String.valueOf(backendDN)));
      return false;
    }
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(BackendCfg configEntry)
  {
    DN                backendDN           = configEntry.dn();
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // See if this backend config manager has a backend registered with the
    // provided DN.  If not, then we don't care if the entry is deleted.
    Backend backend = registeredBackends.get(backendDN);
    if (backend == null)
    {
      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }


    // See if the backend has any subordinate backends.  If so, then it is not
    // acceptable to remove it.  Otherwise, it should be fine.
    Backend[] subBackends = backend.getSubordinateBackends();
    if ((subBackends == null) || (subBackends.length == 0))
    {
      registeredBackends.remove(backendDN);

      try
      {
        backend.finalizeBackend();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }

      for (BackendInitializationListener listener :
           DirectoryServer.getBackendInitializationListeners())
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
          int msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
          String message = getMessage(msgID, backend.getBackendID(),
                                      String.valueOf(failureReason));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e2)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e2);
        }

        int msgID = MSGID_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK;
        String message = getMessage(msgID, backend.getBackendID(),
                                    stackTraceToSingleLineString(e2));
        logError(ErrorLogCategory.CONFIGURATION,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);
        // FIXME -- Do we need to send an admin alert?
      }

      return new ConfigChangeResult(resultCode, adminActionRequired,
                                    messages);
    }
    else
    {
      int msgID = MSGID_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES;
      messages.add(getMessage(msgID, String.valueOf(backendDN)));
      resultCode = ResultCode.UNWILLING_TO_PERFORM;
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }
  }

  @SuppressWarnings("unchecked")
  private static void initializeBackend(Backend backend, BackendCfg cfg)
       throws ConfigException, InitializationException
  {
    backend.configureBackend(cfg);
    backend.initializeBackend();
  }

}

