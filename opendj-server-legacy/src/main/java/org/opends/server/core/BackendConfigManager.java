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
package org.opends.server.core;

import static org.forgerock.opendj.ldap.ResultCode.*;
import static org.forgerock.util.Reject.ifNull;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.meta.LocalBackendCfgDefn;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.forgerock.opendj.server.config.server.LocalBackendCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.RootDSEBackendCfg;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.Backend;
import org.opends.server.api.LocalBackendInitializationListener;
import org.opends.server.backends.ConfigurationBackend;
import org.opends.server.backends.RootDSEBackend;
import org.opends.server.config.ConfigConstants;
import org.opends.server.monitors.LocalBackendMonitor;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.WritabilityMode;

/**
 * Responsible for managing the configuration of backends defined in the Directory Server.
 * <p>
 * It will perform the necessary initialization of those backends when the server is first
 * started, and then will manage any changes to them while the server is running.
 */
public class BackendConfigManager implements
     ConfigurationChangeListener<BackendCfg>,
     ConfigurationAddListener<BackendCfg>,
     ConfigurationDeleteListener<BackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The mapping beetwen backends IDs and local backend implementations. */
  private final Map<String, LocalBackend<?>> localBackends = new ConcurrentHashMap<>();

  /** The mapping between configuration entry DNs and their corresponding backend implementations. */
  private final Map<DN, Backend<? extends BackendCfg>> registeredBackends = new ConcurrentHashMap<>();

  /** The set of local backend initialization listeners. */
  private final Set<LocalBackendInitializationListener> initializationListeners = new CopyOnWriteArraySet<>();

  private final ServerContext serverContext;
  private final BaseDnRegistry localBackendsRegistry;

  private RootDSEBackend rootDSEBackend;

  /** Lock to protect reads of the backend maps. */
  private final ReadLock readLock;
  /** Lock to protect updates of the backends maps. */
  private final WriteLock writeLock;

  /**
   * Creates a new instance of this backend config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public BackendConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
    this.localBackendsRegistry = new BaseDnRegistry();
    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    readLock = lock.readLock();
    writeLock = lock.writeLock();
  }

  /**
   * Initializes the configuration associated with the Directory Server
   * backends. This should only be called at Directory Server startup.
   *
   * @param backendIDsToStart
   *           The list of backendID to start. Everything will be started if empty.
   * @throws ConfigException
   *           If a critical configuration problem prevents the backend
   *           initialization from succeeding.
   * @throws InitializationException
   *           If a problem occurs while initializing the backends that is not
   *           related to the server configuration.
   */
  public void initializeBackendConfig(Collection<String> backendIDsToStart)
         throws ConfigException, InitializationException
  {
    initializeConfigurationBackend();

    // Register add and delete listeners.
    RootCfg root = serverContext.getRootConfig();
    root.addBackendAddListener(this);
    root.addBackendDeleteListener(this);

    // Get the configuration entry that is at the root of all the backends in
    // the server.
    Entry backendRoot;
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
      throw new ConfigException(ERR_CONFIG_BACKEND_BASE_DOES_NOT_EXIST.get());
    }
    initializeBackends(backendIDsToStart, root);
  }

  /**
   * Creates and initializes the Root DSE backend.
   *
   * @throws InitializationException
   *            If the configuration entry can't be found
   * @throws ConfigException
   *            If an error occurs during configuration
   */
  public void initializeRootDSEBackend() throws InitializationException, ConfigException
  {
    RootDSEBackendCfg rootDSECfg;
    try
    {
      rootDSECfg = serverContext.getRootConfig().getRootDSEBackend();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY.get(
          stackTraceToSingleLineString(e)), e);
    }
    rootDSEBackend = new RootDSEBackend();
    rootDSEBackend.configureBackend(rootDSECfg, serverContext);
    rootDSEBackend.openBackend();
  }

  /**
   * Initializes specified backends. If a backend has been already initialized, do nothing.
   * This should only be called at Directory Server startup, after #initializeBackendConfig()
   *
   * @param backendIDsToStart
   *           The list of backendID to start. Everything will be started if empty.
   * @param root
   *           The configuration of the server's Root backend
   * @throws ConfigException
   *           If a critical configuration problem prevents the backend
   *           initialization from succeeding.
   */
  public void initializeBackends(Collection<String> backendIDsToStart, RootCfg root) throws ConfigException
  {
    // Initialize existing backends.
    for (String name : root.listBackends())
    {
      // Get the handler's configuration.
      // This will decode and validate its properties.
      final BackendCfg backendCfg = root.getBackend(name);
      final String backendID = backendCfg.getBackendId();
      if (!backendIDsToStart.isEmpty() && !backendIDsToStart.contains(backendID))
      {
        continue;
      }
      if (hasLocalBackend(backendID))
      {
        // Skip this backend if it is already initialized and registered as available.
        continue;
      }

      // Register as a change listener for this backend so that we can be
      // notified when it is disabled or enabled.
      backendCfg.addChangeListener(this);

      final DN backendDN = backendCfg.dn();
      if (!backendCfg.isEnabled())
      {
        logger.debug(INFO_CONFIG_BACKEND_DISABLED, backendDN);
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

      initializeBackend(backend, backendCfg);
    }
  }

  private void initializeConfigurationBackend() throws InitializationException
  {
    final ConfigurationBackend configBackend =
        new ConfigurationBackend(serverContext, DirectoryServer.getConfigurationHandler());
    initializeBackend(configBackend, configBackend.getBackendCfg());
  }

  private void initializeBackend(Backend<? extends BackendCfg> backend, BackendCfg backendCfg)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    initializeBackend(backend, backendCfg, ccr);
    for (LocalizableMessage msg : ccr.getMessages())
    {
      logger.error(msg);
    }
  }

  private void initializeBackend(Backend<? extends BackendCfg> backend, BackendCfg backendCfg,
      ConfigChangeResult ccr)
  {
    backend.setBackendID(backendCfg.getBackendId());
    setLocalBackendWritabilityMode(backend, backendCfg);

    if (acquireSharedLock(backend, backendCfg.getBackendId(), ccr) && configureAndOpenBackend(backend, backendCfg, ccr))
    {
      registerBackend(backend, backendCfg, ccr);
    }
  }

  private void setLocalBackendWritabilityMode(Backend<?> backend, BackendCfg backendCfg)
  {
    if (backend instanceof LocalBackend)
    {
      LocalBackend<?> localBackend = (LocalBackend<?>) backend;
      LocalBackendCfg localCfg = (LocalBackendCfg) backendCfg;
      localBackend.setWritabilityMode(toWritabilityMode(localCfg.getWritabilityMode()));
    }
  }

  /**
   * Returns the provided backend instance as a LocalBackend.
   *
   * @param backend
   *            A backend
   * @return a local backend
   * @throws IllegalArgumentException
   *            If the provided backend is not a LocalBackend
   */
  public static LocalBackend<?> asLocalBackend(Backend<?> backend)
  {
    if (backend instanceof LocalBackend)
    {
      return (LocalBackend<?>) backend;
    }
    throw new IllegalArgumentException("Backend " + backend.getBackendID() + " is not a local backend");
  }

  /**
   * Acquire a shared lock on this backend. This will prevent operations like LDIF import or restore
   * from occurring while the backend is active.
   */
  private boolean acquireSharedLock(Backend<?> backend, String backendID, final ConfigChangeResult ccr)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        cannotAcquireLock(backendID, ccr, failureReason);
        return false;
      }
      return true;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      cannotAcquireLock(backendID, ccr, stackTraceToSingleLineString(e));
      return false;
    }
  }

  private void cannotAcquireLock(String backendID, final ConfigChangeResult ccr, CharSequence failureReason)
  {
    LocalizableMessage message = ERR_CONFIG_BACKEND_CANNOT_ACQUIRE_SHARED_LOCK.get(backendID, failureReason);
    logger.error(message);

    // FIXME -- Do we need to send an admin alert?
    ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
    ccr.setAdminActionRequired(true);
    ccr.addMessage(message);
  }

  private void releaseSharedLock(Backend<?> backend, String backendID)
  {
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
  }

  /**
   * Returns the set of all backends.
   *
   * @return all backends
   */
  public Set<Backend<?>> getAllBackends()
  {
    return new HashSet<Backend<?>>(registeredBackends.values());
  }

  /**
   * Returns the set of local backends.
   *
   * @return the local backends
   */
  public Set<LocalBackend<?>> getLocalBackends()
  {
    return new HashSet<LocalBackend<?>>(localBackends.values());
  }

  /**
   * Retrieves the local backend with the specified base DN.
   *
   * @param  baseDN  The DN that is registered as one of the base DNs for the
   *                 backend to retrieve.
   *
   * @return  The local backend with the specified base DN, or {@code null} if there
   *          is no local backend registered with the specified base DN.
   */
  public LocalBackend<?> getLocalBackendWithBaseDN(DN baseDN)
  {
    return localBackendsRegistry.getBackendWithBaseDN(baseDN);
  }

  /**
   * Retrieves the Root DSE backend.
   *
   * @return the Root DSE backend.
   */
  public RootDSEBackend getRootDSEBackend()
  {
    return rootDSEBackend;
  }

  /**
   * Retrieves the local backend and the corresponding baseDN that should be used to handle operations
   * on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding backend.
   * @return The local backend with its matching base DN or {@code null} if no appropriate backend
   *         is registered with the server.
   */
  public BackendAndName getLocalBackendAndName(DN entryDN)
  {
    return entryDN.isRootDN() ?
        new BackendAndName(getRootDSEBackend(), entryDN) : localBackendsRegistry.getBackendAndName(entryDN);
  }

  /**
   * Retrieves the local backend that should be used to handle operations
   * on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding backend.
   * @return The local backend or {@code null} if no appropriate backend
   *         is registered with the server.
   */
  public LocalBackend<?> getLocalBackend(DN entryDN)
  {
    BackendAndName backend = getLocalBackendAndName(entryDN);
    return backend != null ? backend.getBackend() : null;
  }

  /**
   * Retrieves a local backend provided its identifier.
   *
   * @param backendId
   *          Identifier of the backend
   * @return the local backend, or {@code null} if there is no local backend registered with the
   *            specified id.
   */
  public LocalBackend<?> getLocalBackend(String backendId)
  {
    return localBackends.get(backendId);
  }

  /**
   * Indicates whether the local backend with the provided id exists.
   *
   * @param backendID
   *          The backend ID for which to make the determination.
   * @return {@code true} if a backend with the specified backend ID exists, {@code false} otherwise
   */
  public boolean hasLocalBackend(String backendID)
  {
    return localBackends.containsKey(backendID);
  }

  /**
   * Retrieves the set of subordinate backends of the backend that corresponds to provided base DN.
   *
   * @param baseDN
   *          The base DN for which to retrieve the subordinates backends.
   * @return The set of subordinates backends (and associated base DN), which is never {@code null}
   */
  public Set<BackendAndName> getSubordinateBackends(DN baseDN)
  {
    final Set<BackendAndName> subs = new HashSet<>();
    LocalBackend<?> backend = getLocalBackendWithBaseDN(baseDN);
    if (backend == null)
    {
      return subs;
    }
    for (LocalBackend<?> subordinate : backend.getSubordinateBackends())
    {
      for (DN subordinateDN : subordinate.getBaseDNs())
      {
        if (subordinateDN.isSubordinateOrEqualTo(baseDN))
        {
          subs.add(new BackendAndName(subordinate, subordinateDN));
        }
      }
    }
    return subs;
  }

  /**
   * Gets the mapping of registered public naming contexts, not including
   * sub-suffixes, to their associated backend.
   *
   * @return mapping from naming context to backend
   */
  public Map<DN, LocalBackend<?>> getPublicNamingContexts()
  {
    return localBackendsRegistry.getPublicNamingContextsMap();
  }

  /**
   * Gets the mapping of registered public naming contexts, including sub-suffixes,
   * to their associated backend.
   *
   * @return mapping from naming context to backend
   */
  public Map<DN, LocalBackend<?>> getAllPublicNamingContexts()
  {
    return localBackendsRegistry.getAllPublicNamingContextsMap();
  }

  /**
   * Gets the mapping of registered private naming contexts to their
   * associated backend.
   *
   * @return mapping from naming context to backend
   */
  public Map<DN, LocalBackend<?>> getPrivateNamingContexts()
  {
    return localBackendsRegistry.getPrivateNamingContextsMap();
  }

  /**
   * Indicates whether the specified DN is contained in the backends as
   * a naming contexts.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  {@code true} if the specified DN is a naming context in one
   *          backend, or {@code false} if it is not.
   */
  public boolean containsNamingContext(DN dn)
  {
    return localBackendsRegistry.containsNamingContext(dn);
  }

  /**
   * Registers the provided base DN.
   *
   * @param baseDN
   *          The base DN to register. It must not be {@code null}.
   * @param backend
   *          The backend responsible for the provided base DN. It must not be {@code null}.
   * @param isPrivate
   *          Indicates whether the base DN should be considered a private base DN. If the provided
   *          base DN is a naming context, then this controls whether it is public or private.
   * @throws DirectoryException
   *           If a problem occurs while attempting to register the provided base DN.
   */
  public void registerBaseDN(DN baseDN, LocalBackend<?> backend, boolean isPrivate) throws DirectoryException
  {
    List<LocalizableMessage> warnings = localBackendsRegistry.registerBaseDN(baseDN, backend, isPrivate);
    for (LocalizableMessage warning : warnings)
    {
      logger.error(warning);
    }
  }

  /**
   * Registers a local backend.
   *
   * @param backend
   *          The backend to register with the server.
   *          Neither the backend nor its backend ID may be null.
   * @throws DirectoryException
   *           If the backend ID for the provided backend conflicts with the backend ID of an
   *           existing backend.
   */
  public void registerLocalBackend(LocalBackend<?> backend) throws DirectoryException
  {
    ifNull(backend);
    String backendID = backend.getBackendID();
    ifNull(backendID);
    writeLock.lock();
    try
    {
      if (localBackends.containsKey(backendID))
      {
        LocalizableMessage message = ERR_REGISTER_BACKEND_ALREADY_EXISTS.get(backendID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      localBackends.put(backendID, backend);

      LocalBackendMonitor monitor = new LocalBackendMonitor(backend);
      monitor.initializeMonitorProvider(null);
      backend.setBackendMonitor(monitor);
      registerMonitorProvider(monitor);
    }
    finally {
      writeLock.unlock();
    }
  }

  /**
   * Registers a local backend initialization listener.
   *
   * @param  listener  The listener to register.
   */
  public void registerBackendInitializationListener(LocalBackendInitializationListener listener)
  {
    initializationListeners.add(listener);
  }

  /**
   * Deregisters the provided base DN.
   *
   * @param baseDN
   *          The base DN to deregister. It must not be {@code null}.
   * @throws DirectoryException
   *           If a problem occurs while attempting to deregister the provided base DN.
   */
  public void deregisterBaseDN(DN baseDN) throws DirectoryException
  {
    List<LocalizableMessage> warnings = localBackendsRegistry.deregisterBaseDN(baseDN);
    for (LocalizableMessage error : warnings)
    {
      logger.error(error);
    }
  }

  /**
   * Deregisters a local backend initialization listener.
   *
   * @param  listener  The listener to deregister.
   */
  public void deregisterBackendInitializationListener(LocalBackendInitializationListener listener)
  {
    initializationListeners.remove(listener);
  }

  /**
   * Deregisters a local backend.
   *
   * @param backend
   *          The backend to deregister with the server. It must not be {@code null}.
   */
  public void deregisterLocalBackend(LocalBackend<?> backend)
  {
    ifNull(backend);
    writeLock.lock();
    try
    {
      localBackends.remove(backend.getBackendID());
      LocalBackendMonitor monitor = backend.getBackendMonitor();
      if (monitor != null)
      {
        deregisterMonitorProvider(monitor);
        monitor.finalizeMonitorProvider();
        backend.setBackendMonitor(null);
      }
    }
    finally {
      writeLock.unlock();
    }
  }

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
      LinkedHashSet<DN> removedDNs = new LinkedHashSet<>(backend.getBaseDNs());
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

      if (backend instanceof LocalBackend<?>)
      {
        // Copy the registry and make the requested changes to see if it complains.
        LocalBackend<?> localBackend = (LocalBackend<?>) backend;
        BaseDnRegistry registry = localBackendsRegistry.copy();
        for (DN dn : removedDNs)
        {
          try
          {
            registry.deregisterBaseDN(dn);
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
            registry.registerBaseDN(dn, localBackend, false);
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            unacceptableReason.add(de.getMessageObject());
            return false;
          }
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
          deregisterBackend(backendDN, backend);

          backend.finalizeBackend();

          releaseSharedLock(backend, backend.getBackendID());

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

    // See if the entry contains an attribute that specifies the class name
    // for the backend implementation.  If it does, then load it and make sure
    // that it's a valid backend implementation.  There is no such attribute,
    // the specified class cannot be loaded, or it does not contain a valid
    // backend implementation, then log an error and skip it.
    String className = cfg.getJavaClass();

    // See if this backend is currently active and if so if the name of the class is the same.
    if (backend != null && !className.equals(backend.getClass().getName()))
    {
      // It is not the same. Try to load it and see if it is a valid backend implementation.
      try
      {
        Class<?> backendClass = DirectoryServer.loadClass(className);
        if (LocalBackend.class.isAssignableFrom(backendClass))
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

      initializeBackend(backend, cfg, ccr);
      return ccr;
    }
    else if (ccr.getResultCode() == ResultCode.SUCCESS && backend != null)
    {
      setLocalBackendWritabilityMode(backend, cfg);
    }

    return ccr;
  }

  private boolean registerBackend(Backend<? extends BackendCfg> backend, BackendCfg backendCfg, ConfigChangeResult ccr)
  {
    if (backend instanceof LocalBackend<?>)
    {
      LocalBackend<?> localBackend = (LocalBackend<?>) backend;
      for (LocalBackendInitializationListener listener : initializationListeners)
      {
        listener.performBackendPreInitializationProcessing(localBackend);
      }

      try
      {
        registerLocalBackend(localBackend);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message =
            WARN_CONFIG_BACKEND_CANNOT_REGISTER_BACKEND.get(backendCfg.getBackendId(), getExceptionMessage(e));
        logger.error(message);

        // FIXME -- Do we need to send an admin alert?
        ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
        ccr.addMessage(message);
        return false;
      }

      for (LocalBackendInitializationListener listener : initializationListeners)
      {
        listener.performBackendPostInitializationProcessing(localBackend);
      }

      registeredBackends.put(backendCfg.dn(), backend);
      return true;
    }
    // TODO: manage proxy registration here
    return true;
  }

  @Override
  public boolean isConfigurationAddAcceptable(
       BackendCfg configEntry,
       List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();


    // See if the entry contains an attribute that specifies the backend ID.  If
    // it does not, then skip it.
    String backendID = configEntry.getBackendId();
    if (hasLocalBackend(backendID))
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
    if (backend instanceof LocalBackend<?>)
    {
      BaseDnRegistry registry = localBackendsRegistry.copy();
      for (DN baseDN : baseDNs)
      {
        if (baseDN.isRootDN())
        {
          unacceptableReason.add(ERR_CONFIG_BACKEND_BASE_IS_EMPTY.get(backendDN));
          return false;
        }
        try
        {
          registry.registerBaseDN(baseDN, (LocalBackend<?>) backend, false);
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
    }

    return backend.isConfigurationAcceptable(configEntry, unacceptableReason, serverContext);
  }

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
    if (hasLocalBackend(backendID))
    {
      LocalizableMessage message = WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID.get(backendDN, backendID);
      logger.warn(message);
      ccr.addMessage(message);
      return ccr;
    }


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

    initializeBackend(backend, cfg, ccr);
    return ccr;
  }

  private boolean configureAndOpenBackend(Backend<?> backend, BackendCfg cfg, ConfigChangeResult ccr)
  {
    try
    {
      configureAndOpenBackend(backend, cfg);
      return true;
    }
    catch (Exception e)
    {
      logger.traceException(e);

      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_BACKEND_CANNOT_INITIALIZE.get(
          cfg.getJavaClass(), cfg.dn(), stackTraceToSingleLineString(e)));

      releaseSharedLock(backend, cfg.getBackendId());
      return false;
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void configureAndOpenBackend(Backend backend, BackendCfg cfg) throws ConfigException, InitializationException
  {
    backend.configureBackend(cfg, serverContext);
    backend.openBackend();
  }

  @SuppressWarnings("unchecked")
  private Class<Backend<BackendCfg>> loadBackendClass(String className) throws Exception
  {
    return (Class<Backend<BackendCfg>>) DirectoryServer.loadClass(className);
  }

  private WritabilityMode toWritabilityMode(LocalBackendCfgDefn.WritabilityMode writabilityMode)
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

    // TODO: what about non local backend ?
    if (backend instanceof LocalBackend)
    {
      // See if the backend has any subordinate backends.  If so, then it is not
      // acceptable to remove it.  Otherwise, it should be fine.
      LocalBackend<?>[] subBackends = ((LocalBackend<?>) backend).getSubordinateBackends();
      if (subBackends != null && subBackends.length != 0)
      {
        unacceptableReason.add(NOTE_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES.get(backendDN));
        return false;
      }
    }
    return true;
  }

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

    // TODO: what about non local backend ?
    if (backend instanceof LocalBackend)
    {
      // See if the backend has any subordinate backends.  If so, then it is not
      // acceptable to remove it.  Otherwise, it should be fine.
      LocalBackend<?>[] subBackends = ((LocalBackend<?>) backend).getSubordinateBackends();
      if (subBackends != null && subBackends.length > 0)
      {
        ccr.setResultCode(UNWILLING_TO_PERFORM);
        ccr.addMessage(NOTE_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES.get(backendDN));
        return ccr;
      }
    }

    deregisterBackend(backendDN, backend);

    try
    {
      backend.finalizeBackend();
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    configEntry.removeChangeListener(this);

    releaseSharedLock(backend, backend.getBackendID());

    return ccr;
  }

  private void deregisterBackend(DN backendDN, Backend<?> backend)
  {
    if (backend instanceof LocalBackend<?>)
    {
      LocalBackend<?> localBackend = (LocalBackend<?>) backend;
      for (LocalBackendInitializationListener listener : initializationListeners)
      {
        listener.performBackendPreFinalizationProcessing(localBackend);
      }

      registeredBackends.remove(backendDN);
      deregisterLocalBackend(localBackend);

      for (LocalBackendInitializationListener listener : initializationListeners)
      {
        listener.performBackendPostFinalizationProcessing(localBackend);
      }
    }
    else {
      // TODO: manage proxy deregistering here
    }
  }

  /** Shutdown all local backends. */
  public void shutdownLocalBackends()
  {
    for (LocalBackend<?> backend : localBackends.values())
    {
      try
      {
        for (LocalBackendInitializationListener listener : initializationListeners)
        {
          listener.performBackendPreFinalizationProcessing(backend);
        }

        backend.finalizeBackend();

        for (LocalBackendInitializationListener listener : initializationListeners)
        {
          listener.performBackendPostFinalizationProcessing(backend);
        }

        // Remove the shared lock for this backend.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (!LockFileManager.releaseLock(lockFile, failureReason))
          {
            logger.warn(WARN_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK, backend.getBackendID(), failureReason);
            // FIXME -- Do we need to send an admin alert?
          }
        }
        catch (Exception e2)
        {
          logger.traceException(e2);

          logger.warn(WARN_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK, backend.getBackendID(),
              stackTraceToSingleLineString(e2));
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Registry for maintaining the set of registered base DN's, associated local backends
   * and naming context information.
   */
  static private class BaseDnRegistry {

    /** The set of base DNs registered with the server. */
    private final TreeMap<DN, LocalBackend<?>> baseDNs = new TreeMap<>();
    /** The set of private naming contexts registered with the server. */
    private final TreeMap<DN, LocalBackend<?>> privateNamingContexts = new TreeMap<>();
    /** The set of public naming contexts registered with the server. */
    private final TreeMap<DN, LocalBackend<?>> publicNamingContexts = new TreeMap<>();
    /** The set of public naming contexts, including sub-suffixes, registered with the server. */
    private final TreeMap<DN, LocalBackend<?>> allPublicNamingContexts = new TreeMap<>();

    /**
     * Indicates whether this base DN registry is in test mode.
     * A registry instance that is in test mode will not modify backend
     * objects referred to in the above maps.
     */
    private boolean testOnly;

    /**
     * Registers a base DN with this registry.
     *
     * @param  baseDN to register
     * @param  backend with which the base DN is associated
     * @param  isPrivate indicates whether this base DN is private
     * @return list of error messages generated by registering the base DN
     *         that should be logged if the changes to this registry are
     *         committed to the server
     * @throws DirectoryException if the base DN cannot be registered
     */
    List<LocalizableMessage> registerBaseDN(DN baseDN, LocalBackend<?> backend, boolean isPrivate)
        throws DirectoryException
    {
      // Check to see if the base DN is already registered with the server.
      LocalBackend<?> existingBackend = baseDNs.get(baseDN);
      if (existingBackend != null)
      {
        LocalizableMessage message = ERR_REGISTER_BASEDN_ALREADY_EXISTS.
            get(baseDN, backend.getBackendID(), existingBackend.getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      // Check to see if the backend is already registered with the server for
      // any other base DN(s).  The new base DN must not have any hierarchical
      // relationship with any other base Dns for the same backend.
      LinkedList<DN> otherBaseDNs = new LinkedList<>();
      for (DN dn : baseDNs.keySet())
      {
        LocalBackend<?> b = baseDNs.get(dn);
        if (b.equals(backend))
        {
          otherBaseDNs.add(dn);

          if (baseDN.isSuperiorOrEqualTo(dn) || baseDN.isSubordinateOrEqualTo(dn))
          {
            LocalizableMessage message = ERR_REGISTER_BASEDN_HIERARCHY_CONFLICT.
                get(baseDN, backend.getBackendID(), dn);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
          }
        }
      }

      // Check to see if the new base DN is subordinate to any other base DN
      // already defined.  If it is, then any other base DN(s) for the same
      // backend must also be subordinate to the same base DN.
      final LocalBackend<?> superiorBackend = getSuperiorBackend(baseDN, otherBaseDNs, backend.getBackendID());
      if (superiorBackend == null && backend.getParentBackend() != null)
      {
        LocalizableMessage message = ERR_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE.
            get(baseDN, backend.getBackendID(), backend.getParentBackend().getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      // Check to see if the new base DN should be the superior base DN for any
      // other base DN(s) already defined.
      LinkedList<LocalBackend<?>> subordinateBackends = new LinkedList<>();
      LinkedList<DN>      subordinateBaseDNs  = new LinkedList<>();
      for (DN dn : baseDNs.keySet())
      {
        LocalBackend<?> b = baseDNs.get(dn);
        DN parentDN = dn.parent();
        while (parentDN != null)
        {
          if (parentDN.equals(baseDN))
          {
            subordinateBaseDNs.add(dn);
            subordinateBackends.add(b);
            break;
          }
          else if (baseDNs.containsKey(parentDN))
          {
            break;
          }

          parentDN = parentDN.parent();
        }
      }

      // If we've gotten here, then the new base DN is acceptable.  If we should
      // actually apply the changes then do so now.
      final List<LocalizableMessage> errors = new LinkedList<>();

      // Check to see if any of the registered backends already contain an
      // entry with the DN specified as the base DN.  This could happen if
      // we're creating a new subordinate backend in an existing directory
      // (e.g., moving the "ou=People,dc=example,dc=com" branch to its own
      // backend when that data already exists under the "dc=example,dc=com"
      // backend).  This condition shouldn't prevent the new base DN from
      // being registered, but it's definitely important enough that we let
      // the administrator know about it and remind them that the existing
      // backend will need to be reinitialized.
      if (superiorBackend != null)
      {
        if (superiorBackend.entryExists(baseDN))
        {
          errors.add(WARN_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS.
              get(superiorBackend.getBackendID(), baseDN, backend.getBackendID()));
        }
      }

      baseDNs.put(baseDN, backend);

      if (superiorBackend == null)
      {
        if (!testOnly)
        {
          backend.setPrivateBackend(isPrivate);
        }

        if (isPrivate)
        {
          privateNamingContexts.put(baseDN, backend);
        }
        else
        {
          publicNamingContexts.put(baseDN, backend);
        }
      }
      else if (otherBaseDNs.isEmpty() && !testOnly)
      {
        backend.setParentBackend(superiorBackend);
        superiorBackend.addSubordinateBackend(backend);
      }

      if (!testOnly)
      {
        for (LocalBackend<?> b : subordinateBackends)
        {
          LocalBackend<?> oldParentBackend = b.getParentBackend();
          if (oldParentBackend != null)
          {
            oldParentBackend.removeSubordinateBackend(b);
          }

          b.setParentBackend(backend);
          backend.addSubordinateBackend(b);
        }
      }

      if (!isPrivate)
      {
        allPublicNamingContexts.put(baseDN, backend);
      }
      for (DN dn : subordinateBaseDNs)
      {
        publicNamingContexts.remove(dn);
        privateNamingContexts.remove(dn);
      }

      return errors;
    }

    LocalBackend<?> getBackendWithBaseDN(DN entryDN)
    {
      return baseDNs.get(entryDN);
    }


    BackendAndName getBackendAndName(final DN entryDN)
    {
      /*
       * Try to minimize the number of lookups in the map to find the backend containing the entry.
       * 1) If the DN contains many RDNs it is faster to iterate through the list of registered backends,
       * 2) Otherwise iterating through the parents requires less lookups. It also avoids some attacks
       * where we would spend time going through the list of all parents to finally decide the
       * baseDN is absent.
       */
      if (entryDN.size() <= baseDNs.size())
      {
        DN matchedDN = entryDN;
        while (!matchedDN.isRootDN())
        {
          final LocalBackend<?> backend = baseDNs.get(matchedDN);
          if (backend != null)
          {
            return new BackendAndName(backend, matchedDN);
          }
          matchedDN = matchedDN.parent();
        }
        return null;
      }
      else
      {
        LocalBackend<?> backend = null;
        DN matchedDN = null;
        int currentSize = 0;
        for (DN backendDN : baseDNs.keySet())
        {
          if (entryDN.isSubordinateOrEqualTo(backendDN) && backendDN.size() > currentSize)
          {
            backend = baseDNs.get(backendDN);
            matchedDN = backendDN;
            currentSize = backendDN.size();
          }
        }
        return new BackendAndName(backend, matchedDN);
      }
    }

    private LocalBackend<?> getSuperiorBackend(DN baseDN, LinkedList<DN> otherBaseDNs, String backendID)
        throws DirectoryException
    {
      LocalBackend<?> superiorBackend = null;
      DN parentDN = baseDN.parent();
      while (parentDN != null)
      {
        if (baseDNs.containsKey(parentDN))
        {
          superiorBackend = baseDNs.get(parentDN);

          for (DN dn : otherBaseDNs)
          {
            if (!dn.isSubordinateOrEqualTo(parentDN))
            {
              LocalizableMessage msg = ERR_REGISTER_BASEDN_DIFFERENT_PARENT_BASES.get(baseDN, backendID, dn);
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
            }
          }
          break;
        }

        parentDN = parentDN.parent();
      }
      return superiorBackend;
    }

    /**
     * Deregisters a base DN with this registry.
     *
     * @param  baseDN to deregister
     * @return list of error messages generated by deregistering the base DN
     *         that should be logged if the changes to this registry are
     *         committed to the server
     * @throws DirectoryException if the base DN could not be deregistered
     */
     List<LocalizableMessage> deregisterBaseDN(DN baseDN)
           throws DirectoryException
    {
      ifNull(baseDN);

      // Make sure that the Directory Server actually contains a backend with
      // the specified base DN.
      LocalBackend<?> backend = baseDNs.get(baseDN);
      if (backend == null)
      {
        LocalizableMessage message =
            ERR_DEREGISTER_BASEDN_NOT_REGISTERED.get(baseDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      // Check to see if the backend has a parent backend, and whether it has
      // any subordinates with base DNs that are below the base DN to remove.
      LocalBackend<?> superiorBackend = backend.getParentBackend();
      LinkedList<LocalBackend<?>> subordinateBackends = new LinkedList<>();
      if (backend.getSubordinateBackends() != null)
      {
        for (LocalBackend<?> b : backend.getSubordinateBackends())
        {
          for (DN dn : b.getBaseDNs())
          {
            if (dn.isSubordinateOrEqualTo(baseDN))
            {
              subordinateBackends.add(b);
              break;
            }
          }
        }
      }

      // See if there are any other base DNs registered within the same backend.
      LinkedList<DN> otherBaseDNs = new LinkedList<>();
      for (DN dn : baseDNs.keySet())
      {
        if (dn.equals(baseDN))
        {
          continue;
        }

        LocalBackend<?> b = baseDNs.get(dn);
        if (backend.equals(b))
        {
          otherBaseDNs.add(dn);
        }
      }

      // If we've gotten here, then it's OK to make the changes.

      // Get rid of the references to this base DN in the mapping tree
      // information.
      baseDNs.remove(baseDN);
      publicNamingContexts.remove(baseDN);
      allPublicNamingContexts.remove(baseDN);
      privateNamingContexts.remove(baseDN);

      final LinkedList<LocalizableMessage> errors = new LinkedList<>();
      if (superiorBackend == null)
      {
        // If there were any subordinate backends, then all of their base DNs
        // will now be promoted to naming contexts.
        for (LocalBackend<?> b : subordinateBackends)
        {
          if (!testOnly)
          {
            b.setParentBackend(null);
            backend.removeSubordinateBackend(b);
          }

          for (DN dn : b.getBaseDNs())
          {
            if (b.isPrivateBackend())
            {
              privateNamingContexts.put(dn, b);
            }
            else
            {
              publicNamingContexts.put(dn, b);
            }
          }
        }
      }
      else
      {
        // If there are no other base DNs for the associated backend, then
        // remove this backend as a subordinate of the parent backend.
        if (otherBaseDNs.isEmpty() && !testOnly)
        {
          superiorBackend.removeSubordinateBackend(backend);
        }

        // If there are any subordinate backends, then they need to be made
        // subordinate to the parent backend.  Also, we should log a warning
        // message indicating that there may be inconsistent search results
        // because some of the structural entries will be missing.
        if (! subordinateBackends.isEmpty())
        {
          // Suppress this warning message on server shutdown.
          if (!DirectoryServer.getInstance().isShuttingDown()) {
            errors.add(WARN_DEREGISTER_BASEDN_MISSING_HIERARCHY.get(
                baseDN, backend.getBackendID()));
          }

          if (!testOnly)
          {
            for (LocalBackend<?> b : subordinateBackends)
            {
              backend.removeSubordinateBackend(b);
              superiorBackend.addSubordinateBackend(b);
              b.setParentBackend(superiorBackend);
            }
          }
        }
      }
      return errors;
    }

    /** Creates a default instance. */
    BaseDnRegistry()
    {
      this(false);
    }

    /**
     * Returns a copy of this registry.
     *
     * @return copy of this registry
     */
    BaseDnRegistry copy()
    {
      final BaseDnRegistry registry = new BaseDnRegistry(true);
      registry.baseDNs.putAll(baseDNs);
      registry.publicNamingContexts.putAll(publicNamingContexts);
      registry.allPublicNamingContexts.putAll(allPublicNamingContexts);
      registry.privateNamingContexts.putAll(privateNamingContexts);
      return registry;
    }

    /**
     * Creates a parameterized instance.
     *
     * @param testOnly indicates whether this registry will be used for testing;
     *        when <code>true</code> this registry will not modify backends
     */
    private BaseDnRegistry(boolean testOnly)
    {
      this.testOnly = testOnly;
    }

    /**
     * Gets the mapping of registered base DNs to their associated backend.
     *
     * @return mapping from base DN to backend
     */
    Map<DN, LocalBackend<?>> getBaseDnMap()
    {
      return this.baseDNs;
    }

    /**
     * Gets the mapping of registered public naming contexts, not including
     * sub-suffixes, to their associated backend.
     *
     * @return mapping from naming context to backend
     */
    Map<DN, LocalBackend<?>> getPublicNamingContextsMap()
    {
      return this.publicNamingContexts;
    }

    /**
     * Gets the mapping of registered public naming contexts, including sub-suffixes,
     * to their associated backend.
     *
     * @return mapping from naming context to backend
     */
    Map<DN, LocalBackend<?>> getAllPublicNamingContextsMap()
    {
      return this.allPublicNamingContexts;
    }

    /**
     * Gets the mapping of registered private naming contexts to their
     * associated backend.
     *
     * @return mapping from naming context to backend
     */
    Map<DN, LocalBackend<?>> getPrivateNamingContextsMap()
    {
      return this.privateNamingContexts;
    }

    /**
     * Indicates whether the specified DN is contained in this registry as
     * a naming contexts.
     *
     * @param  dn  The DN for which to make the determination.
     *
     * @return  {@code true} if the specified DN is a naming context in this
     *          registry, or {@code false} if it is not.
     */
    boolean containsNamingContext(DN dn)
    {
      return privateNamingContexts.containsKey(dn) || publicNamingContexts.containsKey(dn);
    }

    /** Clear and nullify this registry's internal state. */
    void clear() {
      baseDNs.clear();
      privateNamingContexts.clear();
      publicNamingContexts.clear();
      allPublicNamingContexts.clear();
    }
  }

  /**
   * Holder for a backend and a single base DN managed by the backend.
   * <p>
   * A backend can manages multiple base DNs, this class allow to keep the association for a single DN.
   */
  public static class BackendAndName
  {
    private final LocalBackend<?> backend;
    private final DN baseDn;

    /**
     * Creates a new holder for base DN and the backend that manages it.
     * @param backend
     *          The backend that holds the base DN
     * @param baseDn
     *          A base DN of the backend
     */
    public BackendAndName(LocalBackend<?> backend, DN baseDn)
    {
      this.backend = backend;
      this.baseDn = baseDn;
    }

    /**
     * Returns the base DN.
     *
     * @return the base DN
     */
    public DN getBaseDn()
    {
      return baseDn;
    }

    /**
     * Returns the backend.
     *
     * @return the backend that holds the base DN
     */
    public LocalBackend<?> getBackend()
    {
      return backend;
    }
  }
}
