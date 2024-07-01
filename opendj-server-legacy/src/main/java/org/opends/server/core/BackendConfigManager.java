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
import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.BackendConfigManager.NamingContextFilter.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageDescriptor.Arg2;
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
import org.forgerock.util.Reject;
import org.opends.server.api.Backend;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackendInitializationListener;
import org.opends.server.backends.ConfigurationBackend;
import org.opends.server.backends.RootDSEBackend;
import org.opends.server.config.ConfigConstants;
import org.opends.server.monitors.LocalBackendMonitor;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.WritabilityMode;

import com.forgerock.opendj.util.Iterables;
import com.forgerock.opendj.util.Predicate;

/**
 * Responsible for managing the lifecycle of backends in the Directory Server.
 * <p>
 * It performs the necessary initialization of the backends when the server is first
 * started, and then manages any changes to them while the server is running.
 */
public class BackendConfigManager implements
     ConfigurationChangeListener<BackendCfg>,
     ConfigurationAddListener<BackendCfg>,
     ConfigurationDeleteListener<BackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The mapping between backends IDs and local backend implementations. */
  private final Map<String, LocalBackend<?>> localBackendsById = new ConcurrentHashMap<>();

  /** The mapping between backend configuration names and backend implementations. */
  private final Map<DN, Backend<? extends BackendCfg>> configuredBackends = new ConcurrentHashMap<>();

  /** The set of initialization listeners restricted to local backends. */
  private final Set<LocalBackendInitializationListener> localInitializationListeners = new CopyOnWriteArraySet<>();

  /** Contains all relationships between the base DNs and the backends (at the exclusion of RootDSE backend). */
  private volatile Registry registry = new Registry();

  /** The Root DSE backend, which is managed separately from other backends. */
  private RootDSEBackend rootDSEBackend;

  /** Lock for updates: add, change and delete operations on backends. */
  private final ReentrantLock writeLock = new ReentrantLock();

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
    final ConfigurationBackend configBackend = new ConfigurationBackend(serverContext);
    initializeBackend(configBackend, configBackend.getBackendCfg());

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
    initializeRootDSEBackend();
  }

  private void initializeRootDSEBackend() throws InitializationException, ConfigException
  {
    RootDSEBackendCfg rootDSECfg;
    try
    {
      rootDSECfg = serverContext.getRootConfig().getRootDSEBackend();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY.get(stackTraceToSingleLineString(e)), e);
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
    writeLock.lock();
    try
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
    finally
    {
      writeLock.unlock();
    }
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

    if (acquireSharedLock(backend, backendCfg.getBackendId(), ccr)
        && configureAndOpenBackend(backend, backendCfg, ccr))
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

  private void releaseSharedLock(Arg2<Object, Object> errorMessage, Backend<?> backend, String backendID)
  {
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        logger.warn(errorMessage, backendID, failureReason);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.warn(errorMessage, backendID, stackTraceToSingleLineString(e));
    }
  }

  /**
   * Returns the set of all backends.
   *
   * @return all backends
   */
  public Set<Backend<?>> getAllBackends()
  {
    return new HashSet<Backend<?>>(registry.backendsByName.values());
  }

  /**
   * Returns the set of local backends.
   *
   * @return the local backends
   */
  public Set<LocalBackend<?>> getLocalBackends()
  {
    return new HashSet<LocalBackend<?>>(registry.localBackendsByName.values());
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
    if (baseDN.isRootDN())
    {
      return rootDSEBackend;
    }
    return registry.localBackendsByName.get(baseDN);
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
   * Retrieves the DN that is the immediate parent for this DN. This method does take the server's
   * naming context configuration into account, so if the current DN is a naming context for the
   * server, then it will not be considered to have a parent.
   *
   * @param dn
   *          the
   * @return The DN that is the immediate parent for this DN, or {@code null} if this DN does not
   *         have a parent (either because there is only a single RDN component or because this DN
   *         is a suffix defined in the server).
   */
  public DN getParentDNInSuffix(DN dn)
  {
    if (dn.size() <= 1 || containsLocalNamingContext(dn))
    {
      return null;
    }
    return dn.parent();
  }

  /**
   * Retrieves the backend that should be used to handle operations on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding backend.
   * @return The backend or {@code null} if no appropriate backend
   *         is registered with the server.
   */
  public Backend<?> findBackendForEntry(final DN entryDN)
  {
    if (entryDN.isRootDN())
    {
      return rootDSEBackend;
    }
    Registry reg = registry;
    DN baseDN = reg.findNamingContextForEntry(entryDN);
    return baseDN != null ? reg.backendsByName.get(baseDN) : null;
  }

  /**
   * Retrieves the naming context that should be used to handle operations
   * on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding naming context.
   * @return The naming context or {@code null} if no appropriate naming context
   *         is registered with the server.
   */
  public DN findNamingContextForEntry(final DN entryDN)
  {
    Registry reg = registry;
    return reg.findNamingContextForEntry(entryDN);
  }

  /**
   * Retrieves the local backend and the corresponding baseDN that should be used to handle operations
   * on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding backend.
   * @return The local backend with its matching base DN or {@code null} if no appropriate local backend
   *         is registered with the server.
   */
  public LocalBackend<?> findLocalBackendForEntry(DN entryDN)
  {
    Backend<?> backend = findBackendForEntry(entryDN);
    return backend != null && backend instanceof LocalBackend ? (LocalBackend<?>)backend : null;
  }

  /**
   * Retrieves a local backend provided its identifier.
   *
   * @param backendId
   *          Identifier of the backend
   * @return the local backend, or {@code null} if there is no local backend registered with the
   *            specified id.
   */
  public LocalBackend<?> getLocalBackendById(String backendId)
  {
    return localBackendsById.get(backendId);
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
    return localBackendsById.containsKey(backendID);
  }

  /**
   * Retrieves the set of subordinate backends of the provided backend.
   *
   * @param backend
   *          The backend for which to retrieve the subordinates backends.
   * @return The set of subordinates backends, which is never {@code null}
   */
  public Set<Backend<?>> getSubordinateBackends(Backend<?> backend)
  {
    Registry reg = registry;
    final Set<Backend<?>> subs = new HashSet<>();
    for (DN baseDN : backend.getBaseDNs())
    {
      for (Backend<?> b : reg.getSubordinateBackends(baseDN))
      {
        subs.add(b);
      }
    }
    return subs;
  }

  /**
   * Retrieves the set of local naming contexts that are subordinates of the naming context
   * that should be used to handle operations on the specified entry.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding naming context.
   * @return The naming context or {@code null} if no appropriate naming context
   *         is registered with the server.
   */
  public Set<DN> findSubordinateLocalNamingContextsForEntry(DN entryDN)
  {
    Registry reg = registry;
    DN baseDN = findNamingContextForEntry(entryDN);
    if (baseDN == null)
    {
      return null;
    }
    return reg.getSubordinateLocalNamingContexts(baseDN);
  }

  /**
   * Retrieves the set of local naming contexts that are subordinates of the naming context
   * that should be used to handle search operation on the specified entry.
   * <p>
   * The global option that restricts the subordinate DNs to search (when searching "") may
   * apply if the provided set is not empty.
   *
   * @param entryDN
   *          The DN of the entry for which to retrieve the corresponding naming context.
   * @return The naming context or {@code null} if no appropriate naming context
   *         is registered with the server.
   */
  public Set<DN> findSubordinateLocalNamingContextsToSearchForEntry(DN entryDN)
  {
    // trigger the special behavior when searching "" if needed
    if (entryDN.isRootDN())
    {
      Set<DN> subordinateBaseDNs = serverContext.getCoreConfigManager().getSubordinateBaseDNs();
      if (!subordinateBaseDNs.isEmpty())
      {
        return subordinateBaseDNs;
      }
    }
    // usual case
    return findSubordinateLocalNamingContextsForEntry(entryDN);
  }

  /**
   * Retrieves naming contexts corresponding to backends, filtered with the combination of
   * all provided filters.
   *
   * @param filters
   *            filter the naming contexts
   * @see NamingContext
   * @return the DNs of naming contexts for which all the filters apply.
   */
  public Set<DN> getNamingContexts(final NamingContextFilter... filters)
  {
    Registry reg = registry;
    return reg.getNamingContexts(filters);
  }

  /**
   * Indicates whether the specified DN is contained in the local backends as
   * a naming context.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  {@code true} if the specified DN is a naming context in one
   *          backend, or {@code false} if it is not.
   */
  public boolean containsLocalNamingContext(DN dn)
  {
    Registry reg = registry;
    return reg.containsLocalNamingContext(dn);
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
  public void registerBaseDN(DN baseDN, Backend<? extends BackendCfg> backend, boolean isPrivate)
      throws DirectoryException
  {
    writeLock.lock();
    try
    {
      Registry newRegister = registry.copy();
      newRegister.registerBaseDN(baseDN, backend, isPrivate);
      registry = newRegister;
    }
    finally
    {
      writeLock.unlock();
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
    Reject.ifNull(backend);
    String backendID = backend.getBackendID();
    Reject.ifNull(backendID);

    writeLock.lock();
    try
    {
      if (localBackendsById.containsKey(backendID))
      {
        LocalizableMessage message = ERR_REGISTER_BACKEND_ALREADY_EXISTS.get(backendID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      localBackendsById.put(backendID, backend);

      LocalBackendMonitor monitor = new LocalBackendMonitor(backend);
      monitor.initializeMonitorProvider(null);
      backend.setBackendMonitor(monitor);
      registerMonitorProvider(monitor);
    }
    finally
    {
      writeLock.unlock();
    }
  }

  /**
   * Registers a local backend initialization listener.
   *
   * @param  listener  The listener to register.
   */
  public void registerLocalBackendInitializationListener(LocalBackendInitializationListener listener)
  {
    localInitializationListeners.add(listener);
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
    writeLock.lock();
    try
    {
      Registry newRegister = registry.copy();
      newRegister.deregisterBaseDN(baseDN);
      registry = newRegister;
    }
    finally
    {
      writeLock.unlock();
    }
  }

  /**
   * Deregisters a local backend initialization listener.
   *
   * @param  listener  The listener to deregister.
   */
  public void deregisterLocalBackendInitializationListener(LocalBackendInitializationListener listener)
  {
    localInitializationListeners.remove(listener);
  }

  /**
   * Deregisters a local backend.
   *
   * @param backend
   *          The backend to deregister with the server. It must not be {@code null}.
   */
  public void deregisterLocalBackend(LocalBackend<?> backend)
  {
    Reject.ifNull(backend);
    writeLock.lock();
    try
    {
      localBackendsById.remove(backend.getBackendID());
      LocalBackendMonitor monitor = backend.getBackendMonitor();
      if (monitor != null)
      {
        deregisterMonitorProvider(monitor);
        monitor.finalizeMonitorProvider();
        backend.setBackendMonitor(null);
      }
    }
    finally
    {
      writeLock.unlock();
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(BackendCfg configEntry, List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();
    Set<DN> baseDNs = configEntry.getBaseDN();

    Backend<? extends BackendCfg> backend = configuredBackends.get(backendDN);
    if (backend != null)
    {
      Set<DN> removedDNs = new LinkedHashSet<>(backend.getBaseDNs());
      Set<DN> addedDNs = new LinkedHashSet<>(baseDNs);
      Iterator<DN> iterator = removedDNs.iterator();
      while (iterator.hasNext())
      {
        DN dn = iterator.next();
        if (addedDNs.remove(dn))
        {
          iterator.remove();
        }
      }

      // Copy the registry and perform the requested changes to see if it complains.
      Registry registryCopy = registry.copyForCheckingChanges();
      for (DN dn : removedDNs)
      {
        try
        {
          registryCopy.deregisterBaseDN(dn);
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
          registryCopy.registerBaseDN(dn, backend, false);
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
      // Backend is added
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
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(BackendCfg cfg)
  {
    DN backendDN = cfg.dn();
    final ConfigChangeResult ccr = new ConfigChangeResult();
    writeLock.lock();
    try
    {
      Backend<? extends BackendCfg> backend = configuredBackends.get(backendDN);

      boolean needToEnable = false;
      try
      {
        if (cfg.isEnabled())
        {
          if (backend == null)
          {
            needToEnable = true;
          }
        }
        else
        {
          if (backend != null)
          {
            deregisterBackend(backendDN, backend);
            backend.finalizeBackend();
            releaseSharedLock(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend, backend.getBackendID());
            return ccr;
          }
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(ERR_CONFIG_BACKEND_UNABLE_TO_DETERMINE_ENABLED_STATE.get(backendDN,
            stackTraceToSingleLineString(e)));
        return ccr;
      }

      String className = cfg.getJavaClass();
      if (backend != null && !className.equals(backend.getClass().getName()))
      {
        // Need to check if it is a valid backend implementation.
        try
        {
          Class<?> backendClass = DirectoryServer.loadClass(className);
          if (LocalBackend.class.isAssignableFrom(backendClass))
          {
            ccr.addMessage(NOTE_CONFIG_BACKEND_ACTION_REQUIRED_TO_CHANGE_CLASS.get(
                backendDN, backend.getClass().getName(), className));
            ccr.setAdminActionRequired(true);
          }
          else
          {
            ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            ccr.addMessage(ERR_CONFIG_BACKEND_CLASS_NOT_BACKEND.get(className, backendDN));
          }
          return ccr;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
          ccr.addMessage(ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(
                  className, backendDN, stackTraceToSingleLineString(e)));
          return ccr;
        }
      }

      if (needToEnable)
      {
        try
        {
          backend = loadBackendClass(className).newInstance();
        }
        catch (Exception e)
        {
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
    }
    finally
    {
      writeLock.unlock();
    }
    return ccr;
  }

  private boolean registerBackend(Backend<? extends BackendCfg> backend, BackendCfg backendCfg, ConfigChangeResult ccr)
  {
    if (backend instanceof LocalBackend<?>)
    {
      LocalBackend<?> localBackend = (LocalBackend<?>) backend;
      for (LocalBackendInitializationListener listener : localInitializationListeners)
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
        ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        ccr.addMessage(message);
        return false;
      }

      for (LocalBackendInitializationListener listener : localInitializationListeners)
      {
        listener.performBackendPostInitializationProcessing(localBackend);
      }

      configuredBackends.put(backendCfg.dn(), backend);
      return true;
    }
    throw new RuntimeException("registerBackend() is not yet supported for proxy backend.");
  }

  @Override
  public boolean isConfigurationAddAcceptable(
       BackendCfg configEntry,
       List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();
    String backendID = configEntry.getBackendId();
    if (hasLocalBackend(backendID))
    {
      unacceptableReason.add(WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID.get(backendDN, backendID));
      return false;
    }

    String className = configEntry.getJavaClass();
    Backend<BackendCfg> backend;
    try
    {
      backend = loadBackendClass(className).newInstance();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      unacceptableReason.add(
          ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(className, backendDN, stackTraceToSingleLineString(e)));
      return false;
    }

    // Copy the registry and perform the requested changes to see if it complains.
    Registry registryCopy = registry.copyForCheckingChanges();
    for (DN baseDN : configEntry.getBaseDN())
    {
      if (baseDN.isRootDN())
      {
        unacceptableReason.add(ERR_CONFIG_BACKEND_BASE_IS_EMPTY.get(backendDN));
        return false;
      }
      try
      {
        registryCopy.registerBaseDN(baseDN, backend, false);
      }
      catch (DirectoryException de)
      {
        unacceptableReason.add(de.getMessageObject());
        return false;
      }
    }

    return backend.isConfigurationAcceptable(configEntry, unacceptableReason, serverContext);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(BackendCfg cfg)
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();
    writeLock.lock();
    try
    {
      DN backendDN = cfg.dn();
      cfg.addChangeListener(this);

      if (!cfg.isEnabled())
      {
        LocalizableMessage message = INFO_CONFIG_BACKEND_DISABLED.get(backendDN);
        logger.debug(message);
        changeResult.addMessage(message);
        return changeResult;
      }

      String backendID = cfg.getBackendId();
      if (hasLocalBackend(backendID))
      {
        LocalizableMessage message = WARN_CONFIG_BACKEND_DUPLICATE_BACKEND_ID.get(backendDN, backendID);
        logger.warn(message);
        changeResult.addMessage(message);
        return changeResult;
      }

      String className = cfg.getJavaClass();
      Backend<? extends BackendCfg> backend;
      try
      {
        backend = loadBackendClass(className).newInstance();
      }
      catch (Exception e)
      {
        logger.traceException(e);
        changeResult.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
        changeResult.addMessage(
            ERR_CONFIG_BACKEND_CANNOT_INSTANTIATE.get(className, backendDN, stackTraceToSingleLineString(e)));
        return changeResult;
      }

      initializeBackend(backend, cfg, changeResult);
    }
    finally
    {
      writeLock.unlock();
    }
    return changeResult;
  }

  private boolean configureAndOpenBackend(Backend<?> backend, BackendCfg cfg, ConfigChangeResult changeResult)
  {
    try
    {
      configureAndOpenBackend(backend, cfg);
      return true;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      changeResult.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      changeResult.addMessage(
          ERR_CONFIG_BACKEND_CANNOT_INITIALIZE.get(cfg.getJavaClass(), cfg.dn(), stackTraceToSingleLineString(e)));
      releaseSharedLock(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend, cfg.getBackendId());
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
  public boolean isConfigurationDeleteAcceptable(BackendCfg configEntry, List<LocalizableMessage> unacceptableReason)
  {
    DN backendDN = configEntry.dn();
    Backend<?> backend = configuredBackends.get(backendDN);
    if (backend == null)
    {
      return true;
    }
    for (DN baseDN : backend.getBaseDNs())
    {
      if (registry.subordinates.containsKey(baseDN))
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
    final ConfigChangeResult changeResult = new ConfigChangeResult();
    writeLock.lock();
    try
    {
      DN backendDN = configEntry.dn();
      Backend<?> backend = configuredBackends.get(backendDN);
      if (backend == null)
      {
        return changeResult;
      }
      for (DN baseDN : backend.getBaseDNs())
      {
        if (registry.subordinates.containsKey(baseDN))
        {
          changeResult.setResultCode(UNWILLING_TO_PERFORM);
          changeResult.addMessage(NOTE_CONFIG_BACKEND_CANNOT_REMOVE_BACKEND_WITH_SUBORDINATES.get(backendDN));
          return changeResult;
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
      releaseSharedLock(WARN_CONFIG_BACKEND_CANNOT_RELEASE_SHARED_LOCK, backend, backend.getBackendID());
    }
    finally
    {
      writeLock.unlock();
    }
    return changeResult;
  }

  private void deregisterBackend(DN backendDN, Backend<?> backend)
  {
    boolean isLocalBackend = backend instanceof LocalBackend<?>;
    LocalBackend<?> localBackend = isLocalBackend ? (LocalBackend<?>) backend : null;
    if (isLocalBackend)
    {
      for (LocalBackendInitializationListener listener : localInitializationListeners)
      {
        listener.performBackendPreFinalizationProcessing(localBackend);
      }
    }

    configuredBackends.remove(backendDN);

    if (isLocalBackend)
    {
      deregisterLocalBackend(localBackend);
      for (LocalBackendInitializationListener listener : localInitializationListeners)
      {
        listener.performBackendPostFinalizationProcessing(localBackend);
      }
    }
    else
    {
      throw new RuntimeException("Proxy backend deregistration not implemented yet");
    }
  }

  /** Shutdown all local backends. */
  public void shutdownLocalBackends()
  {
    writeLock.lock();
    try
    {
      for (LocalBackend<?> backend : localBackendsById.values())
      {
        try
        {
          for (LocalBackendInitializationListener listener : localInitializationListeners)
          {
            listener.performBackendPreFinalizationProcessing(backend);
          }

          backend.finalizeBackend();

          for (LocalBackendInitializationListener listener : localInitializationListeners)
          {
            listener.performBackendPostFinalizationProcessing(backend);
          }

          releaseSharedLock(WARN_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK, backend, backend.getBackendID());
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
    finally
    {
      writeLock.unlock();
    }
  }

  /**
   * The registry maintains the relationships between the base DNs and the backends.
   * <p>
   * Implementation note: a separate map is kept for local backends in order to avoid filtering the backends map
   * each time local backends are requested.
   * <p>
   */
  private static class Registry
  {
    /**
     * The mapping between base DNs and their corresponding local backends.
     * It is a subset of backendsByName field.
     */
    final Map<DN, LocalBackend<? extends LocalBackendCfg>> localBackendsByName = new HashMap<>();
    /**
     * The mapping between base DNs and their corresponding backends.
     * <p>
     * Note that the pair (root DN, RootDSEBackend) is not included in this mapping.
     */
    final Map<DN, Backend<? extends BackendCfg>> backendsByName = new HashMap<>();
    /**
     * The map of subordinates relationships between base DNs, which provides for each base DN
     * the set of its subordinates.
     * <p>
     * A base DN with no subordinates does not appear in this map.
     * Note that the root DN ("") is not included in this mapping.
     */
    final Map<DN, Set<DN>> subordinates = new HashMap<>();

    /** The naming contexts, including sub-suffixes. */
    final Set<NamingContext> namingContexts = new HashSet<>();

    Registry copy()
    {
      return copy(true);
    }

    Registry copyForCheckingChanges()
    {
      return copy(false);
    }

    Set<DN> getNamingContexts(final NamingContextFilter... filters)
    {
      Predicate<NamingContext, Void> predicate = new Predicate<NamingContext, Void>()
      {
        @Override
        public boolean matches(NamingContext namingContext, Void p)
        {
          for (NamingContextFilter filter : filters)
          {
            if (!filter.matches(namingContext, p))
            {
              return false;
            }
          }
          return true;
        }
      };
      Set<DN> result = new HashSet<>();
      for (NamingContext namingContext : Iterables.filteredIterable(namingContexts, predicate))
      {
        result.add(namingContext.getBaseDN());
      }
      return result;
    }

    boolean containsLocalNamingContext(DN dn)
    {
      if (localBackendsByName.containsKey(dn))
      {
        for (NamingContext name : namingContexts)
        {
          if (name.getBaseDN().equals(dn))
          {
            return name.isLocal && !name.isSubSuffix;
          }
        }
      }
      return false;
    }

    private Registry copy(boolean isUpdate)
    {
      Registry registry = isUpdate ? new Registry() : new CheckingRegistry();
      registry.localBackendsByName.putAll(localBackendsByName);
      registry.backendsByName.putAll(backendsByName);
      for (Map.Entry<DN, Set<DN>> entry : subordinates.entrySet())
      {
        registry.subordinates.put(entry.getKey(), new HashSet<>(entry.getValue()));
      }
      registry.namingContexts.addAll(namingContexts);
      return registry;
    }

    void registerBaseDN(DN baseDN, Backend<? extends BackendCfg> backend, boolean isPrivate) throws DirectoryException
    {
      // Check to see if the base DN is already registered with the server.
      Backend<?> existingBackend = backendsByName.get(baseDN);
      if (existingBackend != null)
      {
        LocalizableMessage message =
            ERR_REGISTER_BASEDN_ALREADY_EXISTS.get(baseDN, backend.getBackendID(), existingBackend.getBackendID());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      // Check to see if the backend is already registered with the server for
      // any other base DN(s). The new base DN must not have any hierarchical
      // relationship with any other base Dns for the same backend.
      List<DN> otherBackendBaseDNs = new ArrayList<>();
      for (Map.Entry<DN, Backend<? extends BackendCfg>> entry : backendsByName.entrySet())
      {
        Backend<?> b = entry.getValue();
        if (b.equals(backend))
        {
          DN dn = entry.getKey();
          otherBackendBaseDNs.add(dn);
          if (baseDN.isSuperiorOrEqualTo(dn) || baseDN.isSubordinateOrEqualTo(dn))
          {
            LocalizableMessage message = ERR_REGISTER_BASEDN_HIERARCHY_CONFLICT.get(baseDN, backend.getBackendID(), dn);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
          }
        }
      }

      // Check to see if the new base DN is subordinate to any other base DN
      // already defined. If it is, then any other base DN(s) for the same
      // backend must also be subordinate to the same base DN.
      final DN parentBaseDN = retrieveParentBackend(backend.getBackendID(), baseDN, otherBackendBaseDNs);
      Backend<?> parentBackend = null;
      if (parentBaseDN == null)
      {
        // check that other base DNs do no have a parent
        for (DN dn : otherBackendBaseDNs)
        {
          DN parentDn = retrieveParentSuffix(dn);
          if (parentDn != null)
          {
            String parentBackendId = backendsByName.get(parentDn).getBackendID();
            LocalizableMessage message =
                ERR_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE.get(baseDN, backend.getBackendID(), parentBackendId);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
          }
        }
      }
      else
      {
        parentBackend = backendsByName.get(parentBaseDN);
      }

      List<DN> subordinateBaseDNs = new LinkedList<>();
      for (Map.Entry<DN, Backend<? extends BackendCfg>> entry : backendsByName.entrySet())
      {
        DN dn = entry.getKey();
        DN parentDN = dn.parent();
        while (parentDN != null)
        {
          if (parentDN.equals(baseDN))
          {
            subordinateBaseDNs.add(dn);
            break;
          }
          else if (backendsByName.containsKey(parentDN))
          {
            break;
          }
          parentDN = parentDN.parent();
        }
      }

      // If we've gotten here, then the new base DN is acceptable. If we should
      // actually apply the changes then do so now.
      checkEntryInMultipleBackends(baseDN, backend, parentBackend);

      backendsByName.put(baseDN, backend);
      if (backend instanceof LocalBackend<?>)
      {
        LocalBackend<? extends LocalBackendCfg> localBackend = (LocalBackend<? extends LocalBackendCfg>) backend;
        localBackendsByName.put(baseDN, localBackend);
        setPrivateBackend(isPrivate, parentBackend, localBackend);
      }

      namingContexts.add(new NamingContext(baseDN, isPrivate, (parentBackend!=null), backend instanceof LocalBackend));
      if (parentBaseDN != null)
      {
        addSubordinateDn(parentBaseDN, baseDN);
        for (DN subordinateDN : subordinateBaseDNs)
        {
          removeSubordinateDn(parentBaseDN, subordinateDN);
        }
      }
      for (DN subDN : subordinateBaseDNs)
      {
        addSubordinateDn(baseDN, subDN);
        switchNamingContextIsSubSuffix(subDN);
      }
    }

    void setPrivateBackend(boolean isPrivate, final Backend<?> parentBackend,
        LocalBackend<? extends LocalBackendCfg> localBackend)
    {
      if (parentBackend == null)
      {
        localBackend.setPrivateBackend(isPrivate);
      }
    }

    void checkEntryInMultipleBackends(DN baseDN, Backend<? extends BackendCfg> backend,
        final Backend<?> parentBackend) throws DirectoryException
    {
      // Check to see if any of the registered backends already contain an
      // entry with the DN specified as the base DN. This could happen if
      // we're creating a new subordinate backend in an existing directory
      // (e.g., moving the "ou=People,dc=example,dc=com" branch to its own
      // backend when that data already exists under the "dc=example,dc=com"
      // backend). This condition shouldn't prevent the new base DN from
      // being registered, but it's definitely important enough that we let
      // the administrator know about it and remind them that the existing
      // backend will need to be reinitialized.
      if (parentBackend != null)
      {
        if (entryExistsInBackend(baseDN, parentBackend))
        {
          logger.error(WARN_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS.get(
              parentBackend.getBackendID(), baseDN, backend.getBackendID()));
        }
      }
    }

    DN findNamingContextForEntry(final DN entryDN)
    {
      if (entryDN.isRootDN())
      {
        return entryDN;
      }
      /*
       * Try to minimize the number of lookups in the map to find the backend containing the entry.
       * 1) If the DN contains many RDNs it is faster to iterate through the list of registered backends,
       * 2) Otherwise iterating through the parents requires less lookups. It also avoids some attacks
       * where we would spend time going through the list of all parents to finally decide the baseDN
       * is absent.
       */
      if (entryDN.size() <= backendsByName.size())
      {
        DN matchedDN = entryDN;
        while (!matchedDN.isRootDN())
        {
          if (backendsByName.containsKey(matchedDN))
          {
            return matchedDN;
          }
          matchedDN = matchedDN.parent();
        }
        return null;
      }
      else
      {
        DN matchedDN = null;
        int currentSize = 0;
        for (DN backendDN : backendsByName.keySet())
        {
          if (entryDN.isSubordinateOrEqualTo(backendDN) && backendDN.size() > currentSize)
          {
            matchedDN = backendDN;
            currentSize = backendDN.size();
          }
        }
        return matchedDN;
      }
    }

    /** Returns the parent naming context for base DN, or {@code null} if there is no parent. */
    private DN retrieveParentBackend(String backendID, DN baseDN, List<DN> otherBackendBaseDNs)
        throws DirectoryException
    {
      DN parentDN = baseDN.parent();
      while (parentDN != null)
      {
        if (backendsByName.containsKey(parentDN))
        {
          break;
        }
        parentDN = parentDN.parent();
      }

      if (parentDN == null) {
        return null;
      }
      // check that other base DNs of the backend have the same subordinate
      for (DN dn : otherBackendBaseDNs)
      {
        if (!dn.isSubordinateOrEqualTo(parentDN))
        {
          LocalizableMessage msg = ERR_REGISTER_BASEDN_DIFFERENT_PARENT_BASES.get(baseDN, backendID, dn);
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
        }
      }
      return parentDN;
    }

    /** Returns the DN that has the provided DN as a subordinate, or {@code null} if there is no parent. */
    private DN retrieveParentSuffix(DN dn)
    {
      for (Map.Entry<DN, Set<DN>> entry : subordinates.entrySet())
      {
        Set<DN> subs = entry.getValue();
        if (subs.contains(dn))
        {
          return entry.getKey();
        }
      }
      return null;
    }

    private boolean entryExistsInBackend(DN dn, Backend<?> backend) throws DirectoryException
    {
      if (backend instanceof LocalBackend<?>)
      {
        return ((LocalBackend<?>) backend).entryExists(dn);
      }
      // TODO: assume entry exists in non-local backend, Is it correct ?
      return true;
    }

    private void addSubordinateDn(DN dn, DN subordinateDn)
    {
      Set<DN> subs = subordinates.get(dn);
      if (subs == null)
      {
        subs = new HashSet<>();
        subordinates.put(dn, subs);
      }
      subs.add(subordinateDn);
    }

    void deregisterBaseDN(DN baseDN) throws DirectoryException
    {
      Reject.ifNull(baseDN);

      Backend<?> backend = backendsByName.get(baseDN);
      if (backend == null)
      {
        LocalizableMessage message = ERR_DEREGISTER_BASEDN_NOT_REGISTERED.get(baseDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }

      backendsByName.remove(baseDN);
      if (backend instanceof LocalBackend<?>)
      {
        localBackendsByName.remove(baseDN);
      }
      removeNamingContext(baseDN);

      Set<DN> subordinatesDNs = getSubordinateNamingContexts(baseDN);
      subordinates.remove(baseDN);
      DN parentDN = retrieveParentSuffix(baseDN);
      if (parentDN == null)
      {
        // If there were any subordinate naming contexts,
        // they are all promoted to top-level naming contexts.
        for (DN dn : subordinatesDNs)
          {
            switchNamingContextIsSubSuffix(dn);
          }
      }
      else
      {
        removeSubordinateDn(parentDN, baseDN);

        // If there are any subordinate backends, then they need to be made
        // subordinate to the parent backend. Also, we should log a warning
        // message indicating that there may be inconsistent search results
        // because some of the structural entries will be missing.
        if (!subordinatesDNs.isEmpty())
        {
          checkMissingHierarchy(baseDN, backend);
          for (DN subDN : subordinatesDNs)
          {
            addSubordinateDn(parentDN, subDN);
          }
        }
      }
    }

    void checkMissingHierarchy(DN baseDN, Backend<?> backend)
    {
      if (!DirectoryServer.getInstance().isShuttingDown())
      {
        logger.error(WARN_DEREGISTER_BASEDN_MISSING_HIERARCHY.get(baseDN, backend.getBackendID()));
      }
    }

    Set<Backend<?>> getSubordinateBackends(DN baseDN)
    {
      final Set<Backend<?>> backends = new HashSet<>();
      if (baseDN.isRootDN())
      {
        for (DN dn : getNamingContexts(PUBLIC, TOP_LEVEL))
        {
          backends.add(backendsByName.get(dn));
        }
        return backends;
      }
      // general case
      Set<DN> subDNs = subordinates.get(baseDN);
      if (subDNs != null)
      {
        for (DN subDN : subDNs)
        {
          Backend<? extends BackendCfg> backend = backendsByName.get(subDN);
          if (backend != null)
          {
            // it is safe to add same backend several times because it is a set
            backends.add(backend);
          }
        }
      }
      return backends;
    }

    Set<DN> getSubordinateNamingContexts(DN baseDN)
    {
      final Set<DN> dns = new HashSet<>();
      if (baseDN.isRootDN())
      {
        for (DN dn : getNamingContexts(PUBLIC, TOP_LEVEL))
        {
          dns.add(dn);
        }
        return dns;
      }
      // general case
      Set<DN> subDNs = subordinates.get(baseDN);
      if (subDNs != null)
      {
        dns.addAll(subDNs);
      }
      return dns;
    }

    Set<DN> getSubordinateLocalNamingContexts(DN baseDN)
    {
      Set<DN> subs = new HashSet<>();
      for (DN subDN : getSubordinateNamingContexts(baseDN))
      {
        if (localBackendsByName.containsKey(subDN))
        {
          subs.add(subDN);
        }
      }
      return subs;
    }

    private void removeSubordinateDn(DN dn, DN subordinateDn)
    {
      if (subordinates.containsKey(dn))
      {
        Set<DN> subs = subordinates.get(dn);
        subs.remove(subordinateDn);
        if (subs.isEmpty())
        {
          subordinates.remove(dn);
        }
      }
    }

    private NamingContext removeNamingContext(DN baseDn)
    {
      for (Iterator<NamingContext> it = namingContexts.iterator(); it.hasNext();)
      {
        NamingContext nc = it.next();
        if (nc.getBaseDN().equals(baseDn))
        {
          it.remove();
          return nc;
        }
      }
      return null;
    }

    private void switchNamingContextIsSubSuffix(DN baseDn)
    {
      NamingContext nc = removeNamingContext(baseDn);
      namingContexts.add(new NamingContext(nc.getBaseDN(), nc.isPrivate(), !nc.isSubSuffix(), nc.isLocal()));
    }
  }

  /**
   * A registry to check that changes on baseDNs are acceptable.
   * <p>
   * This registry will only change its internal state. It will not log warning and will not change
   * state of backends.
   */
  private static class CheckingRegistry extends Registry
  {
    @Override
    void setPrivateBackend(boolean isPrivate, final Backend<?> parentBackend,
        LocalBackend<? extends LocalBackendCfg> localBackend)
    {
      // no-op
    }

    @Override
    void checkEntryInMultipleBackends(DN baseDN, Backend<? extends BackendCfg> backend,
        final Backend<?> parentBackend) throws DirectoryException
    {
      // no-op
    }

    @Override
    void checkMissingHierarchy(DN baseDN, Backend<?> backend)
    {
      // no-op
    }
  }

  /** Filter on naming context. */
  public enum NamingContextFilter implements com.forgerock.opendj.util.Predicate<NamingContext, Void>
  {
    /** a naming context corresponding to a private backend. */
    PRIVATE
    {
      @Override
      public boolean matches(NamingContext context, Void p)
      {
        return context.isPrivate();
      }
    },
    /** a public naming context (strict opposite of private). */
    PUBLIC
    {
      @Override
      public boolean matches(NamingContext context, Void p)
      {
        return !context.isPrivate();
      }
    },
    /** a top-level naming context, which is not a subordinate of another naming context. */
    TOP_LEVEL
    {
      @Override
      public boolean matches(NamingContext context, Void p)
      {
        return !context.isSubSuffix();
      }
    },
    /** a naming suffix corresponding to a local backend. */
    LOCAL
    {
      @Override
      public boolean matches(NamingContext context, Void p)
      {
        return context.isLocal();
      }
    }
  }

  /**
   * Represents a naming context, determined by its base DN.
   * <p>
   * A naming context may be private or public, top-level or sub-suffix, local or non-local.
   * <p>
   * This class provides predicates to be able to filter on a provided set of naming contexts.
   */
  public static class NamingContext
  {
    private final DN baseDN;
    private final boolean isPrivate;
    private final boolean isSubSuffix;
    private final boolean isLocal;

    NamingContext(DN baseDN, boolean isPrivate, boolean isSubSuffix, boolean isLocal)
    {
      this.baseDN = baseDN;
      this.isPrivate = isPrivate;
      this.isSubSuffix = isSubSuffix;
      this.isLocal = isLocal;
    }

    /**
     * Retrieves the base DN of the naming context.
     *
     * @return the baseDN
     */
    public DN getBaseDN()
    {
      return baseDN;
    }

    /**
     * Indicates whether this naming context corresponds to a private backend.
     *
     * @return {@code true} if naming context is private, {@code false} otherwise
     */
    public boolean isPrivate()
    {
      return isPrivate;
    }

    /**
     * Indicates whether this naming context corresponds to a sub-suffix (a non top-level naming context).
     *
     * @return {@code true} if naming context is a sub-suffix, {@code false} otherwise
     */
    public boolean isSubSuffix()
    {
      return isSubSuffix;
    }

    /**
     * Indicates whether this naming context corresponds to a local backend.
     *
     * @return {@code true} if naming context is local, {@code false} otherwise
     */
    public boolean isLocal()
    {
      return isLocal;
    }
  }
}
