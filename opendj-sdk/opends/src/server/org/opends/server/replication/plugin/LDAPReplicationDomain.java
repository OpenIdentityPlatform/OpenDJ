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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.replication.plugin.EntryHistorical.*;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.Severity;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.*;
import org.opends.server.admin.std.server.ExternalChangelogDomainCfg;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.task.Task;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.*;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.replication.service.ReplicationMonitor;
import org.opends.server.tasks.PurgeConflictsHistoricalTask;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.TimeThread;
import org.opends.server.workflowelement.externalchangelog.ECLWorkflowElement;
import org.opends.server.workflowelement.localbackend.*;

/**
 *  This class implements the bulk part of the Directory Server side
 *  of the replication code.
 *  It contains the root method for publishing a change,
 *  processing a change received from the replicationServer service,
 *  handle conflict resolution,
 *  handle protocol messages from the replicationServer.
 */
public final class LDAPReplicationDomain extends ReplicationDomain
       implements ConfigurationChangeListener<ReplicationDomainCfg>,
                  AlertGenerator
{
  /**
   * This class is used in the session establishment phase
   * when no Replication Server with all the local changes has been found
   * and we therefore need to recover them.
   * A search is then performed on the database using this
   * internalSearchListener.
   */
  private class ScanSearchListener implements InternalSearchListener
  {
    private final ChangeNumber startingChangeNumber;
    private final ChangeNumber endChangeNumber;

    public ScanSearchListener(
        ChangeNumber startingChangeNumber,
        ChangeNumber endChangeNumber)
    {
      this.startingChangeNumber = startingChangeNumber;
      this.endChangeNumber = endChangeNumber;
    }

    @Override
    public void handleInternalSearchEntry(
        InternalSearchOperation searchOperation, SearchResultEntry searchEntry)
        throws DirectoryException
    {
      // Build the list of Operations that happened on this entry
      // after startingChangeNumber and before endChangeNumber and
      // add them to the replayOperations list
      Iterable<FakeOperation> updates =
        EntryHistorical.generateFakeOperations(searchEntry);

      for (FakeOperation op : updates)
      {
        ChangeNumber cn = op.getChangeNumber();
        if ((cn.newer(startingChangeNumber)) && (cn.older(endChangeNumber)))
        {
          synchronized (replayOperations)
          {
            replayOperations.put(cn, op);
          }
        }
      }
    }

    @Override
    public void handleInternalSearchReference(
        InternalSearchOperation searchOperation,
        SearchResultReference searchReference) throws DirectoryException
    {
       // Nothing to do.
    }
  }

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.replication.plugin.LDAPReplicationDomain";

  /**
   * The attribute used to mark conflicting entries.
   * The value of this attribute should be the dn that this entry was
   * supposed to have when it was marked as conflicting.
   */
  public static final String DS_SYNC_CONFLICT = "ds-sync-conflict";

 /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The update to replay message queue where the listener thread is going to
   * push incoming update messages.
   */
  private final BlockingQueue<UpdateToReplay> updateToReplayQueue;
  private final AtomicInteger numResolvedNamingConflicts = new AtomicInteger();
  private final AtomicInteger numResolvedModifyConflicts = new AtomicInteger();
  private final AtomicInteger numUnresolvedNamingConflicts =
    new AtomicInteger();
  private final PersistentServerState state;
  private int numReplayedPostOpCalled = 0;

  private volatile long generationId = -1;
  private volatile boolean generationIdSavedStatus = false;

  private final ChangeNumberGenerator generator;

  /**
   * This object is used to store the list of update currently being
   * done on the local database.
   * Is is useful to make sure that the local operations are sent in a
   * correct order to the replication server and that the ServerState
   * is not updated too early.
   */
  private final PendingChanges pendingChanges;

  /**
   * It contain the updates that were done on other servers, transmitted
   * by the replication server and that are currently replayed.
   * It is useful to make sure that dependencies between operations
   * are correctly fulfilled and to to make sure that the ServerState is
   * not updated too early.
   */
  private final RemotePendingChanges remotePendingChanges;

  private final int serverId;

  private final DN baseDn;

  private volatile boolean shutdown = false;

  private final InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

  private boolean solveConflictFlag = true;

  private volatile boolean disabled = false;
  private volatile boolean stateSavingDisabled = false;

  /**
   * This list is used to temporary store operations that needs to be replayed
   * at session establishment time.
   */
  private final SortedMap<ChangeNumber, FakeOperation> replayOperations =
    new TreeMap<ChangeNumber, FakeOperation>();

  /**
   * The isolation policy that this domain is going to use.
   * This field describes the behavior of the domain when an update is
   * attempted and the domain could not connect to any Replication Server.
   * Possible values are accept-updates or deny-updates, but other values
   * may be added in the future.
   */
  private IsolationPolicy isolationPolicy;

  /**
   * The DN of the configuration entry of this domain.
   */
  private final DN configDn;
  private ExternalChangelogDomain eclDomain;

  /**
   * A boolean indicating if the thread used to save the persistentServerState
   * is terminated.
   */
  private volatile boolean done = true;

  private final ServerStateFlush flushThread;

  /**
   * The attribute name used to store the generation id in the backend.
   */
  private static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";
  /**
   * The attribute name used to store the fractional include configuration in
   * the backend.
   */
  public static final String REPLICATION_FRACTIONAL_INCLUDE =
    "ds-sync-fractional-include";
  /**
   * The attribute name used to store the fractional exclude configuration in
   * the backend.
   */
  public static final String REPLICATION_FRACTIONAL_EXCLUDE =
    "ds-sync-fractional-exclude";

  /**
   * Fractional replication variables.
   */

  /** Holds the fractional configuration for this domain, if any. */
  private FractionalConfig fractionalConfig = null;

  /**
   * The list of attributes that cannot be used in fractional replication
   * configuration.
   */
  private static final String[] FRACTIONAL_PROHIBITED_ATTRIBUTES = new String[]
  {
    "objectClass",
    "2.5.4.0" // objectClass OID
  };

  /**
   * When true, this flag is used to force the domain status to be put in bad
   * data set just after the connection to the replication server.
   * This must be used when fractional replication is enabled with a
   * configuration different from the previous one (or at the very first
   * fractional usage time) : after connection, a ChangeStatusMsg is sent
   * requesting the bad data set status. Then none of the update messages
   * received from the replication server are taken into account until the
   * backend is synchronized with brand new data set compliant with the new
   * fractional configuration (i.e with compliant fractional configuration in
   * domain root entry).
   */
  private boolean forceBadDataSet = false;

  /**
   * This flag is used by the fractional replication ldif import plugin to
   * stop the (online) import process if a fractional configuration
   * inconsistency is detected by it.
   */
  private boolean followImport = true;

  /**
   * The message id to be used when an import is stopped with error by
   * the fractional replication ldif import plugin.
   */
  private int importErrorMessageId = -1;
  /**
   * Message type for ERR_FULL_UPDATE_IMPORT_FRACTIONAL_BAD_REMOTE.
   */
  public static final int IMPORT_ERROR_MESSAGE_BAD_REMOTE = 1;
  /**
   * Message type for ERR_FULL_UPDATE_IMPORT_FRACTIONAL_REMOTE_IS_FRACTIONAL.
   */
  public static final int IMPORT_ERROR_MESSAGE_REMOTE_IS_FRACTIONAL = 2;

  /*
   * Definitions for the return codes of the
   * fractionalFilterOperation(PreOperationModifyOperation
   *  modifyOperation, boolean performFiltering) method
   */
  /**
   * The operation contains attributes subject to fractional filtering according
   * to the fractional configuration.
   */
  private static final int FRACTIONAL_HAS_FRACTIONAL_FILTERED_ATTRIBUTES = 1;
  /**
   * The operation contains no attributes subject to fractional filtering
   * according to the fractional configuration.
   */
  private static final int FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES = 2;
  /** The operation should become a no-op. */
  private static final int FRACTIONAL_BECOME_NO_OP = 3;

  /**
   * This configuration boolean indicates if this ReplicationDomain should log
   * ChangeNumbers.
   */
  private boolean logChangeNumber = false;

  /**
   * This configuration integer indicates the time the domain keeps the
   * historical information necessary to solve conflicts.<br>
   * When a change stored in the historical part of the user entry has a date
   * (from its replication ChangeNumber) older than this delay, it is candidate
   * to be purged.
   */
  private long histPurgeDelayInMilliSec = 0;

  /**
   * The last change number purged in this domain. Allows to have a continuous
   * purging process from one purge processing (task run) to the next one.
   * Values 0 when the server starts.
   */
  private ChangeNumber lastChangeNumberPurgedFromHist = new ChangeNumber(0,0,0);

  /**
   * The thread that periodically saves the ServerState of this
   * LDAPReplicationDomain in the database.
   */
  private class  ServerStateFlush extends DirectoryThread
  {
    protected ServerStateFlush()
    {
      super("Replica DS(" + serverId
          + ") state checkpointer for domain \"" + baseDn.toString()
          + "\"");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      done = false;

      while (!shutdown)
      {
        try
        {
          synchronized (this)
          {
            this.wait(1000);
            if (!disabled && !stateSavingDisabled)
            {
              // save the ServerState
              state.save();
            }
          }
        }
        catch (InterruptedException e)
        {
          // Thread interrupted: check for shutdown.
        }
      }
      state.save();

      done = true;
    }
  }

  /**
   * The thread that is responsible to update the RS to which this domain is
   * connected in case it is late and there is no RS which is up to date.
   */
  private class RSUpdater extends DirectoryThread
  {
    private final ChangeNumber startChangeNumber;



    protected RSUpdater(ChangeNumber replServerMaxChangeNumber)
    {
      super("Replica DS(" + serverId
          + ") missing change publisher for domain \""
          + baseDn.toString() + "\"");
      this.startChangeNumber = replServerMaxChangeNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
      // Replication server is missing some of our changes: let's
      // send them to him.
      Message message = DEBUG_GOING_TO_SEARCH_FOR_CHANGES.get();
      logError(message);

      /*
       * Get all the changes that have not been seen by this
       * replication server and publish them.
       */
      try
      {
        if (buildAndPublishMissingChanges(startChangeNumber, broker))
        {
          message = DEBUG_CHANGES_SENT.get();
          logError(message);
          synchronized(replayOperations)
          {
            replayOperations.clear();
          }
        }
        else
        {
          /*
           * An error happened trying to search for the updates
           * This server will start accepting again new updates but
           * some inconsistencies will stay between servers.
           * Log an error for the repair tool
           * that will need to re-synchronize the servers.
           */
          message = ERR_CANNOT_RECOVER_CHANGES.get(
              baseDn.toNormalizedString());
          logError(message);
        }
      } catch (Exception e)
      {
        /*
         * An error happened trying to search for the updates
         * This server will start accepting again new updates but
         * some inconsistencies will stay between servers.
         * Log an error for the repair tool
         * that will need to re-synchronize the servers.
         */
        message = ERR_CANNOT_RECOVER_CHANGES.get(
            baseDn.toNormalizedString());
        logError(message);
      }
      finally
      {
        broker.setRecoveryRequired(false);
      }
    }
  }


  /**
   * Creates a new ReplicationDomain using configuration from configEntry.
   *
   * @param configuration    The configuration of this ReplicationDomain.
   * @param updateToReplayQueue The queue for update messages to replay.
   * @throws ConfigException In case of invalid configuration.
   */
  public LDAPReplicationDomain(ReplicationDomainCfg configuration,
    BlockingQueue<UpdateToReplay> updateToReplayQueue)
    throws ConfigException
  {
    super(configuration.getBaseDN().toNormalizedString(),
          configuration.getServerId(),
          configuration.getInitializationWindowSize());

    // Read the configuration parameters.
    Set<String> replicationServers = configuration.getReplicationServer();

    this.serverId = configuration.getServerId();
    this.baseDn = configuration.getBaseDN();
    int window  = configuration.getWindowSize();
    /**
     * The time in milliseconds between heartbeats from the replication
     * server.  Zero means heartbeats are off.
     */
    long heartbeatInterval = configuration.getHeartbeatInterval();

    this.isolationPolicy = configuration.getIsolationPolicy();
    this.configDn = configuration.dn();
    this.logChangeNumber = configuration.isLogChangenumber();
    this.updateToReplayQueue = updateToReplayQueue;
    this.histPurgeDelayInMilliSec =
      configuration.getConflictsHistoricalPurgeDelay()*60*1000;

    // Get assured configuration
    readAssuredConfig(configuration, false);

    // Get fractional configuration
    fractionalConfig = new FractionalConfig(baseDn);
    readFractionalConfig(configuration, false);

    setGroupId((byte)configuration.getGroupId());
    setURLs(configuration.getReferralsUrl());

    storeECLConfiguration(configuration);

    /*
     * Modify conflicts are solved for all suffixes but the schema suffix
     * because we don't want to store extra information in the schema
     * ldif files.
     * This has no negative impact because the changes on schema should
     * not produce conflicts.
     */
    if (baseDn.compareTo(DirectoryServer.getSchemaDN()) == 0)
    {
      solveConflictFlag = false;
    }
    else
    {
      solveConflictFlag = configuration.isSolveConflicts();
    }

    Backend backend = retrievesBackend(baseDn);
    if (backend == null)
    {
      throw new ConfigException(ERR_SEARCHING_DOMAIN_BACKEND.get(
                                  baseDn.toNormalizedString()));
    }

    try
    {
      generationId = loadGenerationId();
    }
    catch (DirectoryException e)
    {
      logError(ERR_LOADING_GENERATION_ID.get(
          baseDn.toNormalizedString(), e.getLocalizedMessage()));
    }

    /*
     * Create a new Persistent Server State that will be used to store
     * the last ChangeNumber seen from all LDAP servers in the topology.
     */
    state = new PersistentServerState(baseDn, serverId, getServerState());

    flushThread = new ServerStateFlush();

    /*
     * ChangeNumberGenerator is used to create new unique ChangeNumbers
     * for each operation done on this replication domain.
     *
     * The generator time is adjusted to the time of the last CN received from
     * remote other servers.
     */
    generator = getGenerator();

    pendingChanges =
      new PendingChanges(generator, this);

    remotePendingChanges = new RemotePendingChanges(getServerState());

    // listen for changes on the configuration
    configuration.addChangeListener(this);

    // register as an AlertGenerator
    DirectoryServer.registerAlertGenerator(this);

    startPublishService(replicationServers, window, heartbeatInterval,
        configuration.getChangetimeHeartbeatInterval());
  }

  /**
   * Gets and stores the assured replication configuration parameters. Returns
   * a boolean indicating if the passed configuration has changed compared to
   * previous values and the changes require a reconnection.
   * @param configuration The configuration object
   * @param allowReconnection Tells if one must reconnect if significant changes
   *        occurred
   */
  private void readAssuredConfig(ReplicationDomainCfg configuration,
    boolean allowReconnection)
  {
    boolean needReconnection = false;

    byte newSdLevel = (byte) configuration.getAssuredSdLevel();
    if (isAssured() && getAssuredMode() == AssuredMode.SAFE_DATA_MODE &&
        newSdLevel != getAssuredSdLevel())
    {
      needReconnection = true;
    }

    AssuredType newAssuredType = configuration.getAssuredType();
    switch (newAssuredType)
    {
      case NOT_ASSURED:
        if (isAssured())
        {
          needReconnection = true;
        }
        break;
      case SAFE_DATA:
        if (!isAssured() || getAssuredMode() == AssuredMode.SAFE_READ_MODE)
        {
          needReconnection = true;
        }
        break;
      case SAFE_READ:
        if (!isAssured() || getAssuredMode() == AssuredMode.SAFE_DATA_MODE)
        {
          needReconnection = true;
        }
        break;
    }

    // Disconnect if required: changing configuration values before
    // disconnection would make assured replication used immediately and
    // disconnection could cause some timeouts error.
    if (needReconnection && allowReconnection)
      disableService();

    switch (newAssuredType)
    {
      case NOT_ASSURED:
        setAssured(false);
        break;
      case SAFE_DATA:
        setAssured(true);
        setAssuredMode(AssuredMode.SAFE_DATA_MODE);
        break;
      case SAFE_READ:
        setAssured(true);
        setAssuredMode(AssuredMode.SAFE_READ_MODE);
        break;
    }
    setAssuredSdLevel(newSdLevel);
    setAssuredTimeout(configuration.getAssuredTimeout());

    // Reconnect if required
    if (needReconnection && allowReconnection)
      enableService();
  }

  /**
   * Sets the error message id to be used when online import is stopped with
   * error by the fractional replication ldif import plugin.
   * @param importErrorMessageId The message to use.
   */
  public void setImportErrorMessageId(int importErrorMessageId)
  {
    this.importErrorMessageId = importErrorMessageId;
  }

  /**
   * Sets the boolean telling if the online import currently in progress should
   * continue.
   * @param followImport The boolean telling if the online import currently in
   * progress should continue.
   */
  public void setFollowImport(boolean followImport)
  {
    this.followImport = followImport;
  }

  /**
   * Gets and stores the fractional replication configuration parameters.
   * @param configuration The configuration object
   * @param allowReconnection Tells if one must reconnect if significant changes
   *        occurred
   */
  private void readFractionalConfig(ReplicationDomainCfg configuration,
    boolean allowReconnection)
  {
    // Read the configuration entry
    FractionalConfig newFractionalConfig;
    try
    {
      newFractionalConfig = FractionalConfig.toFractionalConfig(
        configuration);
    }
    catch(ConfigException e)
    {
      // Should not happen as normally already called without problem in
      // isConfigurationChangeAcceptable or isConfigurationAcceptable
      // if we come up to this method
      Message message = NOTE_ERR_FRACTIONAL.get(baseDn.toString(),
        e.getLocalizedMessage());
      logError(message);
      return;
    }

    /**
     * Is there any change in fractional configuration ?
     */

    // Compute current configuration
    boolean needReconnection;
     try
    {
      needReconnection = !FractionalConfig.
        isFractionalConfigEquivalent(fractionalConfig, newFractionalConfig);
    }
    catch  (ConfigException e)
    {
      // Should not happen
      Message message = NOTE_ERR_FRACTIONAL.get(baseDn.toString(),
        e.getLocalizedMessage());
      logError(message);
      return;
    }

    // Disable service if configuration changed
    if (needReconnection && allowReconnection)
    {
      disableService();
    }
    // Set new configuration
    int newFractionalMode = newFractionalConfig.fractionalConfigToInt();
    fractionalConfig.setFractional(newFractionalMode !=
      FractionalConfig.NOT_FRACTIONAL);
    if (fractionalConfig.isFractional())
    {
      // Set new fractional configuration values
      fractionalConfig.setFractionalExclusive(
          newFractionalMode == FractionalConfig.EXCLUSIVE_FRACTIONAL);
      fractionalConfig.setFractionalSpecificClassesAttributes(
        newFractionalConfig.getFractionalSpecificClassesAttributes());
      fractionalConfig.setFractionalAllClassesAttributes(
        newFractionalConfig.fractionalAllClassesAttributes);
    } else
    {
      // Reset default values
      fractionalConfig.setFractionalExclusive(true);
      fractionalConfig.setFractionalSpecificClassesAttributes(
        new HashMap<String, List<String>>());
      fractionalConfig.setFractionalAllClassesAttributes(
        new ArrayList<String>());
    }

    // Reconnect if required
    if (needReconnection && allowReconnection)
      enableService();
  }

  /**
   * Return true if the fractional configuration stored in the domain root
   * entry of the backend is equivalent to the fractional configuration stored
   * in the local variables.
   */
  private boolean isBackendFractionalConfigConsistent()
  {
    /*
     * Read config stored in domain root entry
     */

    if (debugEnabled())
      TRACER.debugInfo(
        "Attempt to read the potential fractional config in domain root " +
        "entry " + baseDn.toString());

    ByteString asn1BaseDn = ByteString.valueOf(baseDn.toString());
    boolean found = false;
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    } catch (LDAPException e)
    {
      // Can not happen
      return false;
    }

    /*
     * Search the domain root entry that is used to save the generation id
     */

    LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
    attributes.add(REPLICATION_GENERATION_ID);
    attributes.add(REPLICATION_FRACTIONAL_EXCLUDE);
    attributes.add(REPLICATION_FRACTIONAL_INCLUDE);
    InternalSearchOperation search = conn.processSearch(asn1BaseDn,
      SearchScope.BASE_OBJECT,
      DereferencePolicy.DEREF_ALWAYS, 0, 0, false,
      filter, attributes);
    if (((search.getResultCode() != ResultCode.SUCCESS)) &&
      ((search.getResultCode() != ResultCode.NO_SUCH_OBJECT)))
    {
      Message message = ERR_SEARCHING_GENERATION_ID.get(
        search.getResultCode().getResultCodeName() + " " +
        search.getErrorMessage(),
        baseDn.toString());
      logError(message);
      return false;
    }

    SearchResultEntry resultEntry = null;
    if (search.getResultCode() == ResultCode.SUCCESS)
    {
      LinkedList<SearchResultEntry> result = search.getSearchEntries();
      resultEntry = result.getFirst();
      if (resultEntry != null)
      {
        AttributeType synchronizationGenIDType =
          DirectoryServer.getAttributeType(REPLICATION_GENERATION_ID);
        List<Attribute> attrs =
          resultEntry.getAttribute(synchronizationGenIDType);
        if (attrs != null)
        {
          Attribute attr = attrs.get(0);
          if (attr.size() > 1)
          {
            Message message = ERR_LOADING_GENERATION_ID.get(
              baseDn.toString(), "#Values=" + attr.size() +
              " Must be exactly 1 in entry " +
              resultEntry.toLDIFString());
            logError(message);
          } else if (attr.size() == 1)
          {
            found = true;
          }
        }
      }
    }

    if (!found)
    {
      /*
      The backend is probably empty: if there is some fractional
      configuration in memory, we do not let the domain being connected,
      otherwise, it's ok
      */
      return !fractionalConfig.isFractional();
    }

    /*
     * Now extract fractional configuration if any
     */

    Iterator<String> exclIt = null;
    AttributeType fractionalExcludeType =
      DirectoryServer.getAttributeType(REPLICATION_FRACTIONAL_EXCLUDE);
    List<Attribute> exclAttrs =
      resultEntry.getAttribute(fractionalExcludeType);
    if (exclAttrs != null)
    {
      Attribute exclAttr = exclAttrs.get(0);
      if (exclAttr != null)
      {
        exclIt = new AttributeValueStringIterator(exclAttr.iterator());
      }
    }

    Iterator<String> inclIt = null;
    AttributeType fractionalIncludeType =
      DirectoryServer.getAttributeType(REPLICATION_FRACTIONAL_INCLUDE);
    List<Attribute> inclAttrs =
      resultEntry.getAttribute(fractionalIncludeType);
    if (inclAttrs != null)
    {
      Attribute inclAttr = inclAttrs.get(0);
      if (inclAttr != null)
      {
        inclIt = new AttributeValueStringIterator(inclAttr.iterator());
      }
    }

    // Compare backend and local fractional configuration
    return isFractionalConfigConsistent(fractionalConfig, exclIt, inclIt);
  }

  /**
   * Return true if the fractional configuration passed as fractional
   * configuration attribute values is equivalent to the fractional
   * configuration stored in the local variables.
   * @param fractionalConfig The local fractional configuration
   * @param exclIt Fractional exclude mode configuration attribute values to
   * analyze.
   * @param inclIt Fractional include mode configuration attribute values to
   * analyze.
   * @return True if the fractional configuration passed as fractional
   * configuration attribute values is equivalent to the fractional
   * configuration stored in the local variables.
   */
   static boolean isFractionalConfigConsistent(
    FractionalConfig fractionalConfig, Iterator<String> exclIt,
    Iterator<String> inclIt)
  {
    /*
     * Parse fractional configuration stored in passed fractional configuration
     * attributes values
     */

    Map<String, List<String>> storedFractionalSpecificClassesAttributes =
      new HashMap<String, List<String>>();
    List<String> storedFractionalAllClassesAttributes = new ArrayList<String>();

    int storedFractionalMode;
    try
    {
      storedFractionalMode = FractionalConfig.parseFractionalConfig(exclIt,
        inclIt, storedFractionalSpecificClassesAttributes,
        storedFractionalAllClassesAttributes);
    } catch (ConfigException e)
    {
      // Should not happen as configuration in domain root entry is flushed
      // from valid configuration in local variables
      Message message = NOTE_ERR_FRACTIONAL.get(
        fractionalConfig.getBaseDn().toString(), e.getLocalizedMessage());
      logError(message);
      return false;
    }

    FractionalConfig storedFractionalConfig = new FractionalConfig(
      fractionalConfig.getBaseDn());
    storedFractionalConfig.setFractional(storedFractionalMode !=
      FractionalConfig.NOT_FRACTIONAL);
    // Set stored fractional configuration values
    if (storedFractionalConfig.isFractional())
    {
      storedFractionalConfig.setFractionalExclusive(
          storedFractionalMode == FractionalConfig.EXCLUSIVE_FRACTIONAL);
    }
    storedFractionalConfig.setFractionalSpecificClassesAttributes(
      storedFractionalSpecificClassesAttributes);
    storedFractionalConfig.setFractionalAllClassesAttributes(
      storedFractionalAllClassesAttributes);

    /*
     * Compare configuration stored in passed fractional configuration
     * attributes with local variable one
     */

    try
    {
      return FractionalConfig.
        isFractionalConfigEquivalent(fractionalConfig, storedFractionalConfig);
    } catch (ConfigException e)
    {
      // Should not happen as configuration in domain root entry is flushed
      // from valid configuration in local variables so both should have already
      // been checked
      Message message = NOTE_ERR_FRACTIONAL.get(
        fractionalConfig.getBaseDn().toString(), e.getLocalizedMessage());
      logError(message);
      return false;
    }
  }

  /**
   * Utility class to have get a string iterator from an AtributeValue iterator.
   * Assuming the attribute values are strings.
   */
  public static class AttributeValueStringIterator implements Iterator<String>
  {
    private Iterator<AttributeValue> attrValIt = null;

    /**
     * Creates a new AttributeValueStringIterator object.
     * @param attrValIt The underlying attribute iterator to use, assuming
     * internal values are strings.
     */
    public AttributeValueStringIterator(Iterator<AttributeValue> attrValIt)
    {
      this.attrValIt = attrValIt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext()
    {
      return attrValIt.hasNext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String next()
    {
      return attrValIt.next().getValue().toString();
    }

    /**
     * {@inheritDoc}
     */
    // Should not be needed anyway
    @Override
    public void remove()
    {
      attrValIt.remove();
    }
  }

  /**
   * Compare 2 attribute lists and returns true if they are equivalent.
   * @param attributes1 First attribute list to compare.
   * @param attributes2 Second attribute list to compare.
   * @return True if both attribute lists are equivalent.
   * @throws ConfigException If some attributes could not be retrieved from the
   * schema.
   */
  private static boolean isAttributeListEquivalent(
    List<String> attributes1, List<String> attributes2) throws ConfigException
  {
    // Compare all classes attributes
    if (attributes1.size() != attributes2.size())
      return false;

    // Check consistency of all classes attributes
    Schema schema = DirectoryServer.getSchema();
    /*
     * For each attribute in attributes1, check there is the matching
     * one in attributes2.
     */
    for (String attributName1 : attributes1)
    {
      // Get attribute from attributes1
      AttributeType attributeType1 = schema.getAttributeType(attributName1);
      if (attributeType1 == null)
      {
        throw new ConfigException(
          NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_ATTRIBUTE_TYPE.get(attributName1));
      }
      // Look for matching one in attributes2
      boolean foundAttribute = false;
      for (String attributName2 : attributes2)
      {
        AttributeType attributeType2 = schema.getAttributeType(attributName2);
        if (attributeType2 == null)
        {
          throw new ConfigException(
            NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_ATTRIBUTE_TYPE.get(
            attributName2));
        }
        if (attributeType1.equals(attributeType2))
        {
          foundAttribute = true;
          break;
        }
      }
      // Found matching attribute ?
      if (!foundAttribute)
        return false;
    }

    return true;
  }

  /**
   * Check that the passed fractional configuration is acceptable
   * regarding configuration syntax, schema constraints...
   * Throws an exception if the configuration is not acceptable.
   * @param configuration The configuration to analyze.
   * @throws org.opends.server.config.ConfigException if the configuration is
   * not acceptable.
   */
  private static void isFractionalConfigAcceptable(
    ReplicationDomainCfg configuration) throws ConfigException
  {
    /*
     * Parse fractional configuration
     */

    // Read the configuration entry
    FractionalConfig newFractionalConfig = FractionalConfig.toFractionalConfig(
        configuration);

    if (!newFractionalConfig.isFractional())
    {
        // Nothing to check
        return;
    }

    // Prepare variables to be filled with config
    Map<String, List<String>> newFractionalSpecificClassesAttributes =
      newFractionalConfig.getFractionalSpecificClassesAttributes();
    List<String> newFractionalAllClassesAttributes =
      newFractionalConfig.getFractionalAllClassesAttributes();

    /*
     * Check attributes consistency : we only allow to filter MAY (optional)
     * attributes of a class : to be compliant with the schema, no MUST
     * (mandatory) attribute can be filtered by fractional replication.
     */

    // Check consistency of specific classes attributes
    Schema schema = DirectoryServer.getSchema();
    int fractionalMode = newFractionalConfig.fractionalConfigToInt();
    for (String className : newFractionalSpecificClassesAttributes.keySet())
    {
      // Does the class exist ?
      ObjectClass fractionalClass = schema.getObjectClass(
        className.toLowerCase());
      if (fractionalClass == null)
      {
        throw new ConfigException(
          NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_OBJECT_CLASS.get(className));
      }

      boolean isExtensibleObjectClass = className.
        equalsIgnoreCase("extensibleObject");

      List<String> attributes =
        newFractionalSpecificClassesAttributes.get(className);

      for (String attrName : attributes)
      {
        // Not a prohibited attribute ?
        if (isFractionalProhibitedAttr(attrName))
        {
          throw new ConfigException(
            NOTE_ERR_FRACTIONAL_CONFIG_PROHIBITED_ATTRIBUTE.get(attrName));
        }

        // Does the attribute exist ?
        AttributeType attributeType = schema.getAttributeType(attrName);
        if (attributeType != null)
        {
          // No more checking for the extensibleObject class
          if (!isExtensibleObjectClass)
          {
            if (fractionalMode == FractionalConfig.EXCLUSIVE_FRACTIONAL)
            {
              // Exclusive mode : the attribute must be optional
              if (!fractionalClass.isOptional(attributeType))
              {
                throw new ConfigException(
                  NOTE_ERR_FRACTIONAL_CONFIG_NOT_OPTIONAL_ATTRIBUTE.
                  get(attrName, className));
              }
            }
          }
        }
        else
        {
          throw new ConfigException(
            NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_ATTRIBUTE_TYPE.get(attrName));
        }
      }

    }

    // Check consistency of all classes attributes
    for (String attrName : newFractionalAllClassesAttributes)
    {
      // Not a prohibited attribute ?
      if (isFractionalProhibitedAttr(attrName))
      {
        throw new ConfigException(
          NOTE_ERR_FRACTIONAL_CONFIG_PROHIBITED_ATTRIBUTE.get(attrName));
      }

      // Does the attribute exist ?
      if (schema.getAttributeType(attrName) == null)
      {
        throw new ConfigException(
          NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_ATTRIBUTE_TYPE.get(attrName));
      }
    }
  }

  /**
   * Test if the passed attribute is not allowed to be used in configuration of
   * fractional replication.
   * @param attr Attribute to test.
   * @return true if the attribute is prohibited.
   */
  private static boolean isFractionalProhibitedAttr(String attr)
  {
    for (String forbiddenAttr : FRACTIONAL_PROHIBITED_ATTRIBUTES)
    {
      if (forbiddenAttr.equalsIgnoreCase(attr))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * If fractional replication is enabled, this analyzes the operation and
   * suppresses the forbidden attributes in it so that they are not added in
   * the local backend.
   *
   * @param addOperation The operation to modify based on fractional
   * replication configuration
   * @param performFiltering Tells if the effective attribute filtering should
   * be performed or if the call is just to analyze if there are some
   * attributes filtered by fractional configuration
   * @return true if the operation contains some attributes subject to filtering
   * by the fractional configuration
   */
  public boolean fractionalFilterOperation(
    PreOperationAddOperation addOperation, boolean performFiltering)
  {
    return fractionalRemoveAttributesFromEntry(fractionalConfig,
      addOperation.getEntryDN().getRDN(), addOperation.getObjectClasses(),
      addOperation.getUserAttributes(), performFiltering);
  }

  /**
   * If fractional replication is enabled, this analyzes the operation and
   * suppresses the forbidden attributes in it so that they are not added in
   * the local backend.
   *
   * @param modifyDNOperation The operation to modify based on fractional
   * replication configuration
   * @param performFiltering Tells if the effective modifications should
   * be performed or if the call is just to analyze if there are some
   * inconsistency with fractional configuration
   * @return true if the operation is inconsistent with fractional
   * configuration
   */
  public boolean fractionalFilterOperation(
    PreOperationModifyDNOperation modifyDNOperation, boolean performFiltering)
  {
    boolean inconsistentOperation = false;

    // Quick exit if not called for analyze and
    if (performFiltering)
    {
      if (modifyDNOperation.deleteOldRDN())
      {
        // The core will remove any occurrence of attribute that was part
        // of the old RDN, nothing more to do.
        return true; // Will not be used as analyze was not requested
      }
    }

    /*
     * Create a list of filtered attributes for this entry
     */

    Entry concernedEntry = modifyDNOperation.getOriginalEntry();
    List<String> fractionalConcernedAttributes =
      createFractionalConcernedAttrList(fractionalConfig,
      concernedEntry.getObjectClasses().keySet());

    boolean fractionalExclusive = fractionalConfig.isFractionalExclusive();
    if ( fractionalExclusive && (fractionalConcernedAttributes.isEmpty()) )
      // No attributes to filter
      return false;

    /*
     * Analyze the old and new rdn to see if they are some attributes to be
     * removed: if the oldRDN contains some forbidden attributes (for instance
     * it is possible if the entry was created with an add operation and the
     * RDN used contains a forbidden attribute: in this case the attribute value
     * has been kept to be consistent with the dn of the entry.) that are no
     * more part of the new RDN, we must remove any attribute of this type by
     * putting a modification to delete the attribute.
     */

    RDN rdn = modifyDNOperation.getEntryDN().getRDN();
    RDN newRdn = modifyDNOperation.getNewRDN();

    // Go through each attribute of the old RDN
    for (int i=0 ; i<rdn.getNumValues() ; i++)
    {
      AttributeType attributeType = rdn.getAttributeType(i);
      boolean found = false;
      // Is it present in the fractional attributes established list ?
      for (String attrTypeStr : fractionalConcernedAttributes)
      {
        AttributeType attributeTypeFromList =
        DirectoryServer.getAttributeType(attrTypeStr);
        if (attributeTypeFromList.equals(attributeType))
        {
          found = true;
          break;
        }
      }
      boolean attributeToBeFiltered = ( (fractionalExclusive && found) ||
        (!fractionalExclusive && !found) );
      if (attributeToBeFiltered &&
        !newRdn.hasAttributeType(attributeType) &&
        !modifyDNOperation.deleteOldRDN())
      {
        /*
         * A forbidden attribute is in the old RDN and no more in the new RDN,
         * and it has not been requested to remove attributes from old RDN:
         * let's remove the attribute from the entry to stay consistent with
         * fractional configuration
         */
        Modification modification = new Modification(ModificationType.DELETE,
          Attributes.empty(attributeType));
        modifyDNOperation.addModification(modification);
        inconsistentOperation = true;
      }
    }

    return inconsistentOperation;
  }

  /**
   * Remove attributes from an entry, according to the passed fractional
   * configuration. The entry is represented by the 2 passed parameters.
   * The attributes to be removed are removed using the remove method on the
   * passed iterator for the attributes in the entry.
   * @param fractionalConfig The fractional configuration to use
   * @param entryRdn The rdn of the entry to add
   * @param classes The object classes representing the entry to modify
   * @param attributesMap The map of attributes/values to be potentially removed
   * from the entry.
   * @param performFiltering Tells if the effective attribute filtering should
   * be performed or if the call is just an analyze to see if there are some
   * attributes filtered by fractional configuration
   * @return true if the operation contains some attributes subject to filtering
   * by the fractional configuration
   */
   static boolean fractionalRemoveAttributesFromEntry(
    FractionalConfig fractionalConfig, RDN entryRdn,
    Map<ObjectClass,String> classes, Map<AttributeType,
    List<Attribute>> attributesMap, boolean performFiltering)
  {
    boolean hasSomeAttributesToFilter = false;
    /*
     * Prepare a list of attributes to be included/excluded according to the
     * fractional replication configuration
     */

    List<String> fractionalConcernedAttributes =
      createFractionalConcernedAttrList(fractionalConfig, classes.keySet());
    boolean fractionalExclusive = fractionalConfig.isFractionalExclusive();
    if ( fractionalExclusive && (fractionalConcernedAttributes.isEmpty()) )
      return false; // No attributes to filter

    // Prepare list of object classes of the added entry
    Set<ObjectClass> entryClasses = classes.keySet();

    /*
     * Go through the user attributes and remove those that match filtered one
     * - exclude mode : remove only attributes that are in
     * fractionalConcernedAttributes
     * - include mode : remove any attribute that is not in
     * fractionalConcernedAttributes
     */
    Iterator<AttributeType> attributeTypes = attributesMap.keySet().iterator();
    List<List<Attribute>> newRdnAttrLists = new ArrayList<List<Attribute>>();
    List<AttributeType> rdnAttrTypes = new ArrayList<AttributeType>();
    while (attributeTypes.hasNext())
    {
      AttributeType attributeType = attributeTypes.next();

      // Only optional attributes may be removed
      boolean isMandatoryAttribute = false;
      for (ObjectClass objectClass : entryClasses)
      {
        if (objectClass.isRequired(attributeType))
        {
          isMandatoryAttribute = true;
          break;
        }
      }
      if (isMandatoryAttribute)
      {
        continue;
      }

      String attributeName = attributeType.getPrimaryName();
      String attributeOid = attributeType.getOID();
      // Do not remove an attribute if it is a prohibited one
      if (((attributeName != null) &&
        isFractionalProhibitedAttr(attributeName)) ||
        isFractionalProhibitedAttr(attributeOid))
      {
        continue;
      }

      // Is the current attribute part of the established list ?
      boolean foundAttribute =
        fractionalConcernedAttributes.contains(attributeName.toLowerCase());
      if (!foundAttribute)
      {
        foundAttribute =
          fractionalConcernedAttributes.contains(attributeOid);
      }
      // Now remove the attribute if:
      // - exclusive mode and attribute is in configuration
      // - inclusive mode and attribute is not in configuration
      if ((foundAttribute && fractionalExclusive) ||
        (!foundAttribute && !fractionalExclusive))
      {
        if (performFiltering)
        {
          // Do not remove an attribute/value that is part of the RDN of the
          // entry as it is forbidden
          if (entryRdn.hasAttributeType(attributeType))
          {
            /*
            We must remove all values of the attributes map for this
            attribute type but the one that has the value which is in the RDN
            of the entry. In fact the (underlying )attribute list does not
            support remove so we have to create a new list, keeping only the
            attribute value which is the same as in the RDN
            */
            AttributeValue rdnAttributeValue =
              entryRdn.getAttributeValue(attributeType);
            List<Attribute> attrList = attributesMap.get(attributeType);
            AttributeValue sameAttrValue = null;
            //    Locate the attribute value identical to the one in the RDN
            for (Attribute attr : attrList)
            {
              if (attr.contains(rdnAttributeValue))
              {
                for (AttributeValue attrValue : attr) {
                  if (rdnAttributeValue.equals(attrValue)) {
                    // Keep the value we want
                    sameAttrValue = attrValue;
                  } else {
                    hasSomeAttributesToFilter = true;
                  }
                }
              }
              else
              {
                hasSomeAttributesToFilter = true;
              }
            }
            //    Recreate the attribute list with only the RDN attribute value
            if (sameAttrValue != null)
              // Paranoia check: should never be the case as we should always
              // find the attribute/value pair matching the pair in the RDN
            {
              // Construct and store new attribute list
              List<Attribute> newRdnAttrList = new ArrayList<Attribute>();
              AttributeBuilder attrBuilder =
                new AttributeBuilder(attributeType);
              attrBuilder.add(sameAttrValue);
              newRdnAttrList.add(attrBuilder.toAttribute());
              newRdnAttrLists.add(newRdnAttrList);
              /*
              Store matching attribute type
              The mapping will be done using object from rdnAttrTypes as key
              and object from newRdnAttrLists (at same index) as value in
              the user attribute map to be modified
              */
              rdnAttrTypes.add(attributeType);
            }
          }
          else
          {
            // Found an attribute to remove, remove it from the list.
            attributeTypes.remove();
            hasSomeAttributesToFilter = true;
          }
        }
        else
        {
          // The call was just to check : at least one attribute to filter
          // found, return immediately the answer;
          return true;
        }
      }
    }
    // Now overwrite the attribute values for the attribute types present in the
    // RDN, if there are some filtered attributes in the RDN
    for (int index = 0 ; index < rdnAttrTypes.size() ; index++)
    {
      attributesMap.put(rdnAttrTypes.get(index), newRdnAttrLists.get(index));
    }
    return hasSomeAttributesToFilter;
  }

  /**
   * Prepares a list of attributes of interest for the fractional feature.
   * @param fractionalConfig The fractional configuration to use
   * @param entryObjectClasses The object classes of an entry on which an
   * operation is going to be performed.
   * @return The list of attributes of the entry to be excluded/included
   * when the operation will be performed.
   */
  private static List<String> createFractionalConcernedAttrList(
    FractionalConfig fractionalConfig, Set<ObjectClass> entryObjectClasses)
  {
    /*
     * Is the concerned entry of a type concerned by fractional replication
     * configuration ? If yes, add the matching attribute names to a list of
     * attributes to take into account for filtering
     * (inclusive or exclusive mode)
     */

    List<String> fractionalConcernedAttributes = new ArrayList<String>();

    // Get object classes the entry matches
    List<String> fractionalAllClassesAttributes =
      fractionalConfig.getFractionalAllClassesAttributes();
    Map<String, List<String>> fractionalSpecificClassesAttributes =
      fractionalConfig.getFractionalSpecificClassesAttributes();

    Set<String> fractionalClasses =
        fractionalSpecificClassesAttributes.keySet();
    for (ObjectClass entryObjectClass : entryObjectClasses)
    {
      for(String fractionalClass : fractionalClasses)
      {
        if (entryObjectClass.hasNameOrOID(fractionalClass.toLowerCase()))
        {
          List<String> attrList =
            fractionalSpecificClassesAttributes.get(fractionalClass);
          for(String attr : attrList)
          {
            // Avoid duplicate attributes (from 2 inheriting classes for
            // instance)
            if (!fractionalConcernedAttributes.contains(attr))
            {
              fractionalConcernedAttributes.add(attr);
            }
          }
        }
      }
    }

    /*
     * Add to the list any attribute which is class independent
     */
    for (String attr : fractionalAllClassesAttributes)
    {
      if (!fractionalConcernedAttributes.contains(attr))
      {
        fractionalConcernedAttributes.add(attr);
      }
    }

    return fractionalConcernedAttributes;
  }

  /**
   * If fractional replication is enabled, this analyzes the operation and
   * suppresses the forbidden attributes in it so that they are not added/
   * deleted/modified in the local backend.
   *
   * @param modifyOperation The operation to modify based on fractional
   * replication configuration
   * @param performFiltering Tells if the effective attribute filtering should
   * be performed or if the call is just to analyze if there are some
   * attributes filtered by fractional configuration
   * @return FRACTIONAL_HAS_FRACTIONAL_FILTERED_ATTRIBUTES,
   * FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES or FRACTIONAL_BECOME_NO_OP
   */
  public int fractionalFilterOperation(PreOperationModifyOperation
    modifyOperation, boolean performFiltering)
  {
    int result = FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES;
    /*
     * Prepare a list of attributes to be included/excluded according to the
     * fractional replication configuration
     */

    Entry modifiedEntry = modifyOperation.getCurrentEntry();
    List<String> fractionalConcernedAttributes =
      createFractionalConcernedAttrList(fractionalConfig,
      modifiedEntry.getObjectClasses().keySet());
    boolean fractionalExclusive = fractionalConfig.isFractionalExclusive();
    if ( fractionalExclusive && (fractionalConcernedAttributes.isEmpty()) )
      // No attributes to filter
      return FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES;

    // Prepare list of object classes of the modified entry
    DN entryToModifyDn = modifyOperation.getEntryDN();
    Entry entryToModify;
    try
    {
      entryToModify = DirectoryServer.getEntry(entryToModifyDn);
    }
    catch(DirectoryException e)
    {
      Message message = NOTE_ERR_FRACTIONAL.get(baseDn.toString(),
        e.getLocalizedMessage());
      logError(message);
      return FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES;
    }
    Set<ObjectClass> entryClasses = entryToModify.getObjectClasses().keySet();

    /*
     * Now go through the attribute modifications and filter the mods according
     * to the fractional configuration (using the just established concerned
     * attributes list):
     * - delete attributes: remove them if regarding a filtered attribute
     * - add attributes: remove them if regarding a filtered attribute
     * - modify attributes: remove them if regarding a filtered attribute
     */

    List<Modification> mods = modifyOperation.getModifications();
    Iterator<Modification> modsIt = mods.iterator();
    while (modsIt.hasNext())
    {
      Modification mod = modsIt.next();
      Attribute attr = mod.getAttribute();
      AttributeType attrType = attr.getAttributeType();
      // Fractional replication ignores operational attributes
      if (!attrType.isOperational())
      {
        // Only optional attributes may be removed
        boolean isMandatoryAttribute = false;
        for (ObjectClass objectClass : entryClasses)
        {
          if (objectClass.isRequired(attrType))
          {
            isMandatoryAttribute = true;
            break;
          }
        }
        if (isMandatoryAttribute)
        {
          continue;
        }

        String attributeName = attrType.getPrimaryName();
        String attributeOid = attrType.getOID();
        // Do not remove an attribute if it is a prohibited one
        if (((attributeName != null) &&
          isFractionalProhibitedAttr(attributeName)) ||
          isFractionalProhibitedAttr(attributeOid))
        {
          continue;
        }
        // Is the current attribute part of the established list ?
        boolean foundAttribute = attributeName != null &&
          fractionalConcernedAttributes.contains(attributeName.toLowerCase());
        if (!foundAttribute)
        {
          foundAttribute =
            fractionalConcernedAttributes.contains(attributeOid);
        }

        // Now remove the modification if:
        // - exclusive mode and the concerned attribute is in configuration
        // - inclusive mode and the concerned attribute is not in configuration
        if ( (foundAttribute && fractionalExclusive) ||
             (!foundAttribute && !fractionalExclusive) )
        {
          if (performFiltering)
          {
            // Found a modification to remove, remove it from the list.
            modsIt.remove();
            result = FRACTIONAL_HAS_FRACTIONAL_FILTERED_ATTRIBUTES;
            if (mods.isEmpty())
            {
              // This operation must become a no-op as no more modification in
              // it
              return FRACTIONAL_BECOME_NO_OP;
            }
          }
          else
          {
            // The call was just to check : at least one attribute to filter
            // found, return immediately the answer;
            return FRACTIONAL_HAS_FRACTIONAL_FILTERED_ATTRIBUTES;
          }
        }
      }
    }

    return result;
  }

  /**
   * This is overwritten to allow stopping the (online) import process by the
   * fractional ldif import plugin when it detects that the (imported) remote
   * data set is not consistent with the local fractional configuration.
   * {@inheritDoc}
   */
  @Override
  protected byte[] receiveEntryBytes()
  {
    if (followImport)
    {
      // Ok, next entry is allowed to be received
      return super.receiveEntryBytes();
    } else
    {
      // Fractional ldif import plugin detected inconsistency between local
      // and remote server fractional configuration and is stopping the import
      // process:
      // This is an error termination during the import
      // The error is stored and the import is ended
      // by returning null
      Message msg = null;
      switch (importErrorMessageId)
      {
        case IMPORT_ERROR_MESSAGE_BAD_REMOTE:
          msg = NOTE_ERR_FULL_UPDATE_IMPORT_FRACTIONAL_BAD_REMOTE.get(
            baseDn.toString(), Integer.toString(ieContext.getImportSource()));
          break;
        case IMPORT_ERROR_MESSAGE_REMOTE_IS_FRACTIONAL:
          msg = NOTE_ERR_FULL_UPDATE_IMPORT_FRACTIONAL_REMOTE_IS_FRACTIONAL.get(
            baseDn.toString(), Integer.toString(ieContext.getImportSource()));
          break;
      }
      ieContext.setException(
        new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg));
      return null;
    }
  }

  /**
   * This is overwritten to allow stopping the (online) export process if the
   * local domain is fractional and the destination is all other servers:
   * This make no sense to have only fractional servers in a replicated
   * topology. This prevents from administrator manipulation error that would
   * lead to whole topology data corruption.
   * {@inheritDoc}
   */
  @Override
  protected void initializeRemote(int target, int requestorID,
    Task initTask, int initWindow) throws DirectoryException
  {
    if ((target == RoutableMsg.ALL_SERVERS) && fractionalConfig.isFractional())
    {
      Message msg = NOTE_ERR_FRACTIONAL_FORBIDDEN_FULL_UPDATE_FRACTIONAL.get(
            baseDn.toString(), Integer.toString(getServerId()));
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
    } else
    {
      super.initializeRemote(target, requestorID, initTask, this.initWindow);
    }
  }

  /**
   * Returns the base DN of this ReplicationDomain.
   *
   * @return The base DN of this ReplicationDomain
   */
  public DN getBaseDN()
  {
    return baseDn;
  }

  /**
   * Implement the  handleConflictResolution phase of the deleteOperation.
   *
   * @param deleteOperation The deleteOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationDeleteOperation deleteOperation)
  {
    if ((!deleteOperation.isSynchronizationOperation())
        && (!brokerIsConnected()))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    DeleteContext ctx =
      (DeleteContext) deleteOperation.getAttachment(SYNCHROCONTEXT);
    Entry deletedEntry = deleteOperation.getEntryToDelete();

    if (ctx != null)
    {
      /*
       * This is a replication operation
       * Check that the modified entry has the same entryuuid
       * as it was in the original message.
       */
      String operationEntryUUID = ctx.getEntryUUID();
      String modifiedEntryUUID = EntryHistorical.getEntryUUID(deletedEntry);
      if (!operationEntryUUID.equals(modifiedEntryUUID))
      {
        /*
         * The changes entry is not the same entry as the one on
         * the original change was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the change proceed, return a negative
         * result and set the result code to NO_SUCH_OBJECT.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
      }
    }
    else
    {
      // There is no replication context attached to the operation
      // so this is not a replication operation.
      ChangeNumber changeNumber = generateChangeNumber(deleteOperation);
      String modifiedEntryUUID = EntryHistorical.getEntryUUID(deletedEntry);
      ctx = new DeleteContext(changeNumber, modifiedEntryUUID);
      deleteOperation.setAttachment(SYNCHROCONTEXT, ctx);

      synchronized (replayOperations)
      {
        int size = replayOperations.size();
        if (size >= 10000)
        {
          replayOperations.remove(replayOperations.firstKey());
        }
        replayOperations.put(
            changeNumber,
            new FakeDelOperation(
                deleteOperation.getEntryDN().toString(),
                changeNumber,modifiedEntryUUID ));
      }

    }

    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Implement the  handleConflictResolution phase of the addOperation.
   *
   * @param addOperation The AddOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationAddOperation addOperation)
  {
    if ((!addOperation.isSynchronizationOperation())
        && (!brokerIsConnected()))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    if (fractionalConfig.isFractional())
    {
      if (addOperation.isSynchronizationOperation())
      {
        /*
         * Filter attributes here for fractional replication. If fractional
         * replication is enabled, we analyze the operation to suppress the
         * forbidden attributes in it so that they are not added in the local
         * backend. This must be called before any other plugin is called, to
         * keep coherency across plugin calls.
         */
        fractionalFilterOperation(addOperation, true);
      }
      else
      {
        /*
         * Direct access from an LDAP client : if some attributes are to be
         * removed according to the fractional configuration, simply forbid
         * the operation
         */
        if (fractionalFilterOperation(addOperation, false))
        {
          StringBuilder sb = new StringBuilder();
          addOperation.toString(sb);
          Message msg = NOTE_ERR_FRACTIONAL_FORBIDDEN_OPERATION.get(
            baseDn.toString(), sb.toString());
          return new SynchronizationProviderResult.StopProcessing(
            ResultCode.UNWILLING_TO_PERFORM, msg);
        }
      }
    }

    if (addOperation.isSynchronizationOperation())
    {
      AddContext ctx = (AddContext) addOperation.getAttachment(SYNCHROCONTEXT);
      /*
       * If an entry with the same entry uniqueID already exist then
       * this operation has already been replayed in the past.
       */
      String uuid = ctx.getEntryUUID();
      if (findEntryDN(uuid) != null)
      {
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_OPERATION, null);
      }

      /* The parent entry may have been renamed here since the change was done
       * on the first server, and another entry have taken the former dn
       * of the parent entry
       */

      String parentEntryUUID = ctx.getParentEntryUUID();
      // root entry have no parent,
      // there is no need to check for it.
      if (parentEntryUUID != null)
      {
        // There is a potential of perfs improvement here
        // if we could avoid the following parent entry retrieval
        DN parentDnFromCtx = findEntryDN(ctx.getParentEntryUUID());

        if (parentDnFromCtx == null)
        {
          // The parent does not exist with the specified unique id
          // stop the operation with NO_SUCH_OBJECT and let the
          // conflict resolution or the dependency resolution solve this.
          return new SynchronizationProviderResult.StopProcessing(
              ResultCode.NO_SUCH_OBJECT, null);
        }
        else
        {
          DN entryDN = addOperation.getEntryDN();
          DN parentDnFromEntryDn = entryDN.getParentDNInSuffix();
          if ((parentDnFromEntryDn != null)
              && (!parentDnFromCtx.equals(parentDnFromEntryDn)))
          {
            // parentEntry has been renamed
            // replication name conflict resolution is expected to fix that
            // later in the flow
            return new SynchronizationProviderResult.StopProcessing(
                ResultCode.NO_SUCH_OBJECT, null);
          }
        }
      }
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Check that the broker associated to this ReplicationDomain has found
   * a Replication Server and that this LDAP server is therefore able to
   * process operations.
   * If not, set the ResultCode, the response message,
   * interrupt the operation, and return false
   *
   * @return  true when it OK to process the Operation, false otherwise.
   *          When false is returned the resultCode and the response message
   *          is also set in the Operation.
   */
  private boolean brokerIsConnected()
  {
    if (isolationPolicy.equals(IsolationPolicy.ACCEPT_ALL_UPDATES))
    {
      // this policy imply that we always accept updates.
      return true;
    }
    if (isolationPolicy.equals(IsolationPolicy.REJECT_ALL_UPDATES))
    {
      // this isolation policy specifies that the updates are denied
      // when the broker had problems during the connection phase
      // Updates are still accepted if the broker is currently connecting..
      return !hasConnectionError();
    }
    // we should never get there as the only possible policies are
    // ACCEPT_ALL_UPDATES and REJECT_ALL_UPDATES
    return true;
  }


  /**
   * Implement the  handleConflictResolution phase of the ModifyDNOperation.
   *
   * @param modifyDNOperation The ModifyDNOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyDNOperation modifyDNOperation)
  {
    if ((!modifyDNOperation.isSynchronizationOperation())
        && (!brokerIsConnected()))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    if (fractionalConfig.isFractional())
    {
      if (modifyDNOperation.isSynchronizationOperation())
      {
        /*
         * Filter operation here for fractional replication. If fractional
         * replication is enabled, we analyze the operation and modify it if
         * necessary to stay consistent with what is defined in fractional
         * configuration.
         */
        fractionalFilterOperation(modifyDNOperation, true);
      }
      else
      {
        /*
         * Direct access from an LDAP client : something is inconsistent with
         * the fractional configuration, forbid the operation.
         */
        if (fractionalFilterOperation(modifyDNOperation, false))
        {
          StringBuilder sb = new StringBuilder();
          modifyDNOperation.toString(sb);
          Message msg = NOTE_ERR_FRACTIONAL_FORBIDDEN_OPERATION.get(
            baseDn.toString(), sb.toString());
          return new SynchronizationProviderResult.StopProcessing(
            ResultCode.UNWILLING_TO_PERFORM, msg);
        }
      }
    }

    ModifyDnContext ctx =
      (ModifyDnContext) modifyDNOperation.getAttachment(SYNCHROCONTEXT);
    if (ctx != null)
    {
      /*
       * This is a replication operation
       * Check that the modified entry has the same entryuuid
       * as was in the original message.
       */
      String modifiedEntryUUID =
        EntryHistorical.getEntryUUID(modifyDNOperation.getOriginalEntry());
      if (!modifiedEntryUUID.equals(ctx.getEntryUUID()))
      {
        /*
         * The modified entry is not the same entry as the one on
         * the original change was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the change proceed, return a negative
         * result and set the result code to NO_SUCH_OBJECT.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
      }
      if (modifyDNOperation.getNewSuperior() != null)
      {
        /*
         * Also check that the current id of the
         * parent is the same as when the operation was performed.
         */
        String newParentId = findEntryUUID(modifyDNOperation.getNewSuperior());
        if ((newParentId != null) && (ctx.getNewSuperiorEntryUUID() != null) &&
            (!newParentId.equals(ctx.getNewSuperiorEntryUUID())))
        {
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
        }
      }
      /*
       * If the object has been renamed more recently than this
       * operation, cancel the operation.
       */
      EntryHistorical hist = EntryHistorical.newInstanceFromEntry(
          modifyDNOperation.getOriginalEntry());
      if (hist.addedOrRenamedAfter(ctx.getChangeNumber()))
      {
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_OPERATION, null);
      }
    }
    else
    {
      // There is no replication context attached to the operation
      // so this is not a replication operation.
      ChangeNumber changeNumber = generateChangeNumber(modifyDNOperation);
      String newParentId = null;
      if (modifyDNOperation.getNewSuperior() != null)
      {
        newParentId = findEntryUUID(modifyDNOperation.getNewSuperior());
      }

      Entry modifiedEntry = modifyDNOperation.getOriginalEntry();
      String modifiedEntryUUID = EntryHistorical.getEntryUUID(modifiedEntry);
      ctx = new ModifyDnContext(changeNumber, modifiedEntryUUID, newParentId);
      modifyDNOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Handle the conflict resolution.
   * Called by the core server after locking the entry and before
   * starting the actual modification.
   * @param modifyOperation the operation
   * @return code indicating is operation must proceed
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationModifyOperation modifyOperation)
  {
    if ((!modifyOperation.isSynchronizationOperation())
        && (!brokerIsConnected()))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    if (fractionalConfig.isFractional())
    {
      if  (modifyOperation.isSynchronizationOperation())
      {
        /*
         * Filter attributes here for fractional replication. If fractional
         * replication is enabled, we analyze the operation and modify it so
         * that no forbidden attribute is added/modified/deleted in the local
         * backend. This must be called before any other plugin is called, to
         * keep coherency across plugin calls.
         */
        if (fractionalFilterOperation(modifyOperation, true) ==
          FRACTIONAL_BECOME_NO_OP)
        {
          // Every modifications filtered in this operation: the operation
          // becomes a no-op
          return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_OPERATION, null);
        }
      }
      else
      {
        /*
         * Direct access from an LDAP client : if some attributes are to be
         * removed according to the fractional configuration, simply forbid
         * the operation
         */
        switch(fractionalFilterOperation(modifyOperation, false))
        {
          case FRACTIONAL_HAS_NO_FRACTIONAL_FILTERED_ATTRIBUTES:
            // Ok, let the operation happen
            break;
          case FRACTIONAL_HAS_FRACTIONAL_FILTERED_ATTRIBUTES:
            // Some attributes not compliant with fractional configuration :
            // forbid the operation
            StringBuilder sb = new StringBuilder();
            modifyOperation.toString(sb);
            Message msg = NOTE_ERR_FRACTIONAL_FORBIDDEN_OPERATION.get(
              baseDn.toString(), sb.toString());
            return new SynchronizationProviderResult.StopProcessing(
              ResultCode.UNWILLING_TO_PERFORM, msg);
        }
      }
    }

    ModifyContext ctx =
      (ModifyContext) modifyOperation.getAttachment(SYNCHROCONTEXT);

    Entry modifiedEntry = modifyOperation.getModifiedEntry();
    if (ctx == null)
    {
      // No replication ctx attached => not a replicated operation
      // - create a ctx with : changeNumber, entryUUID
      // - attach the context to the op

      ChangeNumber changeNumber = generateChangeNumber(modifyOperation);
      String modifiedEntryUUID = EntryHistorical.getEntryUUID(modifiedEntry);
      ctx = new ModifyContext(changeNumber, modifiedEntryUUID);

      modifyOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    else
    {
      // Replication ctx attached => this is a replicated operation being
      // replayed here, it is necessary to
      // - check if the entry has been renamed
      // - check for conflicts
      String modifiedEntryUUID = ctx.getEntryUUID();
      String currentEntryUUID = EntryHistorical.getEntryUUID(modifiedEntry);
      if ((currentEntryUUID != null) &&
          (!currentEntryUUID.equals(modifiedEntryUUID)))
      {
        /*
         * The current modified entry is not the same entry as the one on
         * the original modification was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the modification proceed, return a negative
         * result and set the result code to NO_SUCH_OBJECT.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
         return new SynchronizationProviderResult.StopProcessing(
              ResultCode.NO_SUCH_OBJECT, null);
      }

      /*
       * Solve the conflicts between modify operations
       */
      EntryHistorical historicalInformation =
        EntryHistorical.newInstanceFromEntry(modifiedEntry);
      modifyOperation.setAttachment(EntryHistorical.HISTORICAL,
                                    historicalInformation);

      if (historicalInformation.replayOperation(modifyOperation, modifiedEntry))
      {
        numResolvedModifyConflicts.incrementAndGet();
      }

    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * The preOperation phase for the add Operation.
   * Its job is to generate the replication context associated to the
   * operation. It is necessary to do it in this phase because contrary to
   * the other operations, the entry UUID is not set when the handleConflict
   * phase is called.
   *
   * @param addOperation The Add Operation.
   */
  public void doPreOperation(PreOperationAddOperation addOperation)
  {
    AddContext ctx = new AddContext(generateChangeNumber(addOperation),
        EntryHistorical.getEntryUUID(addOperation),
        findEntryUUID(addOperation.getEntryDN().getParentDNInSuffix()));

    addOperation.setAttachment(SYNCHROCONTEXT, ctx);
  }

  /**
   * Check if an operation must be synchronized.
   * Also update the list of pending changes and the server RUV
   * @param op the operation
   */
  public void synchronize(PostOperationOperation op)
  {
    ResultCode result = op.getResultCode();
    if ((result == ResultCode.SUCCESS) && op.isSynchronizationOperation())
    {
      numReplayedPostOpCalled++;
    }
    LDAPUpdateMsg msg = null;

    // Note that a failed non-replication operation might not have a change
    // number.
    ChangeNumber curChangeNumber = OperationContext.getChangeNumber(op);
    if ((curChangeNumber != null) && (logChangeNumber))
    {
      op.addAdditionalLogItem(AdditionalLogItem.unquotedKeyValue(getClass(),
          "replicationCN", curChangeNumber));
    }

    if ((result == ResultCode.SUCCESS) && (!op.isSynchronizationOperation()))
    {
      // Generate a replication message for a successful non-replication
      // operation.
      msg = LDAPUpdateMsg.generateMsg(op);

      if (msg == null)
      {
        /*
         * This is an operation type that we do not know about
         * It should never happen.
         */
        pendingChanges.remove(curChangeNumber);
        Message message =
            ERR_UNKNOWN_TYPE.get(op.getOperationType().toString());
        logError(message);
        return;
      }
    }

    if (result == ResultCode.SUCCESS)
    {
      try
      {
        if (op.isSynchronizationOperation())
        {
          remotePendingChanges.commit(curChangeNumber);
        }
        else
        {
          try
          {
            addEntryAttributesForCL(msg,op);
          }
          catch(Exception e)
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
          // If assured replication is configured, this will prepare blocking
          // mechanism. If assured replication is disabled, this returns
          // immediately
          prepareWaitForAckIfAssuredEnabled(msg);
          try
          {
            msg.encode();
          } catch (UnsupportedEncodingException e)
          {
            // will be caught at publish time.
          }
          pendingChanges.commitAndPushCommittedChanges(curChangeNumber, msg);
        }
      }
      catch  (NoSuchElementException e)
      {
        Message message = ERR_OPERATION_NOT_FOUND_IN_PENDING.get(
            op.toString(), curChangeNumber.toString());
        logError(message);
        return;
      }

      // If the operation is a DELETE on the base entry of the suffix
      // that is replicated, the generation is now lost because the
      // DB is empty. We need to save it again the next time we add an entry.
      if ((op.getOperationType().equals(OperationType.DELETE))
          && (((PostOperationDeleteOperation) op).getEntryDN().equals(baseDn)))
      {
        generationIdSavedStatus = false;
      }

      if (!generationIdSavedStatus)
      {
        this.saveGenerationId(generationId);
      }


      if (!op.isSynchronizationOperation())
      {
        // If assured replication is enabled, this will wait for the matching
        // ack or time out. If assured replication is disabled, this returns
        // immediately
        try
        {
          waitForAckIfAssuredEnabled(msg);
        } catch (TimeoutException ex)
        {
          // This exception may only be raised if assured replication is
          // enabled
          Message errorMsg = NOTE_DS_ACK_TIMEOUT.get(getServiceID(),
            Long.toString(getAssuredTimeout()), msg.toString());
          logError(errorMsg);
        }
      }
    }
    else if (!op.isSynchronizationOperation())
    {
      // Remove an unsuccessful non-replication operation from the pending
      // changes list.
      if (curChangeNumber != null)
      {
        pendingChanges.remove(curChangeNumber);
        pendingChanges.pushCommittedChanges();
      }
    }

    checkForClearedConflict(op);
  }

  /**
   * Check if the operation that just happened has cleared a conflict :
   * Clearing a conflict happens if the operation has free a DN that
   * for which an other entry was in conflict.
   * Steps:
   * - get the DN freed by a DELETE or MODRDN op
   * - search for entries put in the conflict space (dn=entryUUID'+'....)
   *   because the expected DN was not available (ds-sync-conflict=expected DN)
   * - retain the entry with the oldest conflict
   * - rename this entry with the freedDN as it was expected originally
   */
   private void checkForClearedConflict(PostOperationOperation op)
   {
     OperationType type = op.getOperationType();
     if (op.getResultCode() != ResultCode.SUCCESS)
     {
       // those operations cannot have cleared a conflict
       return;
     }

     DN freedDN;
     if (type == OperationType.DELETE)
     {
       freedDN = ((PostOperationDeleteOperation) op).getEntryDN();
     }
     else if (type == OperationType.MODIFY_DN)
     {
       freedDN = ((PostOperationModifyDNOperation) op).getEntryDN();
     }
     else
     {
       return;
     }

    LDAPFilter filter = LDAPFilter.createEqualityFilter(DS_SYNC_CONFLICT,
        ByteString.valueOf(freedDN.toString()));

     LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
     attrs.add(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);
     attrs.add(EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);
     attrs.add("*");
     InternalSearchOperation searchOp =  conn.processSearch(
       ByteString.valueOf(baseDn.toString()),
       SearchScope.WHOLE_SUBTREE,
       DereferencePolicy.NEVER_DEREF_ALIASES,
       0, 0, false, filter,
       attrs, null);

     LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
     Entry entryToRename = null;
     ChangeNumber entryToRenameCN = null;
     for (SearchResultEntry entry : entries)
     {
       EntryHistorical history = EntryHistorical.newInstanceFromEntry(entry);
       if (entryToRename == null)
       {
         entryToRename = entry;
         entryToRenameCN = history.getDNDate();
       }
       else if (!history.addedOrRenamedAfter(entryToRenameCN))
       {
         // this conflict is older than the previous, keep it.
         entryToRename = entry;
         entryToRenameCN = history.getDNDate();
       }
     }

     if (entryToRename != null)
     {
       DN entryDN = entryToRename.getDN();
       ModifyDNOperationBasis newOp = renameEntry(
           entryDN, freedDN.getRDN(), freedDN.getParent(), false);

       ResultCode res = newOp.getResultCode();
       if (res != ResultCode.SUCCESS)
       {
         Message message =
           ERR_COULD_NOT_SOLVE_CONFLICT.get(entryDN.toString(), res.toString());
           logError(message);
       }
     }
   }

  /**
   * Rename an Entry Using a synchronization, non-replicated operation.
   * This method should be used instead of the InternalConnection methods
   * when the operation that need to be run must be local only and therefore
   * not replicated to the RS.
   *
   * @param targetDN     The DN of the entry to rename.
   * @param newRDN       The new RDN to be used.
   * @param parentDN     The parentDN to be used.
   * @param markConflict A boolean indicating is this entry should be marked
   *                     as a conflicting entry. In such case the
   *                     DS_SYNC_CONFLICT attribute will be added to the entry
   *                     with the value of its original DN.
   *                     If false, the DS_SYNC_CONFLICT attribute will be
   *                     cleared.
   *
   * @return The operation that was run to rename the entry.
   */
  private ModifyDNOperationBasis renameEntry(
      DN targetDN, RDN newRDN, DN parentDN, boolean markConflict)
  {
    ModifyDNOperationBasis newOp =
        new ModifyDNOperationBasis(
        conn, InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), new ArrayList<Control>(0),
        targetDN, newRDN, false,
        parentDN);
    newOp.setInternalOperation(true);
    newOp.setSynchronizationOperation(true);
    newOp.setDontSynchronize(true);

    if (markConflict)
    {
      AttributeType attrType =
          DirectoryServer.getAttributeType(DS_SYNC_CONFLICT, true);
      Attribute attr = Attributes.create(attrType, AttributeValues.create(
          attrType, targetDN.toNormalizedString()));
      Modification mod = new Modification(ModificationType.REPLACE, attr);
      newOp.addModification(mod);
    }
    else
    {
      AttributeType attrType =
          DirectoryServer.getAttributeType(DS_SYNC_CONFLICT, true);
      Attribute attr = Attributes.empty(attrType);
      Modification mod = new Modification(ModificationType.DELETE, attr);
      newOp.addModification(mod);
    }

    newOp.run();
    return newOp;
  }

  /**
   * Get the number of updates in the pending list.
   *
   * @return The number of updates in the pending list
   */
  public int getPendingUpdatesCount()
  {
    if (pendingChanges != null)
      return pendingChanges.size();
    else
      return 0;
  }

  /**
   * get the number of updates replayed successfully by the replication.
   *
   * @return The number of updates replayed successfully
   */
  public int getNumReplayedPostOpCalled()
  {
    return numReplayedPostOpCalled;
  }


  /**
   * Delete this ReplicationDomain.
   */
  public void delete()
  {
    shutdown();
    removeECLDomainCfg();
  }

  /**
   * Shutdown this ReplicationDomain.
   */
  public void shutdown()
  {
    if (!shutdown)
    {
      shutdown = true;

      // stop the thread in charge of flushing the ServerState.
      if (flushThread != null)
      {
        synchronized (flushThread)
        {
          flushThread.notify();
        }
      }

      DirectoryServer.deregisterAlertGenerator(this);

      // stop the ReplicationDomain
      stopDomain();
    }

    // wait for completion of the persistentServerState thread.
    try
    {
      while (!done)
      {
        Thread.sleep(50);
      }
    } catch (InterruptedException e)
    {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Create and replay a synchronized Operation from an UpdateMsg.
   *
   * @param msg
   *          The UpdateMsg to be replayed.
   * @param shutdown
   *          whether the server initiated shutdown
   */
  public void replay(LDAPUpdateMsg msg, AtomicBoolean shutdown)
  {
    Operation op = null;
    boolean replayDone = false;
    boolean dependency = false;
    ChangeNumber changeNumber = null;
    int retryCount = 10;

    // Try replay the operation, then flush (replaying) any pending operation
    // whose dependency has been replayed until no more left.
    do
    {
      String replayErrorMsg = null;
      try
      {
        op = msg.createOperation(conn);
        dependency = remotePendingChanges.checkDependencies(op, msg);

        while ((!dependency) && (!replayDone) && (retryCount-- > 0))
        {
          if (shutdown.get())
          {
            // shutdown initiated, let's leave
            return;
          }
          // Try replay the operation
          op.setInternalOperation(true);
          op.setSynchronizationOperation(true);

          // Always add the ManageDSAIT control so that updates to referrals
          // are processed locally.
          op.addRequestControl(new LDAPControl(OID_MANAGE_DSAIT_CONTROL));

          changeNumber = OperationContext.getChangeNumber(op);
          op.run();

          ResultCode result = op.getResultCode();

          if (result != ResultCode.SUCCESS)
          {
            if (result == ResultCode.NO_OPERATION)
            {
              // Pre-operation conflict resolution detected that the operation
              // was a no-op. For example, an add which has already been
              // replayed, or a modify DN operation on an entry which has been
              // renamed by a more recent modify DN.
              replayDone = true;
            }
            else if (result == ResultCode.BUSY)
            {
              /*
               * We probably could not get a lock (OPENDJ-885). Give the server
               * another chance to process this operation immediately.
               */
              Thread.yield();
              continue;
            }
            else if (result == ResultCode.UNAVAILABLE)
            {
              /*
               * It can happen when a rebuild is performed or the backend is
               * offline (OPENDJ-49). Give the server another chance to process
               * this operation after some time.
               */
              Thread.sleep(50);
              continue;
            }
            else if (op instanceof ModifyOperation)
            {
              ModifyOperation newOp = (ModifyOperation) op;
              dependency = remotePendingChanges
                  .checkDependencies(newOp);
              ModifyMsg modifyMsg = (ModifyMsg) msg;
              replayDone = solveNamingConflict(newOp, modifyMsg);
            }
            else if (op instanceof DeleteOperation)
            {
              DeleteOperation newOp = (DeleteOperation) op;
              dependency = remotePendingChanges
                  .checkDependencies(newOp);
              replayDone = solveNamingConflict(newOp, msg);
            }
            else if (op instanceof AddOperation)
            {
              AddOperation newOp = (AddOperation) op;
              AddMsg addMsg = (AddMsg) msg;
              dependency = remotePendingChanges
                  .checkDependencies(newOp);
              replayDone = solveNamingConflict(newOp, addMsg);
            }
            else if (op instanceof ModifyDNOperationBasis)
            {
              ModifyDNOperationBasis newOp = (ModifyDNOperationBasis) op;
              replayDone = solveNamingConflict(newOp, msg);
            }
            else
            {
              replayDone = true; // unknown type of operation ?!
            }

            if (replayDone)
            {
              // the update became a dummy update and the result
              // of the conflict resolution phase is to do nothing.
              // however we still need to push this change to the serverState
              updateError(changeNumber);
            }
            else
            {
              /*
               * Create a new operation as the ConflictResolution
               * different operation.
               *  Note: When msg is a DeleteMsg, the DeleteOperation is properly
               *  created with subtreeDelete request control when needed.
               */
              op = msg.createOperation(conn);
            }
          }
          else
          {
            replayDone = true;
          }
        }

        if (!replayDone && !dependency)
        {
          // Continue with the next change but the servers could now become
          // inconsistent.
          // Let the repair tool know about this.
          Message message = ERR_LOOP_REPLAYING_OPERATION.get(op.toString(),
            op.getErrorMessage().toString());
          logError(message);
          numUnresolvedNamingConflicts.incrementAndGet();
          replayErrorMsg = message.toString();
          updateError(changeNumber);
        }
      } catch (ASN1Exception e)
      {
        replayErrorMsg = logDecodingOperationError(msg, e);
      } catch (LDAPException e)
      {
        replayErrorMsg = logDecodingOperationError(msg, e);
      } catch (DataFormatException e)
      {
        replayErrorMsg = logDecodingOperationError(msg, e);
      } catch (Exception e)
      {
        if (changeNumber != null)
        {
          /*
           * An Exception happened during the replay process.
           * Continue with the next change but the servers will now start
           * to be inconsistent.
           * Let the repair tool know about this.
           */
          Message message = ERR_EXCEPTION_REPLAYING_OPERATION.get(
            stackTraceToSingleLineString(e), op.toString());
          logError(message);
          replayErrorMsg = message.toString();
          updateError(changeNumber);
        } else
        {
          replayErrorMsg = logDecodingOperationError(msg, e);
        }
      } finally
      {
        if (!dependency)
        {
          processUpdateDone(msg, replayErrorMsg);
        }
      }

      // Now replay any pending update that had a dependency and whose
      // dependency has been replayed, do that until no more updates of that
      // type left...
      msg = remotePendingChanges.getNextUpdate();

      // Prepare restart of loop
      replayDone = false;
      dependency = false;
      changeNumber = null;
      retryCount = 10;

    } while (msg != null);
  }

  private String logDecodingOperationError(LDAPUpdateMsg msg, Exception e)
  {
    Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
      String.valueOf(msg) + stackTraceToSingleLineString(e));
    logError(message);
    return message.toString();
  }

  /**
   * This method is called when an error happens while replaying
   * an operation.
   * It is necessary because the postOperation does not always get
   * called when error or Exceptions happen during the operation replay.
   *
   * @param changeNumber the ChangeNumber of the operation with error.
   */
  public void updateError(ChangeNumber changeNumber)
  {
    try
    {
      remotePendingChanges.commit(changeNumber);
    }
    catch (NoSuchElementException e)
    {
      // A failure occurred after the change had been removed from the pending
      // changes table.
      if (debugEnabled())
      {
        TRACER
            .debugInfo(
                "LDAPReplicationDomain.updateError: Unable to find remote "
                    + "pending change for change number %s",
                changeNumber);
      }
    }
  }

  /**
   * Generate a new change number and insert it in the pending list.
   *
   * @param operation The operation for which the change number must be
   *                  generated.
   * @return The new change number.
   */
  private ChangeNumber generateChangeNumber(PluginOperation operation)
  {
    return pendingChanges.putLocalOperation(operation);
  }


  /**
   * Find the Unique Id of the entry with the provided DN by doing a
   * search of the entry and extracting its entryUUID from its attributes.
   *
   * @param dn The dn of the entry for which the unique Id is searched.
   *
   * @return The unique Id of the entry with the provided DN.
   */
  static String findEntryUUID(DN dn)
  {
    if (dn == null)
      return null;
    try
    {
      InternalClientConnection conn =
                InternalClientConnection.getRootConnection();
      LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
      attrs.add(ENTRYUUID_ATTRIBUTE_NAME);
      InternalSearchOperation search = conn.processSearch(dn,
            SearchScope.BASE_OBJECT, DereferencePolicy.NEVER_DEREF_ALIASES,
            0, 0, false,
            SearchFilter.createFilterFromString("objectclass=*"),
            attrs);

      if (search.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> result = search.getSearchEntries();
        if (!result.isEmpty())
        {
          SearchResultEntry resultEntry = result.getFirst();
          if (resultEntry != null)
          {
            return EntryHistorical.getEntryUUID(resultEntry);
          }
        }
      }
    } catch (DirectoryException e)
    {
      // never happens because the filter is always valid.
    }
    return null;
  }

  /**
   * find the current DN of an entry from its entry UUID.
   *
   * @param uuid the Entry Unique ID.
   * @return The current DN of the entry or null if there is no entry with
   *         the specified UUID.
   */
  private DN findEntryDN(String uuid)
  {
    try
    {
      InternalSearchOperation search = conn.processSearch(baseDn,
            SearchScope.WHOLE_SUBTREE,
            SearchFilter.createFilterFromString("entryuuid="+uuid));
      if (search.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> result = search.getSearchEntries();
        if (!result.isEmpty())
        {
          SearchResultEntry resultEntry = result.getFirst();
          if (resultEntry != null)
          {
            return resultEntry.getDN();
          }
        }
      }
    } catch (DirectoryException e)
    {
      // never happens because the filter is always valid.
    }
    return null;
  }

  /**
   * Solve a conflict detected when replaying a modify operation.
   *
   * @param op The operation that triggered the conflict detection.
   * @param msg The operation that triggered the conflict detection.
   * @return true if the process is completed, false if it must continue..
   */
  private boolean solveNamingConflict(ModifyOperation op,
      ModifyMsg msg)
  {
    ResultCode result = op.getResultCode();
    ModifyContext ctx = (ModifyContext) op.getAttachment(SYNCHROCONTEXT);
    String entryUUID = ctx.getEntryUUID();

    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      /*
       * The operation is a modification but
       * the entry has been renamed on a different master in the same time.
       * search if the entry has been renamed, and return the new dn
       * of the entry.
       */
      DN newDN = findEntryDN(entryUUID);
      if (newDN != null)
      {
        // There is an entry with the same unique id as this modify operation
        // replay the modify using the current dn of this entry.
        msg.setDn(newDN.toString());
        numResolvedNamingConflicts.incrementAndGet();
        return false;
      }
      else
      {
        // This entry does not exist anymore.
        // It has probably been deleted, stop the processing of this operation
        numResolvedNamingConflicts.incrementAndGet();
        return true;
      }
    }
    else if (result == ResultCode.NOT_ALLOWED_ON_RDN)
    {
      DN currentDN = findEntryDN(entryUUID);
      RDN currentRDN;
      if (currentDN != null)
      {
        currentRDN = currentDN.getRDN();
      }
      else
      {
        // The entry does not exist anymore.
        numResolvedNamingConflicts.incrementAndGet();
        return true;
      }

      // The modify operation is trying to delete the value that is
      // currently used in the RDN. We need to alter the modify so that it does
      // not remove the current RDN value(s).

      List<Modification> mods = op.getModifications();
      for (Modification mod : mods)
      {
        AttributeType modAttrType = mod.getAttribute().getAttributeType();
        if ((mod.getModificationType() == ModificationType.DELETE) ||
            (mod.getModificationType() == ModificationType.REPLACE))
        {
          if (currentRDN.hasAttributeType(modAttrType))
          {
            // the attribute can't be deleted because it is used
            // in the RDN, turn this operation is a replace with the
            // current RDN value(s);
            mod.setModificationType(ModificationType.REPLACE);
            Attribute newAttribute = mod.getAttribute();
            AttributeBuilder attrBuilder = new AttributeBuilder(newAttribute);
            attrBuilder.add(currentRDN.getAttributeValue(modAttrType));
            mod.setAttribute(attrBuilder.toAttribute());
          }
        }
      }
      msg.setMods(mods);
      numResolvedNamingConflicts.incrementAndGet();
      return false;
    }
    else
    {
      // The other type of errors can not be caused by naming conflicts.
      // Log a message for the repair tool.
      Message message = ERR_ERROR_REPLAYING_OPERATION.get(
          op.toString(), ctx.getChangeNumber().toString(),
          result.toString(), op.getErrorMessage().toString());
      logError(message);
      return true;
    }
  }

 /**
  * Solve a conflict detected when replaying a delete operation.
  *
  * @param op The operation that triggered the conflict detection.
  * @param msg The operation that triggered the conflict detection.
  * @return true if the process is completed, false if it must continue..
  */
 private boolean solveNamingConflict(DeleteOperation op,
     LDAPUpdateMsg msg)
 {
   ResultCode result = op.getResultCode();
   DeleteContext ctx = (DeleteContext) op.getAttachment(SYNCHROCONTEXT);
   String entryUUID = ctx.getEntryUUID();

   if (result == ResultCode.NO_SUCH_OBJECT)
   {
     /*
      * Find if the entry is still in the database.
      */
     DN currentDn = findEntryDN(entryUUID);
     if (currentDn == null)
     {
       /*
        * The entry has already been deleted, either because this delete
        * has already been replayed or because another concurrent delete
        * has already done the job.
        * In any case, there is is nothing more to do.
        */
       numResolvedNamingConflicts.incrementAndGet();
       return true;
     }
     else
     {
       /*
        * This entry has been renamed, replay the delete using its new DN.
        */
       msg.setDn(currentDn.toString());
       numResolvedNamingConflicts.incrementAndGet();
       return false;
     }
   }
   else if (result == ResultCode.NOT_ALLOWED_ON_NONLEAF)
   {
     /*
      * This may happen when we replay a DELETE done on a master
      * but children of this entry have been added on another master.
      *
      * Rename all the children by adding entryuuid in dn and delete this entry.
      *
      * The action taken here must be consistent with the actions
      * done in the solveNamingConflict(AddOperation) method
      * when we are adding an entry whose parent entry has already been deleted.
      *
      */
     if (findAndRenameChild(entryUUID, op.getEntryDN(), op))
       numUnresolvedNamingConflicts.incrementAndGet();

     return false;
   }
   else
   {
     // The other type of errors can not be caused by naming conflicts.
     // Log a message for the repair tool.
     Message message = ERR_ERROR_REPLAYING_OPERATION.get(
         op.toString(), ctx.getChangeNumber().toString(),
         result.toString(), op.getErrorMessage().toString());
     logError(message);
     return true;
   }
 }

  /**
 * Solve a conflict detected when replaying a Modify DN operation.
 *
 * @param op The operation that triggered the conflict detection.
 * @param msg The operation that triggered the conflict detection.
 * @return true if the process is completed, false if it must continue.
 * @throws Exception When the operation is not valid.
 */
private boolean solveNamingConflict(ModifyDNOperation op,
    LDAPUpdateMsg msg) throws Exception
{
  ResultCode result = op.getResultCode();
  ModifyDnContext ctx = (ModifyDnContext) op.getAttachment(SYNCHROCONTEXT);
  String entryUUID = ctx.getEntryUUID();
  String newSuperiorID = ctx.getNewSuperiorEntryUUID();

  /*
   * four possible cases :
   * - the modified entry has been renamed
   * - the new parent has been renamed
   * - the operation is replayed for the second time.
   * - the entry has been deleted
   * action :
   *  - change the target dn and the new parent dn and
   *        restart the operation,
   *  - don't do anything if the operation is replayed.
   */

  // get the current DN of this entry in the database.
  DN currentDN = findEntryDN(entryUUID);

  // Construct the new DN to use for the entry.
  DN entryDN = op.getEntryDN();
  DN newSuperior;
  RDN newRDN = op.getNewRDN();

  if (newSuperiorID != null)
  {
    newSuperior = findEntryDN(newSuperiorID);
  }
  else
  {
    newSuperior = entryDN.getParent();
  }

  //If we could not find the new parent entry, we missed this entry
  // earlier or it has disappeared from the database
  // Log this information for the repair tool and mark the entry
  // as conflicting.
  // stop the processing.
  if (newSuperior == null)
  {
    markConflictEntry(op, currentDN, currentDN.getParent().concat(newRDN));
    numUnresolvedNamingConflicts.incrementAndGet();
    return true;
  }

  DN newDN = newSuperior.concat(newRDN);

  if (currentDN == null)
  {
    // The entry targeted by the Modify DN is not in the database
    // anymore.
    // This is a conflict between a delete and this modify DN.
    // The entry has been deleted, we can safely assume
    // that the operation is completed.
    numResolvedNamingConflicts.incrementAndGet();
    return true;
  }

  // if the newDN and the current DN match then the operation
  // is a no-op (this was probably a second replay)
  // don't do anything.
  if (newDN.equals(currentDN))
  {
    numResolvedNamingConflicts.incrementAndGet();
    return true;
  }

  if ((result == ResultCode.NO_SUCH_OBJECT) ||
      (result == ResultCode.UNWILLING_TO_PERFORM) ||
      (result == ResultCode.OBJECTCLASS_VIOLATION))
  {
    /*
     * The entry or it's new parent has not been found
     * reconstruct the operation with the DN we just built
     */
    ModifyDNMsg modifyDnMsg = (ModifyDNMsg) msg;
    msg.setDn(currentDN.toString());
    modifyDnMsg.setNewSuperior(newSuperior.toString());
    numResolvedNamingConflicts.incrementAndGet();
    return false;
  }
  else if (result == ResultCode.ENTRY_ALREADY_EXISTS)
  {
    /*
     * This may happen when two modifyDn operation
     * are done on different servers but with the same target DN
     * add the conflict object class to the entry
     * and rename it using its entryuuid.
     */
    ModifyDNMsg modifyDnMsg = (ModifyDNMsg) msg;
    markConflictEntry(op, op.getEntryDN(), newDN);
    modifyDnMsg.setNewRDN(generateConflictRDN(entryUUID,
                          modifyDnMsg.getNewRDN()));
    modifyDnMsg.setNewSuperior(newSuperior.toString());
    numUnresolvedNamingConflicts.incrementAndGet();
    return false;
  }
  else
  {
    // The other type of errors can not be caused by naming conflicts.
    // Log a message for the repair tool.
    Message message = ERR_ERROR_REPLAYING_OPERATION.get(
        op.toString(), ctx.getChangeNumber().toString(),
        result.toString(), op.getErrorMessage().toString());
    logError(message);
    return true;
  }
}


  /**
   * Solve a conflict detected when replaying a ADD operation.
   *
   * @param op The operation that triggered the conflict detection.
   * @param msg The message that triggered the conflict detection.
   * @return true if the process is completed, false if it must continue.
   * @throws Exception When the operation is not valid.
   */
  private boolean solveNamingConflict(AddOperation op,
      AddMsg msg) throws Exception
  {
    ResultCode result = op.getResultCode();
    AddContext ctx = (AddContext) op.getAttachment(SYNCHROCONTEXT);
    String entryUUID = ctx.getEntryUUID();
    String parentUniqueId = ctx.getParentEntryUUID();

    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      /*
       * This can happen if the parent has been renamed or deleted
       * find the parent dn and calculate a new dn for the entry
       */
      if (parentUniqueId == null)
      {
        /*
         * This entry is the base dn of the backend.
         * It is quite surprising that the operation result be NO_SUCH_OBJECT.
         * There is nothing more we can do except log a
         * message for the repair tool to look at this problem.
         * TODO : Log the message
         */
        return true;
      }
      DN parentDn = findEntryDN(parentUniqueId);
      if (parentDn == null)
      {
        /*
         * The parent has been deleted
         * rename the entry as a conflicting entry.
         * The action taken here must be consistent with the actions
         * done when in the solveNamingConflict(DeleteOperation) method
         * when we are deleting an entry that have some child entries.
         */
        addConflict(msg);

        msg.setDn(generateConflictRDN(entryUUID,
                    op.getEntryDN().getRDN().toString()) + ","
                    + baseDn);
        // reset the parent entryUUID so that the check done is the
        // handleConflict phase does not fail.
        msg.setParentEntryUUID(null);
        numUnresolvedNamingConflicts.incrementAndGet();
        return false;
      }
      else
      {
        RDN entryRdn = DN.decode(msg.getDn()).getRDN();
        msg.setDn(entryRdn + "," + parentDn);
        numResolvedNamingConflicts.incrementAndGet();
        return false;
      }
    }
    else if (result == ResultCode.ENTRY_ALREADY_EXISTS)
    {
      /*
       * This can happen if
       *  - two adds are done on different servers but with the
       *    same target DN.
       *  - the same ADD is being replayed for the second time on this server.
       * if the entryUUID already exist, assume this is a replay and
       *        don't do anything
       * if the entry unique id do not exist, generate conflict.
       */
      if (findEntryDN(entryUUID) != null)
      {
        // entry already exist : this is a replay
        return true;
      }
      else
      {
        addConflict(msg);
        msg.setDn(generateConflictRDN(entryUUID, msg.getDn()));
        numUnresolvedNamingConflicts.incrementAndGet();
        return false;
      }
    }
    else
    {
      // The other type of errors can not be caused by naming conflicts.
      // log a message for the repair tool.
      Message message = ERR_ERROR_REPLAYING_OPERATION.get(
          op.toString(), ctx.getChangeNumber().toString(),
          result.toString(), op.getErrorMessage().toString());
      logError(message);
      return true;
    }
  }

  /**
   * Find all the entries below the provided DN and rename them
   * so that they stay below the baseDn of this replicationDomain and
   * use the conflicting name and attribute.
   *
   * @param entryUUID   The unique ID of the entry whose child must be renamed.
   * @param entryDN    The DN of the entry whose child must be renamed.
   * @param conflictOp The Operation that generated the conflict.
   */
  private boolean findAndRenameChild(
      String entryUUID, DN entryDN, Operation conflictOp)
  {
    boolean conflict = false;

    // Find an rename child entries.
    try
    {
      LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
      attrs.add(ENTRYUUID_ATTRIBUTE_NAME);
      attrs.add(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);

      InternalSearchOperation op =
          conn.processSearch(entryDN, SearchScope.SINGLE_LEVEL,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              SearchFilter.createFilterFromString("(objectClass=*)"),
              attrs);

      if (op.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> entries = op.getSearchEntries();
        if (entries != null)
        {
          for (SearchResultEntry entry : entries)
          {
            /*
             * Check the ADD and ModRDN date of the child entry (All of them,
             * not only the one that are newer than the DEL op)
             * and keep the entry as a conflicting entry,
             */
            conflict = true;
            renameConflictEntry(conflictOp, entry.getDN(),
                EntryHistorical.getEntryUUID(entry));
          }
        }
      }
      else
      {
        // log error and information for the REPAIR tool.
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
        mb.append(String.valueOf(entryDN));
        mb.append(" ");
        mb.append(String.valueOf(conflictOp));
        mb.append(" ");
        mb.append(String.valueOf(op.getResultCode()));
        logError(mb.toMessage());
      }
    } catch (DirectoryException e)
    {
      // log error and information for the REPAIR tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_EXCEPTION_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(entryDN));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(e.getLocalizedMessage());
      logError(mb.toMessage());
    }

    return conflict;
  }


  /**
   * Rename an entry that was conflicting so that it stays below the
   * baseDn of the replicationDomain.
   *
   * @param conflictOp The Operation that caused the conflict.
   * @param dn         The DN of the entry to be renamed.
   * @param entryUUID        The uniqueID of the entry to be renamed.
   */
  private void renameConflictEntry(Operation conflictOp, DN dn,
      String entryUUID)
  {
    Message alertMessage = NOTE_UNRESOLVED_CONFLICT.get(dn.toString());
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT, alertMessage);

    ModifyDNOperation newOp =
      renameEntry(dn, generateDeleteConflictDn(entryUUID, dn), baseDn, true);

    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      // log information for the repair tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(dn));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
    }
  }


  /**
   * Generate a modification to add the conflict attribute to an entry
   * whose Dn is now conflicting with another entry.
   *
   * @param op        The operation causing the conflict.
   * @param currentDN The current DN of the operation to mark as conflicting.
   * @param conflictDN     The newDn on which the conflict happened.
   */
  private void markConflictEntry(Operation op, DN currentDN, DN conflictDN)
  {
    // create new internal modify operation and run it.
    AttributeType attrType = DirectoryServer.getAttributeType(DS_SYNC_CONFLICT,
        true);
    Attribute attr = Attributes.create(attrType, AttributeValues.create(
        attrType, conflictDN.toNormalizedString()));
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);

    ModifyOperationBasis newOp =
      new ModifyOperationBasis(
          conn, InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(), new ArrayList<Control>(0),
          currentDN, mods);
    newOp.setInternalOperation(true);
    newOp.setSynchronizationOperation(true);
    newOp.setDontSynchronize(true);

    newOp.run();

    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      // Log information for the repair tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_ADD_CONFLICT_ATTRIBUTE.get());
      mb.append(String.valueOf(op));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
    }

    // Generate an alert to let the administration know that some
    // conflict could not be solved.
    Message alertMessage = NOTE_UNRESOLVED_CONFLICT.get(conflictDN.toString());
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT, alertMessage);
  }

  /**
   * Add the conflict attribute to an entry that could
   * not be added because it is conflicting with another entry.
   *
   * @param msg            The conflicting Add Operation.
   *
   * @throws ASN1Exception When an encoding error happened manipulating the
   *                       msg.
   */
  private void addConflict(AddMsg msg) throws ASN1Exception
  {
    String normalizedDN;
    try
    {
      normalizedDN = DN.decode(msg.getDn()).toNormalizedString();
    } catch (DirectoryException e)
    {
      normalizedDN = msg.getDn();
    }

    // Generate an alert to let the administrator know that some
    // conflict could not be solved.
    Message alertMessage = NOTE_UNRESOLVED_CONFLICT.get(normalizedDN);
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT, alertMessage);

    // Add the conflict attribute
    msg.addAttribute(DS_SYNC_CONFLICT, normalizedDN);
  }

  /**
   * Generate the Dn to use for a conflicting entry.
   *
   * @param entryUUID The unique identifier of the entry involved in the
   * conflict.
   * @param rdn Original rdn.
   * @return The generated RDN for a conflicting entry.
   */
  private String generateConflictRDN(String entryUUID, String rdn)
  {
    return "entryuuid=" + entryUUID + "+" + rdn;
  }

  /**
   * Generate the RDN to use for a conflicting entry whose father was deleted.
   *
   * @param entryUUID The unique identifier of the entry involved in the
   *                 conflict.
   * @param dn       The original DN of the entry.
   *
   * @return The generated RDN for a conflicting entry.
   */
  private RDN generateDeleteConflictDn(String entryUUID, DN dn)
  {
    String newRDN =  "entryuuid=" + entryUUID + "+" + dn.getRDN();
    RDN rdn = null;
    try
    {
      rdn = RDN.decode(newRDN);
    } catch (DirectoryException e)
    {
      // cannot happen
    }
    return rdn;
  }

  /**
   * Get the number of modify conflicts successfully resolved.
   * @return The number of modify conflicts successfully resolved.
   */
  public int getNumResolvedModifyConflicts()
  {
    return numResolvedModifyConflicts.get();
  }

  /**
   * Get the number of naming conflicts successfully resolved.
   * @return The number of naming conflicts successfully resolved.
   */
  public int getNumResolvedNamingConflicts()
  {
    return numResolvedNamingConflicts.get();
  }

  /**
   * Get the number of unresolved conflicts.
   * @return The number of unresolved conflicts.
   */
  public int getNumUnresolvedNamingConflicts()
  {
    return numUnresolvedNamingConflicts.get();
  }

  /**
   * Check if the domain solve conflicts.
   *
   * @return a boolean indicating if the domain should solve conflicts.
   */
  public boolean solveConflict()
  {
    return solveConflictFlag;
  }

  /**
   * Disable the replication on this domain.
   * The session to the replication server will be stopped.
   * The domain will not be destroyed but call to the pre-operation
   * methods will result in failure.
   * The listener thread will be destroyed.
   * The monitor informations will still be accessible.
   */
  public void disable()
  {
    state.save();
    state.clearInMemory();
    disabled = true;
    disableService(); // This will cut the session and wake up the listener
  }

  /**
   * Do what necessary when the data have changed : load state, load
   * generation Id.
   * If there is no such information check if there is a
   * ReplicaUpdateVector entry and translate it into a state
   * and generationId.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void loadDataState()
  throws DirectoryException
  {
    state.clearInMemory();
    state.loadState();

    generator.adjust(state.getMaxChangeNumber(serverId));
    // Retrieves the generation ID associated with the data imported

    generationId = loadGenerationId();
  }

  /**
   * Enable back the domain after a previous disable.
   * The domain will connect back to a replication Server and
   * will recreate threads to listen for messages from the Synchronization
   * server.
   * The generationId will be retrieved or computed if necessary.
   * The ServerState will also be read again from the local database.
   */
  public void enable()
  {
    try
    {
      loadDataState();
    }
    catch (Exception e)
    {
      /* TODO should mark that replicationServer service is
       * not available, log an error and retry upon timeout
       * should we stop the modifications ?
       */
      logError(ERR_LOADING_GENERATION_ID.get(
          baseDn.toNormalizedString(), e.getLocalizedMessage()));
      return;
    }

    enableService();

    disabled = false;
  }

  /**
   * Compute the data generationId associated with the current data present
   * in the backend for this domain.
   * @return The computed generationId.
   * @throws DirectoryException When an error occurs.
   */
  public long computeGenerationId() throws DirectoryException
  {
    long genId = exportBackend(null, true);

    if (debugEnabled())
      TRACER.debugInfo("Computed generationId: generationId=" + genId);

    return genId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public long getGenerationID()
  {
    return generationId;
  }


  /**
   * Run a modify operation to update the entry whose DN is given as
   * a parameter with the generationID information.
   *
   * @param entryDN       The DN of the entry to be updated.
   * @param generationId  The value of the generationID to be saved.
   *
   * @return A ResultCode indicating if the operation was successful.
   */
  private ResultCode runSaveGenerationId(DN entryDN, long generationId)
  {
    // The generationId is stored in the root entry of the domain.
    ByteString asn1BaseDn = ByteString.valueOf(entryDN.toString());

    ArrayList<ByteString> values = new ArrayList<ByteString>();
    ByteString value = ByteString.valueOf(Long.toString(generationId));
    values.add(value);

    LDAPAttribute attr =
      new LDAPAttribute(REPLICATION_GENERATION_ID, values);
    LDAPModification mod = new LDAPModification(ModificationType.REPLACE, attr);
    ArrayList<RawModification> mods = new ArrayList<RawModification>(1);
    mods.add(mod);

    ModifyOperationBasis op =
      new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          new ArrayList<Control>(0), asn1BaseDn,
          mods);
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);

    op.run();

    return op.getResultCode();
  }

  /**
   * Stores the value of the generationId.
   * @param generationId The value of the generationId.
   * @return a ResultCode indicating if the method was successful.
   */
  public ResultCode saveGenerationId(long generationId)
  {
    ResultCode result = runSaveGenerationId(baseDn, generationId);

    if (result != ResultCode.SUCCESS)
    {
      generationIdSavedStatus = false;
      if (result == ResultCode.NO_SUCH_OBJECT)
      {
        // If the base entry does not exist, save the generation
        // ID in the config entry
        result = runSaveGenerationId(configDn, generationId);
      }

      if (result != ResultCode.SUCCESS)
      {
        Message message = ERR_UPDATING_GENERATION_ID.get(
            result.getResultCodeName() + " " ,
            baseDn.toString());
        logError(message);
      }
    }
    else
    {
      generationIdSavedStatus = true;
    }
    return result;
  }


  /**
   * Load the GenerationId from the root entry of the domain
   * from the REPLICATION_GENERATION_ID attribute in database
   * to memory, or compute it if not found.
   *
   * @return generationId The retrieved value of generationId
   * @throws DirectoryException When an error occurs.
   */
  private long loadGenerationId()
  throws DirectoryException
  {
    long aGenerationId=-1;

    if (debugEnabled())
      TRACER.debugInfo(
          "Attempt to read generation ID from DB " + baseDn.toString());

    ByteString asn1BaseDn = ByteString.valueOf(baseDn.toString());
    boolean found = false;
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    }
    catch (LDAPException e)
    {
      // can not happen
      return -1;
    }

    /*
     * Search the database entry that is used to periodically
     * save the generation id
     */
    LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
    attributes.add(REPLICATION_GENERATION_ID);
    InternalSearchOperation search = conn.processSearch(asn1BaseDn,
        SearchScope.BASE_OBJECT,
        DereferencePolicy.DEREF_ALWAYS, 0, 0, false,
        filter,attributes);
    if (search.getResultCode() == ResultCode.NO_SUCH_OBJECT)
    {
      // if the base entry does not exist look for the generationID
      // in the config entry.
      asn1BaseDn = ByteString.valueOf(baseDn.toString());
      search = conn.processSearch(asn1BaseDn,
          SearchScope.BASE_OBJECT,
          DereferencePolicy.DEREF_ALWAYS, 0, 0, false,
          filter,attributes);
    }
    if (search.getResultCode() != ResultCode.SUCCESS)
    {
      if (search.getResultCode() != ResultCode.NO_SUCH_OBJECT)
      { // This is an error.
        Message message = ERR_SEARCHING_GENERATION_ID.get(
            search.getResultCode().getResultCodeName() + " " +
            search.getErrorMessage(),
            baseDn.toString());
        logError(message);
      }
    }
    else
    {
      LinkedList<SearchResultEntry> result = search.getSearchEntries();
      SearchResultEntry resultEntry = result.getFirst();
      if (resultEntry != null)
      {
        AttributeType synchronizationGenIDType =
          DirectoryServer.getAttributeType(REPLICATION_GENERATION_ID);
        List<Attribute> attrs =
          resultEntry.getAttribute(synchronizationGenIDType);
        if (attrs != null)
        {
          Attribute attr = attrs.get(0);
          if (attr.size()>1)
          {
            Message message = ERR_LOADING_GENERATION_ID.get(
                baseDn.toString(), "#Values=" + attr.size() +
                " Must be exactly 1 in entry " +
                resultEntry.toLDIFString());
            logError(message);
          }
          else if (attr.size() == 1)
          {
            found=true;
            try
            {
              aGenerationId = Long.decode(attr.iterator().next().toString());
            }
            catch(Exception e)
            {
              Message message = ERR_LOADING_GENERATION_ID.get(
                baseDn.toString(), e.getLocalizedMessage());
              logError(message);
            }
          }
        }
      }
    }

    if (!found)
    {
      aGenerationId = computeGenerationId();
      saveGenerationId(aGenerationId);

      if (debugEnabled())
        TRACER.debugInfo("Generation ID created for domain base DN=" +
            baseDn.toString() +
            " generationId=" + aGenerationId);
    }
    else
    {
      generationIdSavedStatus = true;
      if (debugEnabled())
        TRACER.debugInfo(
            "Generation ID successfully read from domain base DN=" + baseDn +
            " generationId=" + aGenerationId);
    }
    return aGenerationId;
  }

  /**
   * Do whatever is needed when a backup is started.
   * We need to make sure that the serverState is correctly save.
   */
  public void backupStart()
  {
    state.save();
  }

  /**
   * Do whatever is needed when a backup is finished.
   */
  public void backupEnd()
  {
    // Nothing is needed at the moment
  }

  /*
   * Total Update >>
   */



  /**
   * Clears all the entries from the JE backend determined by the
   * be id passed into the method.
   *
   * @param  createBaseEntry  Indicate whether to automatically create the base
   *                          entry and add it to the backend.
   * @param beID  The be id to clear.
   * @param dn   The suffix of the backend to create if the the createBaseEntry
   *             boolean is true.
   * @throws Exception  If an unexpected problem occurs.
   */
  public static void clearJEBackend(boolean createBaseEntry, String beID,
      String dn) throws Exception
  {
    BackendImpl backend = (BackendImpl)DirectoryServer.getBackend(beID);

    // FIXME Should setBackendEnabled be part of TaskUtils ?
    TaskUtils.disableBackend(beID);

    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();

      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        throw new RuntimeException(failureReason.toString());
      }

      try
      {
        backend.clearBackend();
      }
      finally
      {
        LockFileManager.releaseLock(lockFile, failureReason);
      }
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    if (createBaseEntry)
    {
      DN baseDN = DN.decode(dn);
      Entry e = createEntry(baseDN);
      backend = (BackendImpl)DirectoryServer.getBackend(beID);
      backend.addEntry(e, null);
    }
  }

  /**
   * This method trigger an export of the replicated data.
   *
   * @param output               The OutputStream where the export should
   *                             be produced.
   * @throws DirectoryException  When needed.
   */
  @Override
  protected void exportBackend(OutputStream output) throws DirectoryException
  {
    exportBackend(output, false);
  }

  /**
   * Export the entries from the backend and/or compute the generation ID.
   * The ieContext must have been set before calling.
   *
   * @param output              The OutputStream where the export should
   *                            be produced.
   * @param checksumOutput      A boolean indicating if this export is
   *                            invoked to perform a checksum only
   *
   * @return The computed       GenerationID.
   *
   * @throws DirectoryException when an error occurred
   */
  protected long exportBackend(OutputStream output, boolean checksumOutput)
  throws DirectoryException
  {
    long genID = 0;
    Backend backend = retrievesBackend(this.baseDn);
    long numberOfEntries = backend.numSubordinates(baseDn, true) + 1;
    long entryCount = ( (numberOfEntries < 1000 )? numberOfEntries : 1000);

    //  Acquire a shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
                  backend.getBackendID(), e.getLocalizedMessage());
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }

    OutputStream os;
    ReplLDIFOutputStream ros = null;

    if (checksumOutput)
    {
      ros = new ReplLDIFOutputStream(entryCount);
      os = ros;
      try
      {
        os.write((Long.toString(numberOfEntries)).
            getBytes());
      }
      catch(Exception e)
      {
        // Should never happen
      }
    }
    else
    {
      os = output;
    }
    LDIFExportConfig exportConfig = new LDIFExportConfig(os);

    // baseDn branch is the only one included in the export
    List<DN> includeBranches = new ArrayList<DN>(1);
    includeBranches.add(this.baseDn);
    exportConfig.setIncludeBranches(includeBranches);

    // For the checksum computing mode, only consider the 'stable' attributes
    if (checksumOutput)
    {
      String includeAttributeStrings[] =
        {"objectclass", "sn", "cn", "entryuuid"};
      HashSet<AttributeType> includeAttributes;
      includeAttributes = new HashSet<AttributeType>();
      for (String attrName : includeAttributeStrings)
      {
        AttributeType attrType  = DirectoryServer.getAttributeType(attrName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }
        includeAttributes.add(attrType);
      }
      exportConfig.setIncludeAttributes(includeAttributes);
    }

    //  Launch the export.
    try
    {
      backend.exportLDIF(exportConfig);
    }
    catch (DirectoryException de)
    {
      if (ros == null ||
          ros.getNumExportedEntries() < entryCount)
      {
        Message message =
            ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(de.getMessageObject());
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(
          stackTraceToSingleLineString(e));
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }
    finally
    {
      // Clean up after the export by closing the export config.
      // Will also flush the export and export the remaining entries.
      exportConfig.close();

      if (checksumOutput)
      {
        genID = ros.getChecksumValue();
      }

      //  Release the shared lock on the backend.
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      try
      {
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
          throw new DirectoryException(
              ResultCode.OTHER, message, null);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), stackTraceToSingleLineString(e));
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    return genID;
  }

  /**
   * Retrieves the backend related to the domain.
   *
   * @return The backend of that domain.
   * @param baseDn The baseDn to retrieve the backend
   */
  protected static Backend retrievesBackend(DN baseDn)
  {
    // Retrieves the backend related to this domain
    return DirectoryServer.getBackend(baseDn);
  }

  /**
   * Process backend before import.
   * @param backend The backend.
   * @throws Exception
   */
  private void preBackendImport(Backend backend)
  throws Exception
  {
    // Stop saving state
    stateSavingDisabled = true;

    // FIXME setBackendEnabled should be part of TaskUtils ?
    TaskUtils.disableBackend(backend.getBackendID());

    // Acquire an exclusive lock for the backend.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();
    if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
    {
      Message message = ERR_INIT_CANNOT_LOCK_BACKEND.get(
                          backend.getBackendID(),
                          String.valueOf(failureReason));
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }
  }

  /**
   * This method triggers an import of the replicated data.
   *
   * @param input                The InputStream from which the data are read.
   * @throws DirectoryException  When needed.
   */
  @Override
  protected void importBackend(InputStream input) throws DirectoryException
  {
    LDIFImportConfig importConfig = null;

    Backend backend = retrievesBackend(baseDn);

    try
    {
      if (!backend.supportsLDIFImport())
      {
        Message message = ERR_INIT_IMPORT_NOT_SUPPORTED.get(
            backend.getBackendID());
        if (ieContext.getException() == null)
          ieContext.setException(new DirectoryException(ResultCode.OTHER,
              message));
      }
      else
      {
        importConfig =
          new LDIFImportConfig(input);
        List<DN> includeBranches = new ArrayList<DN>();
        includeBranches.add(this.baseDn);
        importConfig.setIncludeBranches(includeBranches);
        importConfig.setAppendToExistingData(false);
        importConfig.setSkipDNValidation(true);
        // We should not validate schema for replication
        importConfig.setValidateSchema(false);
        // Allow fractional replication ldif import plugin to be called
        importConfig.setInvokeImportPlugins(true);
        // Reset the follow import flag and message before starting the import
        importErrorMessageId = -1;
        followImport = true;

        // TODO How to deal with rejected entries during the import
        importConfig.writeRejectedEntries(
            getFileForPath("logs" + File.separator +
            "replInitRejectedEntries").getAbsolutePath(),
            ExistingFileBehavior.OVERWRITE);

        // Process import
        preBackendImport(backend);
        backend.importLDIF(importConfig);

        stateSavingDisabled = false;
      }
    }
    catch(Exception e)
    {
      if (ieContext.getException() == null)
        ieContext.setException(new DirectoryException(
            ResultCode.OTHER,
            ERR_INIT_IMPORT_FAILURE.get(e.getLocalizedMessage())));
    }
    finally
    {
      try
      {
        // Cleanup
        if (importConfig != null)
        {
          importConfig.close();
          closeBackendImport(backend); // Re-enable backend
          backend = retrievesBackend(baseDn);
        }

        loadDataState();

        if (ieContext.getException() != null)
        {
          // When an error occurred during an import, most of times
          // the generationId coming in the root entry of the imported data,
          // is not valid anymore (partial data in the backend).
          generationId = computeGenerationId();
          saveGenerationId(generationId);
        }
      }
      catch (DirectoryException fe)
      {
        // If we already catch an Exception it's quite possible
        // that the loadDataState() and setGenerationId() fail
        // so we don't bother about the new Exception.
        // However if there was no Exception before we want
        // to return this Exception to the task creator.
        if (ieContext.getException() == null)
          ieContext.setException(new DirectoryException(
              ResultCode.OTHER,
              ERR_INIT_IMPORT_FAILURE.get(fe.getLocalizedMessage())));
      }
    }

    if (ieContext.getException() != null)
      throw ieContext.getException();
  }

  /**
   * Make post import operations.
   * @param backend The backend implied in the import.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void closeBackendImport(Backend backend)
  throws DirectoryException
  {
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();

    // Release lock
    if (!LockFileManager.releaseLock(lockFile, failureReason))
    {
      Message message = WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), String.valueOf(failureReason));
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    TaskUtils.enableBackend(backend.getBackendID());
  }

  /**
   * Retrieves a replication domain based on the baseDn.
   *
   * @param baseDn The baseDn of the domain to retrieve
   * @return The domain retrieved
   * @throws DirectoryException When an error occurred or no domain
   * match the provided baseDn.
   */
  public static LDAPReplicationDomain retrievesReplicationDomain(DN baseDn)
  throws DirectoryException
  {
    LDAPReplicationDomain replicationDomain = null;

    // Retrieves the domain
    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider<?> provider :
      DirectoryServer.getSynchronizationProviders())
    {
      if (!( provider instanceof MultimasterReplication))
      {
        Message message = ERR_INVALID_PROVIDER.get();
        throw new DirectoryException(ResultCode.OTHER,
            message);
      }

      // From the domainDN retrieves the replication domain
      LDAPReplicationDomain domain =
        MultimasterReplication.findDomain(baseDn, null);
      if (domain == null)
      {
        break;
      }
      if (replicationDomain != null)
      {
        // Should never happen
        Message message = ERR_MULTIPLE_MATCHING_DOMAIN.get();
        throw new DirectoryException(ResultCode.OTHER,
            message);
      }
      replicationDomain = domain;
    }

    if (replicationDomain == null)
    {
      throw new DirectoryException(ResultCode.OTHER,
          ERR_NO_MATCHING_DOMAIN.get(String.valueOf(baseDn)));
    }
    return replicationDomain;
  }

  /**
   * Returns the backend associated to this domain.
   * @return The associated backend.
   */
  public Backend getBackend()
  {
    return retrievesBackend(baseDn);
  }

  /*
   * <<Total Update
   */


  /**
   * Push the modifications contained in the given parameter as
   * a modification that would happen on a local server.
   * The modifications are not applied to the local database,
   * historical information is not updated but a ChangeNumber
   * is generated and the ServerState associated to this domain is
   * updated.
   * @param modifications The modification to push
   */
  public void synchronizeModifications(List<Modification> modifications)
  {
    ModifyOperation opBasis =
      new ModifyOperationBasis(InternalClientConnection.getRootConnection(),
                          InternalClientConnection.nextOperationID(),
                          InternalClientConnection.nextMessageID(),
                          null, DirectoryServer.getSchemaDN(),
                          modifications);
    LocalBackendModifyOperation op = new LocalBackendModifyOperation(opBasis);

    ChangeNumber cn = generateChangeNumber(op);
    OperationContext ctx = new ModifyContext(cn, "schema");
    op.setAttachment(SYNCHROCONTEXT, ctx);
    op.setResultCode(ResultCode.SUCCESS);
    synchronize(op);
  }

  /**
   * Check if the provided configuration is acceptable for add.
   *
   * @param configuration The configuration to check.
   * @param unacceptableReasons When the configuration is not acceptable, this
   *                            table is use to return the reasons why this
   *                            configuration is not acceptable.
   *
   * @return true if the configuration is acceptable, false other wise.
   */
  public static boolean isConfigurationAcceptable(
      ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    // Check that there is not already a domain with the same DN
    DN dn = configuration.getBaseDN();
    LDAPReplicationDomain domain = MultimasterReplication.findDomain(dn, null);
    if ((domain != null) && (domain.baseDn.equals(dn)))
    {
      Message message = ERR_SYNC_INVALID_DN.get();
      unacceptableReasons.add(message);
      return false;
    }

    // Check that the base DN is configured as a base-dn of the directory server
    if (retrievesBackend(dn) == null)
    {
      Message message = ERR_UNKNOWN_DN.get(dn.toString());
      unacceptableReasons.add(message);
      return false;
    }

    // Check fractional configuration
    try
    {
      isFractionalConfigAcceptable(configuration);
    } catch (ConfigException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
         ReplicationDomainCfg configuration)
  {
    isolationPolicy = configuration.getIsolationPolicy();
    logChangeNumber = configuration.isLogChangenumber();
    histPurgeDelayInMilliSec =
      configuration.getConflictsHistoricalPurgeDelay()*60*1000;

    changeConfig(
        configuration.getReplicationServer(),
        configuration.getWindowSize(),
        configuration.getHeartbeatInterval(),
        (byte)configuration.getGroupId());

    // Read assured configuration and reconnect if needed
    readAssuredConfig(configuration, true);

    // Read fractional configuration and reconnect if needed
    readFractionalConfig(configuration, true);

    /*
     * Modify conflicts are solved for all suffixes but the schema suffix
     * because we don't want to store extra information in the schema
     * ldif files.
     * This has no negative impact because the changes on schema should
     * not produce conflicts.
     */
    solveConflictFlag = baseDn.compareTo(DirectoryServer.getSchemaDN()) != 0 &&
        configuration.isSolveConflicts();

    try
    {
      storeECLConfiguration(configuration);
    }
    catch(Exception e)
    {
      return new ConfigChangeResult(ResultCode.OTHER, false);
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
         ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    // Check that a import/export is not in progress
    if (this.importInProgress() || this.exportInProgress())
    {
      unacceptableReasons.add(
          NOTE_ERR_CANNOT_CHANGE_CONFIG_DURING_TOTAL_UPDATE.get());
      return false;
    }

    // Check fractional configuration
    try
    {
      isFractionalConfigAcceptable(configuration);
    } catch (ConfigException e)
    {
      unacceptableReasons.add(e.getMessageObject());
      return false;
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LinkedHashMap<String, String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT,
               ALERT_DESCRIPTION_REPLICATION_UNRESOLVED_CONFLICT);
    return alerts;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getClassName()
  {
    return CLASS_NAME;

  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DN getComponentEntryDN()
  {
    return configDn;
  }

  /**
   * Starts the Replication Domain.
   */
  public void start()
  {
    // Create the ServerStateFlush thread
    flushThread.start();

    startListenService();
  }


  /**
   * Remove from this domain configuration, the configuration of the
   * external change log.
   */
  public void removeECLDomainCfg()
  {
    try
    {
      DN eclConfigEntryDN = DN.decode(
          "cn=external changeLog," + configDn);

      if (DirectoryServer.getConfigHandler().entryExists(eclConfigEntryDN))
      {
        DirectoryServer.getConfigHandler().deleteEntry(eclConfigEntryDN, null);
      }
    }
    catch(Exception e)
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, e);
      MessageBuilder mb = new MessageBuilder();
      mb.append(e.getMessage());
      Message msg = ERR_CHECK_CREATE_REPL_BACKEND_FAILED.get(mb.toString());
      logError(msg);
    }
  }

  /**
   * Store the provided ECL configuration for the domain.
   * @param  domCfg       The provided configuration.
   * @throws ConfigException When an error occurred.
   */
  public void storeECLConfiguration(ReplicationDomainCfg domCfg)
  throws ConfigException
  {
    ExternalChangelogDomainCfg eclDomCfg = null;
    // create the ecl config if it does not exist
    // There may ot be any config entry related to this domain in some
    // unit test cases
    try
    {
      if (DirectoryServer.getConfigHandler().entryExists(configDn))
      {
        try
        { eclDomCfg = domCfg.getExternalChangelogDomain();
        } catch(Exception e) { /* do nothing */ }
        // domain with no config entry only when running unit tests
        if (eclDomCfg == null)
        {
          // no ECL config provided hence create a default one
          // create the default one
          DN eclConfigEntryDN = DN.decode("cn=external changelog," + configDn);
          if (!DirectoryServer.getConfigHandler().entryExists(eclConfigEntryDN))
          {
            // no entry exist yet for the ECL config for this domain
            // create it
            String ldif = makeLdif(
                "dn: cn=external changelog," + configDn,
                "objectClass: top",
                "objectClass: ds-cfg-external-changelog-domain",
                "cn: external changelog",
                "ds-cfg-enabled: " + (!getBackend().isPrivateBackend()));
            LDIFImportConfig ldifImportConfig = new LDIFImportConfig(
                new StringReader(ldif));
            // No need to validate schema in replication
            ldifImportConfig.setValidateSchema(false);
            LDIFReader reader = new LDIFReader(ldifImportConfig);
            Entry eclEntry = reader.readEntry();
            DirectoryServer.getConfigHandler().addEntry(eclEntry, null);
            ldifImportConfig.close();
          }
        }
      }
      eclDomCfg = domCfg.getExternalChangelogDomain();
      if (eclDomain != null)
      {
        eclDomain.applyConfigurationChange(eclDomCfg);
      }
      else
      {
        // Create the ECL domain object
        eclDomain = new ExternalChangelogDomain(this, eclDomCfg);
      }

    }
    catch(Exception de)
    {
      throw new ConfigException(
            NOTE_ERR_UNABLE_TO_ENABLE_ECL.get(
                "Replication Domain on" + baseDn,
                de.getMessage() + " " + de.getCause().getMessage()), de);
    }
  }

  private static String makeLdif(String... lines)
  {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line).append(EOL);
    }
    // Append an extra line so we can append LDIF Strings.
    buffer.append(EOL);
    return buffer.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void sessionInitiated(
      ServerStatus initStatus,
      ServerState replicationServerState,
      long generationID,
      Session session)
  {
    // Check domain fractional configuration consistency with local
    // configuration variables
    forceBadDataSet = !isBackendFractionalConfigConsistent();

    super.sessionInitiated(
        initStatus, replicationServerState,generationID, session);

    // Now that we are connected , we can enable ECL if :
    // 1/ RS must in the same JVM and created an ECL_WORKFLOW_ELEMENT
    // and 2/ this domain must NOT be private
    if (!getBackend().isPrivateBackend())
    {
      try
      {
        ECLWorkflowElement wfe = (ECLWorkflowElement)
        DirectoryServer.getWorkflowElement(
            ECLWorkflowElement.ECL_WORKFLOW_ELEMENT);
        if (wfe!=null)
          wfe.getReplicationServer().enableECL();
      }
      catch(DirectoryException de)
      {
        Message message =
          NOTE_ERR_UNABLE_TO_ENABLE_ECL.get(
              "Replication Domain on" + baseDn,
              de.getMessage() + " " + de.getCause().getMessage());
        logError(message);
        // and go on
      }
    }

    // Now for bad data set status if needed
    if (forceBadDataSet)
    {
      // Go into bad data set status
      setNewStatus(StatusMachineEvent.TO_BAD_GEN_ID_STATUS_EVENT);
      broker.signalStatusChange(status);
      Message message = NOTE_FRACTIONAL_BAD_DATA_SET_NEED_RESYNC.get(
        baseDn.toString());
      logError(message);
      return; // Do not send changes to the replication server
    }

    try
    {
      /*
       * We must not publish changes to a replicationServer that has
       * not seen all our previous changes because this could cause
       * some other ldap servers to miss those changes.
       * Check that the ReplicationServer has seen all our previous
       * changes.
       */
      ChangeNumber replServerMaxChangeNumber =
        replicationServerState.getMaxChangeNumber(serverId);

      // we don't want to update from here (a DS) an empty RS because
      // normally the RS should have been updated by other RSes except for
      // very last changes lost if the local connection was broken
      // ... hence the RS we are connected to should not be empty
      // ... or if it is empty, it is due to a voluntary reset
      // and we don't want to update it with our changes that could be huge.
      if ((replServerMaxChangeNumber != null) &&
          (replServerMaxChangeNumber.getSeqnum()!=0))
      {
        ChangeNumber ourMaxChangeNumber =
          state.getMaxChangeNumber(serverId);

        if ((ourMaxChangeNumber != null) &&
            (!ourMaxChangeNumber.olderOrEqual(replServerMaxChangeNumber)))
        {
          pendingChanges.setRecovering(true);
          broker.setRecoveryRequired(true);
          new RSUpdater(replServerMaxChangeNumber).start();
        }
      }
    } catch (Exception e)
    {
      Message message = ERR_PUBLISHING_FAKE_OPS.get(
          baseDn.toNormalizedString(),
          e.getLocalizedMessage() + stackTraceToSingleLineString(e));
      logError(message);
    }

  }

  /**
   * Build the list of changes that have been processed by this server
   * after the ChangeNumber given as a parameter and publish them
   * using the given session.
   *
   * @param startingChangeNumber  The ChangeNumber whe we need to start the
   *                              search
   * @param session               The session to use to publish the changes
   *
   * @return                      A boolean indicating he success of the
   *                              operation.
   * @throws Exception            if an Exception happens during the search.
   */
  public boolean buildAndPublishMissingChanges(
      ChangeNumber startingChangeNumber,
      ReplicationBroker session)
      throws Exception
  {
    // Trim the changes in replayOperations that are older than
    // the startingChangeNumber.
    synchronized (replayOperations)
    {
      Iterator<ChangeNumber> it = replayOperations.keySet().iterator();
      while (it.hasNext())
      {
        if (it.next().olderOrEqual(startingChangeNumber))
        {
          it.remove();
        }
        else
        {
          break;
        }
      }
    }

    ChangeNumber lastRetrievedChange;
    long missingChangesDelta;
    InternalSearchOperation op;
    ChangeNumber currentStartChangeNumber = startingChangeNumber;
    do
    {
      lastRetrievedChange = null;
      // We can't do the search in one go because we need to
      // store the results so that we are sure we send the operations
      // in order and because the list might be large
      // So we search by interval of 10 seconds
      // and store the results in the replayOperations list
      // so that they are sorted before sending them.
      missingChangesDelta = currentStartChangeNumber.getTime() + 10000;
      ChangeNumber endChangeNumber =
        new ChangeNumber(
            missingChangesDelta, 0xffffffff, serverId);

      ScanSearchListener listener =
        new ScanSearchListener(currentStartChangeNumber, endChangeNumber);
      op = searchForChangedEntries(
          baseDn, currentStartChangeNumber, endChangeNumber, listener);

      // Publish and remove all the changes from the replayOperations list
      // that are older than the endChangeNumber.
      LinkedList<FakeOperation> opsToSend = new LinkedList<FakeOperation>();
      synchronized (replayOperations)
      {
        Iterator<FakeOperation> itOp = replayOperations.values().iterator();
        while (itOp.hasNext())
        {
          FakeOperation fakeOp = itOp.next();
          if ((fakeOp.getChangeNumber().olderOrEqual(endChangeNumber))
              && state.cover(fakeOp.getChangeNumber()))
          {
            lastRetrievedChange = fakeOp.getChangeNumber();
            opsToSend.add(fakeOp);
            itOp.remove();
          }
          else
          {
            break;
          }
        }
      }

      for (FakeOperation opToSend : opsToSend)
      {
          session.publishRecovery(opToSend.generateMessage());
      }
      opsToSend.clear();
      if (lastRetrievedChange != null)
      {
        currentStartChangeNumber = lastRetrievedChange;
      }
      else
      {
        currentStartChangeNumber = endChangeNumber;
      }

    } while (pendingChanges.recoveryUntil(lastRetrievedChange) &&
             (op.getResultCode().equals(ResultCode.SUCCESS)));

    return op.getResultCode().equals(ResultCode.SUCCESS);
  }


  /**
   * Search for the changes that happened since fromChangeNumber
   * based on the historical attribute. The only changes that will
   * be send will be the one generated on the serverId provided in
   * fromChangeNumber.
   * @param baseDn the base DN
   * @param fromChangeNumber The ChangeNumber from which we want the changes
   * @param lastChangeNumber The max ChangeNumber that the search should return
   * @param resultListener   The listener that will process the entries returned
   * @return the internal search operation
   * @throws Exception when raised.
   */
  public static InternalSearchOperation searchForChangedEntries(
    DN baseDn,
    ChangeNumber fromChangeNumber,
    ChangeNumber lastChangeNumber,
    InternalSearchListener resultListener)
    throws Exception
  {
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();
    Integer serverId = fromChangeNumber.getServerId();

    String maxValueForId;
    if (lastChangeNumber == null)
    {
      maxValueForId = "ffffffffffffffff" + String.format("%04x", serverId)
                      + "ffffffff";
    }
    else
    {
      maxValueForId = lastChangeNumber.toString();
    }

    LDAPFilter filter = LDAPFilter.decode(
       "(&(" + EntryHistorical.HISTORICAL_ATTRIBUTE_NAME + ">=dummy:"
       + fromChangeNumber + ")(" + EntryHistorical.HISTORICAL_ATTRIBUTE_NAME +
       "<=dummy:" + maxValueForId + "))");

    LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
    attrs.add(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);
    attrs.add(EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);
    attrs.add("*");
    return conn.processSearch(
      ByteString.valueOf(baseDn.toString()),
      SearchScope.WHOLE_SUBTREE,
      DereferencePolicy.NEVER_DEREF_ALIASES,
      0, 0, false, filter,
      attrs,
      resultListener);
  }

  /**
   * Search for the changes that happened since fromChangeNumber
   * based on the historical attribute. The only changes that will
   * be send will be the one generated on the serverId provided in
   * fromChangeNumber.
   * @param baseDn the base DN
   * @param fromChangeNumber The change number from which we want the changes
   * @param resultListener that will process the entries returned.
   * @return the internal search operation
   * @throws Exception when raised.
   */
  public static InternalSearchOperation searchForChangedEntries(
    DN baseDn,
    ChangeNumber fromChangeNumber,
    InternalSearchListener resultListener)
    throws Exception
  {
    return searchForChangedEntries(
        baseDn, fromChangeNumber, null, resultListener);
  }


  /**
   * This method should return the total number of objects in the
   * replicated domain.
   * This count will be used for reporting.
   *
   * @throws DirectoryException when needed.
   *
   * @return The number of objects in the replication domain.
   */
  @Override
  public long countEntries() throws DirectoryException
  {
    Backend backend = retrievesBackend(baseDn);

    if (!backend.supportsLDIFExport())
    {
      Message message = ERR_INIT_EXPORT_NOT_SUPPORTED.get(
                          backend.getBackendID());
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    return backend.numSubordinates(baseDn, true) + 1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean processUpdate(UpdateMsg updateMsg, AtomicBoolean shutdown)
  {
    // Ignore message if fractional configuration is inconsistent and
    // we have been passed into bad data set status
    if (forceBadDataSet)
    {
      return false;
    }

    if (updateMsg instanceof LDAPUpdateMsg)
    {
      LDAPUpdateMsg msg = (LDAPUpdateMsg) updateMsg;

      // put the UpdateMsg in the RemotePendingChanges list.
      remotePendingChanges.putRemoteUpdate(msg);

      // Put update message into the replay queue
      // (block until some place in the queue is available)
      final UpdateToReplay updateToReplay = new UpdateToReplay(msg, this);
      while (!shutdown.get())
      {
        // loop until we can offer to the queue or shutdown was initiated
        try
        {
          if (updateToReplayQueue.offer(updateToReplay, 1, TimeUnit.SECONDS))
          {
            // successful offer to the queue, let's exit the loop
            break;
          }
        }
        catch (InterruptedException e)
        {
          // Thread interrupted: check for shutdown.
        }
      }

      return false;
    }

    // unknown message type, this should not happen, just ignore it.
    return true;
  }

  /**
   * Monitoring information for the LDAPReplicationDomain.
   *
   * @return Monitoring attributes specific to the LDAPReplicationDomain.
   */
  @Override
  public Collection<Attribute> getAdditionalMonitoring()
  {
    ArrayList<Attribute> attributes = new ArrayList<Attribute>();

    /* get number of changes in the pending list */
    ReplicationMonitor.addMonitorData(
        attributes, "pending-updates", getPendingUpdatesCount());

    /* get number of changes successfully */
    ReplicationMonitor.addMonitorData(attributes, "replayed-updates-ok",
        getNumReplayedPostOpCalled());

    /* get number of modify conflicts */
    ReplicationMonitor.addMonitorData(attributes, "resolved-modify-conflicts",
        getNumResolvedModifyConflicts());

    /* get number of naming conflicts */
    ReplicationMonitor.addMonitorData(attributes, "resolved-naming-conflicts",
        getNumResolvedNamingConflicts());

    /* get number of unresolved naming conflicts */
    ReplicationMonitor.addMonitorData(attributes, "unresolved-naming-conflicts",
        getNumUnresolvedNamingConflicts());

    ReplicationMonitor.addMonitorData(attributes, "remote-pending-changes-size",
        remotePendingChanges.getQueueSize());

    return attributes;
  }

  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   * @param sourceString The string representing the source
   * @return The source as a integer value
   * @throws DirectoryException if the string is not valid
   */
  public int decodeSource(String sourceString)
  throws DirectoryException
  {
    int  source = 0;
    Throwable cause = null;
    try
    {
      source = Integer.decode(sourceString);
      if ((source >= -1) && (source != serverId))
      {
        // TODO Verifies serverID is in the domain
        // We should check here that this is a server implied
        // in the current domain.
        return source;
      }
    }
    catch(Exception e)
    {
      cause = e;
    }

    ResultCode resultCode = ResultCode.OTHER;
    if (cause != null)
    {
      Message message = ERR_INVALID_IMPORT_SOURCE.get(
          baseDn.toNormalizedString(), Integer.toString(serverId),
          Integer.toString(source),"Details:" + cause.getLocalizedMessage());
      throw new DirectoryException(
          resultCode, message, cause);
    }
    else
    {
      Message message = ERR_INVALID_IMPORT_SOURCE.get(
          baseDn.toNormalizedString(), Integer.toString(serverId),
          Integer.toString(source),"");
      throw new DirectoryException(
          resultCode, message);
    }
  }

  /**
   * Called by synchronize post op plugin in order to add the entry historical
   * attributes to the UpdateMsg.
   * @param msg an replication update message
   * @param op  the operation in progress
   * @throws DirectoryException
   */
  private void addEntryAttributesForCL(UpdateMsg msg,
      PostOperationOperation op) throws DirectoryException
  {
    if (op instanceof PostOperationDeleteOperation)
    {
      Set<String> names = getEclIncludesForDeletes();
      PostOperationDeleteOperation delOp = (PostOperationDeleteOperation) op;
      Entry entry = delOp.getEntryToDelete();
      ((DeleteMsg) msg).setEclIncludes(getIncludedAttributes(entry, names));

      // For delete only, add the Authorized DN since it's required in the
      // ECL entry but is not part of rest of the message.
      DN deleterDN = delOp.getAuthorizationDN();
      if (deleterDN != null)
      {
        ((DeleteMsg) msg).setInitiatorsName(deleterDN.toString());
      }
    }
    else if (op instanceof PostOperationModifyOperation)
    {
      Set<String> names = getEclIncludes();
      PostOperationModifyOperation modOp = (PostOperationModifyOperation) op;
      Entry entry = modOp.getCurrentEntry();
      ((ModifyMsg) msg).setEclIncludes(getIncludedAttributes(entry, names));
    }
    else if (op instanceof PostOperationModifyDNOperation)
    {
      Set<String> names = getEclIncludes();
      PostOperationModifyDNOperation modDNOp =
        (PostOperationModifyDNOperation) op;
      Entry entry = modDNOp.getOriginalEntry();
      ((ModifyDNMsg) msg).setEclIncludes(getIncludedAttributes(entry, names));
    }
    else if (op instanceof PostOperationAddOperation)
    {
      Set<String> names = getEclIncludes();
      PostOperationAddOperation addOp = (PostOperationAddOperation) op;
      Entry entry = addOp.getEntryToAdd();
      ((AddMsg) msg).setEclIncludes(getIncludedAttributes(entry, names));
    }
  }



  private Collection<Attribute> getIncludedAttributes(Entry entry,
      Set<String> names)
  {
    if (names.isEmpty())
    {
      // Fast-path.
      return Collections.emptySet();
    }
    else if (names.size() == 1 && names.contains("*"))
    {
      // Potential fast-path for delete operations.
      LinkedList<Attribute> attributes = new LinkedList<Attribute>();
      for (List<Attribute> attributeList : entry.getUserAttributes().values())
      {
        attributes.addAll(attributeList);
      }
      Attribute objectClassAttribute = entry.getObjectClassAttribute();
      if (objectClassAttribute != null)
      {
        attributes.add(objectClassAttribute);
      }
      return attributes;
    }
    else
    {
      // Expand @objectclass references in attribute list if needed. We
      // do this now in order to take into account dynamic schema changes.

      // Only rebuild the attribute set if necessary.
      boolean needsExpanding = false;
      for (String name : names)
      {
        if (name.startsWith("@"))
        {
          needsExpanding = true;
          break;
        }
      }

      Set<String> expandedNames;
      if (needsExpanding)
      {
        expandedNames = new HashSet<String>(names.size());
        for (String name : names)
        {
          if (name.startsWith("@"))
          {
            String ocName = name.substring(1);
            ObjectClass objectClass = DirectoryServer
                .getObjectClass(toLowerCase(ocName));
            if (objectClass != null)
            {
              for (AttributeType at : objectClass
                  .getRequiredAttributeChain())
              {
                expandedNames.add(at.getNameOrOID());
              }
              for (AttributeType at : objectClass
                  .getOptionalAttributeChain())
              {
                expandedNames.add(at.getNameOrOID());
              }
            }
          }
          else
          {
            expandedNames.add(name);
          }
        }
      }
      else
      {
        expandedNames = names;
      }

      Entry filteredEntry = entry.filterEntry(expandedNames, false,
          false, false);
      return filteredEntry.getAttributes();
    }
  }



  /**
   * Gets the fractional configuration of this domain.
   * @return The fractional configuration of this domain.
   */
  FractionalConfig getFractionalConfig()
  {
    return fractionalConfig;
  }

  /**
   * This bean is a utility class used for holding the parsing
   * result of a fractional configuration. It also contains some facility
   * methods like fractional configuration comparison...
   */
  static class FractionalConfig
  {
    /**
     * Tells if fractional replication is enabled or not (some fractional
     * constraints have been put in place). If this is true then
     * fractionalExclusive explains the configuration mode and either
     * fractionalSpecificClassesAttributes or fractionalAllClassesAttributes or
     * both should be filled with something.
     */
    private boolean fractional = false;

    /**
     * - If true, tells that the configured fractional replication is exclusive:
     * Every attributes contained in fractionalSpecificClassesAttributes and
     * fractionalAllClassesAttributes should be ignored when replaying operation
     * in local backend.
     * - If false, tells that the configured fractional replication is
     * inclusive:
     * Only attributes contained in fractionalSpecificClassesAttributes and
     * fractionalAllClassesAttributes should be taken into account in local
     * backend.
     */
    private boolean fractionalExclusive = true;

    /**
     * Used in fractional replication: holds attributes of a specific object
     * class.
     * - key = object class (name or OID of the class)
     * - value = the attributes of that class that should be taken into account
     * (inclusive or exclusive fractional replication) (name or OID of the
     * attribute)
     * When an operation coming from the network is to be locally replayed, if
     * the concerned entry has an objectClass attribute equals to 'key':
     * - inclusive mode: only the attributes in 'value' will be added/deleted/
     * modified
     * - exclusive mode: the attributes in 'value' will not be added/deleted/
     * modified
     */
    private Map<String, List<String>> fractionalSpecificClassesAttributes =
      new HashMap<String, List<String>>();

    /**
     * Used in fractional replication: holds attributes of any object class.
     * When an operation coming from the network is to be locally replayed:
     * - inclusive mode: only attributes of the matching entry not present in
     * fractionalAllClassesAttributes will be added/deleted/modified
     * - exclusive mode: attributes of the matching entry present in
     * fractionalAllClassesAttributes will not be added/deleted/modified
     * The attributes may be in human readable form of OID form.
     */
    private List<String> fractionalAllClassesAttributes =
      new ArrayList<String>();

    /**
     * Base DN the fractional configuration is for.
     */
    private DN baseDn = null;

    /**
     * Constructs a new fractional configuration object.
     * @param baseDn The base dn the object is for.
     */
    FractionalConfig(DN baseDn)
    {
      this.baseDn = baseDn;
    }

    /**
     * Getter for fractional.
     * @return True if the configuration has fractional enabled
     */
    boolean isFractional()
    {
      return fractional;
    }

    /**
     * Set the fractional parameter.
     * @param fractional The fractional parameter
     */
    void setFractional(boolean fractional)
    {
      this.fractional = fractional;
    }

    /**
     * Getter for fractionalExclusive.
     * @return True if the configuration has fractional exclusive enabled
     */
    boolean isFractionalExclusive()
    {
      return fractionalExclusive;
    }

    /**
     * Set the fractionalExclusive parameter.
     * @param fractionalExclusive The fractionalExclusive parameter
     */
    void setFractionalExclusive(boolean fractionalExclusive)
    {
      this.fractionalExclusive = fractionalExclusive;
    }

    /**
     * Getter for fractionalSpecificClassesAttributes attribute.
     * @return The fractionalSpecificClassesAttributes attribute.
     */
    Map<String, List<String>> getFractionalSpecificClassesAttributes()
    {
      return fractionalSpecificClassesAttributes;
    }

    /**
     * Set the fractionalSpecificClassesAttributes parameter.
     * @param fractionalSpecificClassesAttributes The
     * fractionalSpecificClassesAttributes parameter to set.
     */
    void setFractionalSpecificClassesAttributes(Map<String,
      List<String>> fractionalSpecificClassesAttributes)
    {
      this.fractionalSpecificClassesAttributes =
        fractionalSpecificClassesAttributes;
    }

    /**
     * Getter for fractionalSpecificClassesAttributes attribute.
     * @return The fractionalSpecificClassesAttributes attribute.
     */
    List<String> getFractionalAllClassesAttributes()
    {
      return fractionalAllClassesAttributes;
    }

    /**
     * Set the fractionalAllClassesAttributes parameter.
     * @param fractionalAllClassesAttributes The
     * fractionalSpecificClassesAttributes parameter to set.
     */
    void setFractionalAllClassesAttributes(
      List<String> fractionalAllClassesAttributes)
    {
      this.fractionalAllClassesAttributes = fractionalAllClassesAttributes;
    }

    /**
     * Getter for the base baseDn.
     * @return The baseDn attribute.
     */
    DN getBaseDn()
    {
      return baseDn;
    }

    /**
     * Extract the fractional configuration from the passed domain configuration
     * entry.
     * @param configuration The configuration object
     * @return The fractional replication configuration.
     * @throws ConfigException If an error occurred.
     */
    static FractionalConfig toFractionalConfig(
      ReplicationDomainCfg configuration) throws ConfigException
    {
      // Prepare fractional configuration variables to parse
      Iterator<String> exclIt = null;
      SortedSet<String> fractionalExclude =
        configuration.getFractionalExclude();
      if (fractionalExclude != null)
      {
        exclIt = fractionalExclude.iterator();
      }

      Iterator<String> inclIt = null;
      SortedSet<String> fractionalInclude =
        configuration.getFractionalInclude();
      if (fractionalInclude != null)
      {
        inclIt = fractionalInclude.iterator();
      }

      // Get potentially new fractional configuration
      Map<String, List<String>> newFractionalSpecificClassesAttributes =
        new HashMap<String, List<String>>();
      List<String> newFractionalAllClassesAttributes = new ArrayList<String>();

      int newFractionalMode = parseFractionalConfig(exclIt, inclIt,
        newFractionalSpecificClassesAttributes,
        newFractionalAllClassesAttributes);

      // Create matching parsed config object
      FractionalConfig result = new FractionalConfig(configuration.getBaseDN());
      switch (newFractionalMode)
      {
        case NOT_FRACTIONAL:
          result.setFractional(false);
          result.setFractionalExclusive(true);
          break;
        case EXCLUSIVE_FRACTIONAL:
        case INCLUSIVE_FRACTIONAL:
          result.setFractional(true);
          result.setFractionalExclusive(
              newFractionalMode == EXCLUSIVE_FRACTIONAL);
          break;
      }
      result.setFractionalSpecificClassesAttributes(
        newFractionalSpecificClassesAttributes);
      result.setFractionalAllClassesAttributes(
        newFractionalAllClassesAttributes);
      return result;
    }

    /**
     * Parses a fractional replication configuration, filling the empty passed
     * variables and returning the used fractional mode. The 2 passed variables
     * to fill should be initialized (not null) and empty.
     * @param exclIt The list of fractional exclude configuration values (may be
     *               null)
     * @param inclIt The list of fractional include configuration values (may be
     *               null)
     * @param fractionalSpecificClassesAttributes An empty map to be filled with
     *        what is read from the fractional configuration properties.
     * @param fractionalAllClassesAttributes An empty list to be filled with
     *        what is read from the fractional configuration properties.
     * @return the fractional mode deduced from the passed configuration:
     *         not fractional, exclusive fractional or inclusive fractional
     *         modes
     */
     private static int parseFractionalConfig (
      Iterator<String> exclIt, Iterator<String> inclIt,
      Map<String, List<String>> fractionalSpecificClassesAttributes,
      List<String> fractionalAllClassesAttributes) throws ConfigException
    {
      int fractionalMode;

      // Determine if fractional-exclude or fractional-include property is used
      // : only one of them is allowed
      Iterator<String> iterator;

      // Deduce the wished fractional mode
      if ((exclIt != null) && exclIt.hasNext())
      {
        if ((inclIt != null) && inclIt.hasNext())
        {
          throw new ConfigException(
            NOTE_ERR_FRACTIONAL_CONFIG_BOTH_MODES.get());
        }
        else
        {
          fractionalMode = EXCLUSIVE_FRACTIONAL;
          iterator = exclIt;
        }
      }
      else
      {
        if ((inclIt != null) && inclIt.hasNext())
        {
          fractionalMode = INCLUSIVE_FRACTIONAL;
          iterator = inclIt;
        }
        else
        {
          return NOT_FRACTIONAL;
        }
      }

      while (iterator.hasNext())
      {
        // Parse a value with the form class:attr1,attr2...
        // or *:attr1,attr2...
        String fractCfgStr = iterator.next();
        StringTokenizer st = new StringTokenizer(fractCfgStr, ":");
        int nTokens = st.countTokens();
        if (nTokens < 2)
        {
          throw new ConfigException(NOTE_ERR_FRACTIONAL_CONFIG_WRONG_FORMAT.
            get(fractCfgStr));
        }
        // Get the class name
        String classNameLower = st.nextToken().toLowerCase();
        boolean allClasses = classNameLower.equals("*");
        // Get the attributes
        String attributes = st.nextToken();
        st = new StringTokenizer(attributes, ",");
        while (st.hasMoreTokens())
        {
          String attrNameLower = st.nextToken().toLowerCase();
          // Store attribute in the appropriate variable
          if (allClasses)
          {
            // Avoid duplicate attributes
            if (!fractionalAllClassesAttributes.contains(attrNameLower))
            {
              fractionalAllClassesAttributes.add(attrNameLower);
            }
          }
          else
          {
            List<String> attrList =
              fractionalSpecificClassesAttributes.get(classNameLower);
            if (attrList != null)
            {
              // Avoid duplicate attributes
              if (!attrList.contains(attrNameLower))
              {
                attrList.add(attrNameLower);
              }
            } else
            {
              attrList = new ArrayList<String>();
              attrList.add(attrNameLower);
              fractionalSpecificClassesAttributes.put(classNameLower, attrList);
            }
          }
        }
      }
      return fractionalMode;
    }

    // Return type of the parseFractionalConfig method
    static final int NOT_FRACTIONAL = 0;
    static final int EXCLUSIVE_FRACTIONAL = 1;
    static final int INCLUSIVE_FRACTIONAL = 2;

    /**
     * Get an integer representation of the domain fractional configuration.
     * @return An integer representation of the domain fractional configuration.
     */
    int fractionalConfigToInt()
    {
      int fractionalMode;
      if (fractional)
      {
        if (fractionalExclusive)
          fractionalMode = EXCLUSIVE_FRACTIONAL;
        else
          fractionalMode = INCLUSIVE_FRACTIONAL;
      } else
      {
        fractionalMode = NOT_FRACTIONAL;
      }
      return fractionalMode;
    }

    /**
     * Compare 2 fractional replication configurations and returns true if they
     * are equivalent.
     * @param fractionalConfig1 First fractional configuration
     * @param fractionalConfig2 Second fractional configuration
     * @return True if both configurations are equivalent.
     * @throws ConfigException If some classes or attributes could not be
     * retrieved from the schema.
     */
     static boolean isFractionalConfigEquivalent(
      FractionalConfig fractionalConfig1, FractionalConfig fractionalConfig2)
      throws ConfigException
    {
      // Compare base DNs just to be consistent
      if (!fractionalConfig1.getBaseDn().equals(fractionalConfig2.getBaseDn()))
        return false;

      // Compare modes
      if ( (fractionalConfig1.isFractional() !=
        fractionalConfig2.isFractional()) ||
        (fractionalConfig1.isFractionalExclusive() !=
        fractionalConfig2.isFractionalExclusive()) )
        return false;

      // Compare all classes attributes
      List<String> allClassesAttributes1 =
        fractionalConfig1.getFractionalAllClassesAttributes();
      List<String> allClassesAttributes2 =
        fractionalConfig2.getFractionalAllClassesAttributes();
      if (!isAttributeListEquivalent(allClassesAttributes1,
        allClassesAttributes2))
              return false;

      // Compare specific classes attributes
      Map<String, List<String>> specificClassesAttributes1 =
        fractionalConfig1.getFractionalSpecificClassesAttributes();
      Map<String, List<String>> specificClassesAttributes2 =
        fractionalConfig2.getFractionalSpecificClassesAttributes();
      if (specificClassesAttributes1.size() !=
        specificClassesAttributes2.size())
        return false;
      /*
       * Check consistency of specific classes attributes
       *
       * For each class in specificClassesAttributes1, check that the attribute
       * list is equivalent to specificClassesAttributes2 attribute list
       */
      Schema schema = DirectoryServer.getSchema();
      for (String className1 : specificClassesAttributes1.keySet())
      {
        // Get class from specificClassesAttributes1
        ObjectClass objectClass1 = schema.getObjectClass(className1);
        if (objectClass1 == null)
        {
          throw new ConfigException(
            NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_OBJECT_CLASS.get(className1));
        }

        // Look for matching one in specificClassesAttributes2
        boolean foundClass = false;
        for (String className2 : specificClassesAttributes2.keySet())
        {
          ObjectClass objectClass2 = schema.getObjectClass(className2);
          if (objectClass2 == null)
          {
            throw new ConfigException(
              NOTE_ERR_FRACTIONAL_CONFIG_UNKNOWN_OBJECT_CLASS.get(className2));
          }
          if (objectClass1.equals(objectClass2))
          {
            foundClass = true;
            // Now compare the 2 attribute lists
            List<String> attributes1 =
              specificClassesAttributes1.get(className1);
            List<String> attributes2 =
              specificClassesAttributes2.get(className2);
            if (!isAttributeListEquivalent(attributes1, attributes2))
              return false;
            break;
          }
        }
        // Found matching class ?
        if (!foundClass)
          return false;
      }

      return true;
    }
  }

  /**
   * Specifies whether this domain is enabled/disabled regarding the ECL.
   * @return enabled/disabled for the ECL.
   */
  public boolean isECLEnabled()
  {
    return this.eclDomain.isEnabled();
  }

  /**
   * Return the purge delay (in ms) for the historical information stored
   * in entries to solve conflicts for this domain.
   *
   * @return the purge delay.
   */
  public long getHistoricalPurgeDelay()
  {
    return histPurgeDelayInMilliSec;
  }

  /**
   * Check if the operation that just happened has cleared a conflict :
   * Clearing a conflict happens if the operation has free a DN that
   * for which an other entry was in conflict.
   * Steps:
   * - get the DN freed by a DELETE or MODRDN op
   * - search for entries put in the conflict space (dn=entryUUID'+'....)
   *   because the expected DN was not available (ds-sync-conflict=expected DN)
   * - retain the entry with the oldest conflict
   * - rename this entry with the freedDN as it was expected originally
   *
   * @param task     the task raising this purge.
   * @param endDate  the date to stop this task whether the job is done or not.
   * @throws DirectoryException when an exception happens.
   *
   */
   public void purgeConflictsHistorical(PurgeConflictsHistoricalTask task,
       long endDate)
   throws DirectoryException
   {
     LDAPFilter filter = null;

     TRACER.debugInfo("[PURGE] purgeConflictsHistorical "
         + "on domain: " + baseDn
         + "endDate:" + new Date(endDate)
         + "lastChangeNumberPurgedFromHist: "
         + lastChangeNumberPurgedFromHist.toStringUI());

     try
     {
       filter = LDAPFilter.decode(
         "(" + EntryHistorical.HISTORICAL_ATTRIBUTE_NAME + ">=dummy:"
         + lastChangeNumberPurgedFromHist + ")");

     } catch (LDAPException e)
     {
       // Not possible. We know the filter just above is correct.
     }

     LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
     attrs.add(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);
     attrs.add(EntryHistorical.ENTRYUUID_ATTRIBUTE_NAME);
     attrs.add("*");
     InternalSearchOperation searchOp =  conn.processSearch(
         ByteString.valueOf(baseDn.toString()),
         SearchScope.WHOLE_SUBTREE,
         DereferencePolicy.NEVER_DEREF_ALIASES,
         0, 0, false, filter,
         attrs, null);

     int count = 0;

     if (task != null)
       task.setProgressStats(lastChangeNumberPurgedFromHist, count);

     LinkedList<SearchResultEntry> entries = searchOp.getSearchEntries();
     for (SearchResultEntry entry : entries)
     {
       long maxTimeToRun = endDate - TimeThread.getTime();
       if (maxTimeToRun < 0)
       {
         Message errMsg = Message.raw(Category.SYNC, Severity.NOTICE,
             " end date reached");
         DirectoryException de = new DirectoryException(
             ResultCode.ADMIN_LIMIT_EXCEEDED,
             errMsg);
         throw (de);
       }

       EntryHistorical entryHist = EntryHistorical.newInstanceFromEntry(entry);
       lastChangeNumberPurgedFromHist = entryHist.getOldestCN();
       entryHist.setPurgeDelay(this.histPurgeDelayInMilliSec);
       Attribute attr = entryHist.encodeAndPurge();
       count += entryHist.getLastPurgedValuesCount();
       List<Modification> mods = new LinkedList<Modification>();
       Modification mod;
       mod = new Modification(ModificationType.REPLACE, attr);
       mods.add(mod);

       ModifyOperationBasis newOp =
         new ModifyOperationBasis(
             conn, InternalClientConnection.nextOperationID(),
             InternalClientConnection.nextMessageID(),
             new ArrayList<Control>(0),
             entry.getDN(),
             mods);
       newOp.setInternalOperation(true);
       newOp.setSynchronizationOperation(true);
       newOp.setDontSynchronize(true);

       newOp.run();

       if (newOp.getResultCode() != ResultCode.SUCCESS)
       {
         // Log information for the repair tool.
         MessageBuilder mb = new MessageBuilder();
         mb.append(ERR_CANNOT_ADD_CONFLICT_ATTRIBUTE.get());
         mb.append(String.valueOf(newOp));
         mb.append(" ");
         mb.append(String.valueOf(newOp.getResultCode()));
         logError(mb.toMessage());
       }
       else
       {
         if (task != null)
           task.setProgressStats(lastChangeNumberPurgedFromHist, count);
       }
     }
   }
}
