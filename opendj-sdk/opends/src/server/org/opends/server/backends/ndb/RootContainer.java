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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb;
import com.mysql.cluster.ndbj.Ndb;
import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbApiTemporaryException;
import com.mysql.cluster.ndbj.NdbClusterConnection;
import org.opends.messages.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.opends.server.types.DN;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.opends.server.api.Backend;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.NdbBackendCfg;
import org.opends.server.config.ConfigException;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.messages.NdbMessages.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;

/**
 * Root container holds all the entry containers for each base DN.
 * It also maintains all the openings and closings of the entry
 * containers.
 */
public class RootContainer
     implements ConfigurationChangeListener<NdbBackendCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The backend configuration.
   */
  private NdbBackendCfg config;

  /**
   * The backend to which this entry root container belongs.
   */
  private Backend backend;

  /**
   * The base DNs contained in this entryContainer.
   */
  private ConcurrentHashMap<DN, EntryContainer> entryContainers;

  /**
   * NDB connection objects.
   */
  private static NdbClusterConnection[] ndbConns;

  /**
   * NDB handle objects.
   */
  private static LinkedBlockingQueue<Ndb> ndbQueue;

  /**
   * NDB thread count.
   */
  private int ndbThreadCount;

  /**
   * NDB number of connections.
   */
  private int ndbNumConnections;

  /**
   * The range to use when requesting next ID.
   */
  private static final long NDB_NEXTID_RANGE = 1000;

  /**
   * The maximum number of NDB threads.
   */
  private static final int NDB_MAX_THREAD_COUNT = 128;

  /**
   * Timeout for the first node/group to become ready.
   */
  private static final int NDB_TIMEOUT_FIRST_ALIVE = 60;

  /**
   * Timeout for the rest of nodes/groups to become ready.
   */
  private static final int NDB_TIMEOUT_AFTER_FIRST_ALIVE = 60;



  /**
   * Creates a new RootContainer object.
   *
   * @param config The configuration of the NDB backend.
   * @param backend A reference to the NDB backend that is creating this
   *                root container.
   */
  public RootContainer(Backend backend, NdbBackendCfg config)
  {
    this.entryContainers = new ConcurrentHashMap<DN, EntryContainer>();
    this.backend = backend;
    this.config = config;

    this.ndbNumConnections = this.config.getNdbNumConnections();
    this.ndbConns = new NdbClusterConnection[ndbNumConnections];

    this.ndbThreadCount = this.config.getNdbThreadCount();
    if (this.ndbThreadCount > NDB_MAX_THREAD_COUNT) {
      this.ndbThreadCount = NDB_MAX_THREAD_COUNT;
    }

    this.ndbQueue = new LinkedBlockingQueue<Ndb>(
      this.ndbThreadCount);

    config.addNdbChangeListener(this);
  }

  /**
   * Opens the root container using the NDB configuration object provided.
   *
   * @throws NdbApiException If an error occurs when opening.
   * @throws ConfigException If an configuration error occurs while opening.
   * @throws Exception If an unknown error occurs when opening.
   */
  public void open()
      throws NdbApiException, ConfigException, Exception
  {
    // Log a message indicating upcoming NDB connect.
    logError(NOTE_NDB_WAITING_FOR_CLUSTER.get());

    // Connect to the cluster.
    for (int i = 0; i < this.ndbNumConnections; i++) {
      try {
        this.ndbConns[i] = NdbClusterConnection.create(
          this.config.getNdbConnectString());
        this.ndbConns[i].connect(5, 3, true);
        this.ndbConns[i].waitUntilReady(NDB_TIMEOUT_FIRST_ALIVE,
          NDB_TIMEOUT_AFTER_FIRST_ALIVE);
      } catch (NdbApiTemporaryException e) {
        if (debugEnabled()) {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        // Retry.
        if (this.ndbConns[i] != null) {
          this.ndbConns[i].close();
          this.ndbConns[i] = null;
        }
        i--;
        continue;
      }
    }

    // Get NDB objects.
    int connsIndex = 0;
    for (int i = 0; i < this.ndbThreadCount; i++) {
      Ndb ndb = ndbConns[connsIndex].createNdb(
        BackendImpl.DATABASE_NAME, 1024);
      connsIndex++;
      if (connsIndex >= this.ndbNumConnections) {
        connsIndex = 0;
      }
      try {
        this.ndbQueue.put(ndb);
      } catch (Exception e) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        if (ndb != null) {
          ndb.close();
        }
      }
    }

    openAndRegisterEntryContainers(config.getBaseDN());
  }

  /**
   * Opens the entry container for a base DN. If the entry container does not
   * exist for the base DN, it will be created. The entry container will be
   * opened with the same mode as the root container. Any entry containers
   * opened in a read only root container will also be read only. Any entry
   * containers opened in a non transactional root container will also be non
   * transactional.
   *
   * @param baseDN The base DN of the entry container to open.
   * @return The opened entry container.
   * @throws NdbApiException If an error occurs while opening the entry
   *                           container.
   * @throws ConfigException If an configuration error occurs while opening
   *                         the entry container.
   */
  public EntryContainer openEntryContainer(DN baseDN)
      throws NdbApiException, ConfigException
  {
    String databasePrefix = baseDN.toNormalizedString();

    EntryContainer ec = new EntryContainer(baseDN, databasePrefix,
                                           backend, config, this);
    ec.open();
    return ec;
  }

  /**
   * Registeres the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to register.
   * @param entryContainer The entry container to register for the baseDN.
   * @throws Exception If an error occurs while registering the entry
   *                           container.
   */
  public void registerEntryContainer(DN baseDN,
                                     EntryContainer entryContainer)
      throws Exception
  {
    EntryContainer ec1 = this.entryContainers.get(baseDN);

    // If an entry container for this baseDN is already open we don't allow
    // another to be opened.
    if (ec1 != null)
      // FIXME: Should be NDBException instance.
      throw new Exception("An entry container named " +
          ec1.getDatabasePrefix() + " is alreadly registered for base DN " +
          baseDN.toString());

    this.entryContainers.put(baseDN, entryContainer);
  }

  /**
   * Opens the entry containers for multiple base DNs.
   *
   * @param baseDNs The base DNs of the entry containers to open.
   * @throws NdbApiException If an error occurs while opening the entry
   *                           container.
   * @throws ConfigException if a configuration error occurs while opening the
   *                         container.
   */
  private void openAndRegisterEntryContainers(Set<DN> baseDNs)
      throws NdbApiException, ConfigException, Exception
  {
    for(DN baseDN : baseDNs)
    {
      EntryContainer ec = openEntryContainer(baseDN);
      registerEntryContainer(baseDN, ec);
    }
  }

  /**
   * Unregisteres the entry container for a base DN.
   *
   * @param baseDN The base DN of the entry container to close.
   * @return The entry container that was unregistered or NULL if a entry
   * container for the base DN was not registered.
   */
  public EntryContainer unregisterEntryContainer(DN baseDN)
  {
    return entryContainers.remove(baseDN);

  }

  /**
   * Close the root entryContainer.
   *
   * @throws NdbApiException If an error occurs while attempting to close
   * the entryContainer.
   */
  public void close() throws NdbApiException
  {
    for(DN baseDN : entryContainers.keySet())
    {
      EntryContainer ec = unregisterEntryContainer(baseDN);
      ec.exclusiveLock.lock();
      try
      {
        ec.close();
      }
      finally
      {
        ec.exclusiveLock.unlock();
      }
    }

    while (!this.ndbQueue.isEmpty()) {
      Ndb ndb = null;
      try {
        ndb = this.ndbQueue.poll();
        if (ndb != null) {
          ndb.close();
        }
      } catch (Exception e) {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
    this.ndbQueue.clear();

    for (NdbClusterConnection ndbConn : ndbConns) {
      ndbConn.close();
    }

    config.removeNdbChangeListener(this);
  }

  /**
   * Get NDB handle from the queue.
   * @return NDB handle.
   */
  protected Ndb getNDB()
  {
    try {
      return ndbQueue.take();
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      return null;
    }
  }

  /**
   * Release NDB handle to the queue.
   * @param ndb handle to release.
   */
  protected void releaseNDB(Ndb ndb)
  {
    try {
      ndbQueue.put(ndb);
    } catch (Exception e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      if (ndb != null) {
        ndb.close();
      }
      return;
    }
  }

  /**
   * Return all the entry containers in this root container.
   *
   * @return The entry containers in this root container.
   */
  public Collection<EntryContainer> getEntryContainers()
  {
    return entryContainers.values();
  }

  /**
   * Returns all the baseDNs this root container stores.
   *
   * @return The set of DNs this root container stores.
   */
  public Set<DN> getBaseDNs()
  {
    return entryContainers.keySet();
  }

  /**
   * Return the entry container for a specific base DN.
   *
   * @param baseDN The base DN of the entry container to retrive.
   * @return The entry container for the base DN.
   */
  public EntryContainer getEntryContainer(DN baseDN)
  {
    EntryContainer ec = null;
    DN nodeDN = baseDN;

    while (ec == null && nodeDN != null)
    {
      ec = entryContainers.get(nodeDN);
      if (ec == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }

    return ec;
  }

  /**
   * Get the backend configuration used by this root container.
   *
   * @return The NDB backend configuration used by this root container.
   */
  public NdbBackendCfg getConfiguration()
  {
    return config;
  }

  /**
   * Get the total number of entries in this root container.
   *
   * @return The number of entries in this root container
   * @throws NdbApiException If an error occurs while retrieving the entry
   *                           count.
   */
  public long getEntryCount() throws NdbApiException
  {
    long entryCount = 0;
    for(EntryContainer ec : this.entryContainers.values())
    {
      ec.sharedLock.lock();
      try
      {
        entryCount += ec.getEntryCount();
      }
      finally
      {
        ec.sharedLock.unlock();
      }
    }

    return entryCount;
  }

  /**
   * Assign the next entry ID.
   * @param ndb Ndb handle.
   * @return The assigned entry ID.
   */
  public long getNextEntryID(Ndb ndb)
  {
    long eid = 0;
    try
    {
      eid = ndb.getAutoIncrementValue(BackendImpl.NEXTID_TABLE,
        NDB_NEXTID_RANGE);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    return eid;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      NdbBackendCfg cfg,
      List<Message> unacceptableReasons)
  {
    boolean acceptable = true;

    return acceptable;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(NdbBackendCfg cfg)
  {
    ConfigChangeResult ccr;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    ccr = new ConfigChangeResult(ResultCode.SUCCESS, adminActionRequired,
                                 messages);
    return ccr;
  }
}
