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
import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbOperation;
import java.io.IOException;
import org.opends.messages.Message;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.opends.server.api.Backend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.AlertGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.util.Validator;
import static org.opends.server.util.StaticUtils.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.NdbMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import static org.opends.server.util.ServerConstants.*;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.GlobalCfgDefn.WorkflowConfigurationMode;
import org.opends.server.admin.std.meta.VirtualAttributeCfgDefn;
import org.opends.server.admin.std.server.NdbBackendCfg;
import org.opends.server.admin.std.server.NdbIndexCfg;
import org.opends.server.backends.SchemaBackend;
import org.opends.server.backends.ndb.importLDIF.Importer;
import org.opends.server.core.Workflow;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.core.networkgroups.NetworkGroup;
import org.opends.server.types.DN;
import org.opends.server.util.LDIFException;
import org.opends.server.workflowelement.WorkflowElement;
import org.opends.server.workflowelement.ndb.NDBWorkflowElement;

/**
 * This is an implementation of a Directory Server Backend which stores
 * entries in MySQL Cluster NDB database engine.
 */
public class BackendImpl
    extends Backend
    implements ConfigurationChangeListener<NdbBackendCfg>, AlertGenerator
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();


  /**
    * The fully-qualified name of this class.
    */
  private static final String CLASS_NAME =
        "org.opends.server.backends.ndb.BackendImpl";


  /**
   * The configuration of this NDB backend.
   */
  private NdbBackendCfg cfg;

  /**
   * The root container to use for this backend.
   */
  private RootContainer rootContainer;

  /**
   * A count of the total operation threads currently in the backend.
   */
  private AtomicInteger threadTotalCount = new AtomicInteger(0);

  /**
   * A count of the write operation threads currently in the backend.
   */
  private AtomicInteger threadWriteCount = new AtomicInteger(0);

  /**
   * A list of monitor providers created for this backend instance.
   */
  private ArrayList<MonitorProvider<?>> monitorProviders =
      new ArrayList<MonitorProvider<?>>();

  /**
   * The base DNs defined for this backend instance.
   */
  private DN[] baseDNs;

  /**
   * The mysqld connection object.
   */
  private Connection sqlConn;

  /**
   * The controls supported by this backend.
   */
  private static HashSet<String> supportedControls;

  /**
   * Database name.
   */
  protected static String DATABASE_NAME;

  /**
   * Attribute column length.
   */
  private static int ATTRLEN;

  /**
   * Attribute column length string.
   */
  private static String ATTRLEN_STRING;

  /**
   * NDB Max Row Size known.
   */
  private static final int NDB_MAXROWSIZE = 8052;

  /**
   * Number of rDN components supported.
   */
  protected static final int DN2ID_DN_NC = 16;

  /**
   * Number of times to retry NDB transaction.
   */
  protected static int TXN_RETRY_LIMIT = 0;

  /**
   * DN2ID table.
   */
  protected static final String DN2ID_TABLE = "DS_dn2id";

  /**
   * NEXTID autoincrement table.
   */
  protected static final String NEXTID_TABLE = "DS_nextid";

  /**
   * Operational Attributes table.
   */
  protected static final String OPATTRS_TABLE = "DS_opattrs";

  /**
   * Operational Attributes table.
   */
  protected static final String TAGS_TABLE = "DS_tags";

  /**
   * Index table prefix.
   */
  protected static final String IDX_TABLE_PREFIX = "DS_idx_";

  /**
   * Referrals table.
   */
  protected static final String REFERRALS_TABLE = "referral";

  /**
   * Name prefix for server specific objectclasses.
   */
  private static final String DSOBJ_NAME_PREFIX = "ds-cfg";

  /**
   * EID column name.
   */
  protected static final String EID = "eid";

  /**
   * MID column name.
   */
  protected static final String MID = "mid";

  /**
   * DN column name prefix.
   */
  protected static final String DN2ID_DN = "a";

  /**
   * OC column name.
   */
  protected static final String DN2ID_OC = "object_classes";

  /**
   * Extensible OC column name.
   */
  protected static final String DN2ID_XOC = "x_object_classes";

  /**
   * Attribute column name.
   */
  protected static final String TAG_ATTR = "attr";

  /**
   * Tags column name.
   */
  protected static final String TAG_TAGS = "tags";

  /**
   * Value column name.
   */
  protected static final String IDX_VAL = "value";

  /**
   * Attribute name to lowercase name map.
   */
  protected static Map<String, String> attrName2LC;

  /**
   * Set of blob attribute names.
   */
  protected static Set<String> blobAttributes;

  /**
   * List of operational attribute names.
   */
  protected static List<String> operationalAttributes;

  /**
   * List of index names.
   */
  protected static List<String> indexes;

  /**
   * Attribute name to ObjectClass name/s map.
   */
  protected static Map<String, String> attr2Oc;

  /**
   * Entry DN to Transaction Map to track entry locking.
   */
  protected static Map<DN, AbstractTransaction> lockMap;

  /**
   * The features supported by this backend.
   */
  private static HashSet<String> supportedFeatures;

  static
  {
    // Set our supported controls.
    supportedControls = new HashSet<String>();
    supportedControls.add(OID_MANAGE_DSAIT_CONTROL);
    supportedControls.add(OID_SUBTREE_DELETE_CONTROL);

    attrName2LC = new HashMap<String, String>();
    operationalAttributes = new ArrayList<String>();
    blobAttributes = new HashSet<String>();
    indexes = new ArrayList<String>();
    attr2Oc = new HashMap<String, String>();
    lockMap = new ConcurrentHashMap<DN, AbstractTransaction>();

    // Set supported features.
    supportedFeatures = new HashSet<String>();
  }



  /**
   * Begin a Backend API method that reads the database.
   */
  private void readerBegin()
  {
    threadTotalCount.getAndIncrement();
  }



  /**
   * End a Backend API method that reads the database.
   */
  private void readerEnd()
  {
    threadTotalCount.getAndDecrement();
  }



  /**
   * Begin a Backend API method that writes the database.
   */
  private void writerBegin()
  {
    threadTotalCount.getAndIncrement();
    threadWriteCount.getAndIncrement();
  }



  /**
   * End a Backend API method that writes the database.
   */
  private void writerEnd()
  {
    threadWriteCount.getAndDecrement();
    threadTotalCount.getAndDecrement();
  }



  /**
   * Wait until there are no more threads accessing the database. It is assumed
   * that new threads have been prevented from entering the database at the time
   * this method is called.
   */
  private void waitUntilQuiescent()
  {
    while (threadTotalCount.get() > 0)
    {
      // Still have threads in the database so sleep a little
      try
      {
        Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }



  /**
   * Get suggested minimum upper bound for given attribute type.
   * @param attrType attribute type
   * @return suggested upper bound
   *         or 0 if none suggested.
   */
  private int getAttributeBound(AttributeType attrType) {
    // HACK: This should be done by Directory Server
    // Schema parser and available in AttributeSyntax.
    String attrDefinition = attrType.getDefinition();
    try {
      int boundOpenIndex = attrDefinition.indexOf("{");
      if (boundOpenIndex == -1) {
        return 0;
      }
      int boundCloseIndex = attrDefinition.indexOf("}");
      if (boundCloseIndex == -1) {
        return 0;
      }
      String boundString = attrDefinition.substring(
        boundOpenIndex + 1, boundCloseIndex);
      return Integer.parseInt(boundString);
    } catch (Exception ex) {
      return 0;
    }
  }



  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration cfg)
      throws ConfigException
  {
    Validator.ensureNotNull(cfg);
    Validator.ensureTrue(cfg instanceof NdbBackendCfg);

    this.cfg = (NdbBackendCfg)cfg;

    Set<DN> dnSet = this.cfg.getBaseDN();
    baseDNs = new DN[dnSet.size()];
    dnSet.toArray(baseDNs);

    this.DATABASE_NAME = this.cfg.getNdbDbname();
    this.ATTRLEN = this.cfg.getNdbAttrLen();
    this.ATTRLEN_STRING = Integer.toString(ATTRLEN);
    this.TXN_RETRY_LIMIT = this.cfg.getDeadlockRetryLimit();

    for (String attrName : this.cfg.getNdbAttrBlob()) {
      this.blobAttributes.add(attrName);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeBackend()
      throws ConfigException, InitializationException
  {
    // Checksum this db environment and register its offline state id/checksum.
    DirectoryServer.registerOfflineBackendStateID(this.getBackendID(), 0);

    // Load MySQL JDBC driver.
    try {
      // The newInstance() call is a work around for some
      // broken Java implementations
      Class.forName("com.mysql.jdbc.Driver").newInstance();
    } catch (Exception ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get(ex.getMessage()));
    }

    // Get MySQL connection.
    try {
      sqlConn =
        DriverManager.getConnection("jdbc:mysql://" +
        cfg.getSqlConnectString(),
        cfg.getSqlUser(), cfg.getSqlPasswd());
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get(ex.getMessage()));
    }

    // Initialize the database.
    Statement stmt = null;
    try {
      stmt = sqlConn.createStatement();
      stmt.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE_NAME);
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("CREATE DATABASE failed: " +
        ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {}
        stmt = null;
      }
    }

    try {
      stmt = sqlConn.createStatement();
      stmt.execute("USE " + DATABASE_NAME);
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("USE failed: " +
        ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {}
        stmt = null;
      }
    }

    // Log a message indicating that database init
    // and schema bootstraping are about to start.
    logError(NOTE_NDB_BOOTSTRAP_SCHEMA.get());

    // Initialize dn2id table.
    try {
      stmt = sqlConn.createStatement();
      stmt.execute("CREATE TABLE IF NOT EXISTS " +
        DN2ID_TABLE + "(" +
        "eid bigint unsigned NOT NULL, " +
    "object_classes VARCHAR(1024) NOT NULL, " +
        "x_object_classes VARCHAR(1024) NOT NULL, " +
    "a0 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a1 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a2 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a3 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a4 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a5 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a6 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a7 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a8 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a9 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a10 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a11 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a12 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a13 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a14 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "a15 VARCHAR(" + ATTRLEN_STRING + ") NOT NULL DEFAULT '', " +
    "PRIMARY KEY (a0, a1, a2, a3, a4, a5, a6, " +
        "a7, a8, a9, a10, a11, a12, a13, a14, a15), " +
        "UNIQUE KEY eid (eid)" +
        ") ENGINE=ndb");
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
          DN2ID_TABLE + " failed: " + ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {}
        stmt = null;
      }
    }

    // Initialize nextid autoincrement table.
    try {
      stmt = sqlConn.createStatement();
      stmt.execute("CREATE TABLE IF NOT EXISTS " +
        NEXTID_TABLE + "(" +
        "a bigint unsigned AUTO_INCREMENT PRIMARY KEY" +
        ") ENGINE=ndb");
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
          NEXTID_TABLE + " failed: " + ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {}
        stmt = null;
      }
    }

    // Set schema read only.
    SchemaBackend schemaBackend =
      (SchemaBackend) DirectoryServer.getBackend("schema");
    if (schemaBackend != null) {
      schemaBackend.setWritabilityMode(WritabilityMode.DISABLED);
    }

    // Set defined attributes.
    Map<String, AttributeType> attrTypesMap =
      DirectoryServer.getSchema().getAttributeTypes();
    for (AttributeType attrType : attrTypesMap.values()) {
      String attrName = attrType.getNameOrOID();
      attrName2LC.put(attrName, attrName.toLowerCase());
      // Skip over server specific object classes.
      // FIXME: this is not clean.
      if (attrName.startsWith(DSOBJ_NAME_PREFIX)) {
        continue;
      }
      if (attrType.getUsage() == AttributeUsage.DIRECTORY_OPERATION) {
        if (operationalAttributes.contains(attrName)) {
          continue;
        }
        operationalAttributes.add(attrName);
      }
    }

    // Strip virtual attributes.
    for (VirtualAttributeRule rule :
      DirectoryServer.getVirtualAttributes())
    {
      if (rule.getConflictBehavior() ==
        VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL)
      {
        String attrName = rule.getAttributeType().getNameOrOID();
        if (operationalAttributes.contains(attrName)) {
          operationalAttributes.remove(attrName);
        }
      }
    }

    // Initialize objectClass tables.
    // TODO: dynamic schema validation and adjustement.
    Map<String,ObjectClass> objectClasses =
      DirectoryServer.getSchema().getObjectClasses();

    Set<Map.Entry<String, ObjectClass>> ocKeySet =
      objectClasses.entrySet();
    for (Map.Entry<String, ObjectClass> ocEntry : ocKeySet) {
      ObjectClass oc = ocEntry.getValue();
      String ocName = oc.getNameOrOID();

      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }

      // Skip over server specific object classes.
      // FIXME: this is not clean.
      if (ocName.startsWith(DSOBJ_NAME_PREFIX)) {
        continue;
      }

      int nColumns = 0;
      StringBuilder attrsBuffer = new StringBuilder();
      Set<AttributeType> reqAttrs = oc.getRequiredAttributes();

      for (AttributeType attrType : reqAttrs) {
        String attrName = attrType.getNameOrOID();
        if (nColumns > 0) {
          attrsBuffer.append(", ");
        }
        attrsBuffer.append("`");
        attrsBuffer.append(attrName);
        attrsBuffer.append("`");
        if (blobAttributes.contains(attrName)) {
          attrsBuffer.append(" BLOB");
        } else {
          attrsBuffer.append(" VARCHAR(");
          int attrBound = getAttributeBound(attrType);
          if ((attrBound > 0) && (attrBound < NDB_MAXROWSIZE)) {
            attrsBuffer.append(Integer.toString(attrBound));
          } else {
            attrsBuffer.append(ATTRLEN_STRING);
          }
          attrsBuffer.append(")");
        }
        if (!attr2Oc.containsKey(attrName)) {
          attr2Oc.put(attrName, ocName);
        }
        nColumns++;
      }

      Set<AttributeType> optAttrs = oc.getOptionalAttributes();

      for (AttributeType attrType : optAttrs) {
        String attrName = attrType.getNameOrOID();
        if (nColumns > 0) {
          attrsBuffer.append(", ");
        }
        attrsBuffer.append("`");
        attrsBuffer.append(attrName);
        attrsBuffer.append("`");
        if (blobAttributes.contains(attrName)) {
          attrsBuffer.append(" BLOB");
        } else {
          attrsBuffer.append(" VARCHAR(");
          int attrBound = getAttributeBound(attrType);
          if ((attrBound > 0) && (attrBound < NDB_MAXROWSIZE)) {
            attrsBuffer.append(Integer.toString(attrBound));
          } else {
            attrsBuffer.append(ATTRLEN_STRING);
          }
          attrsBuffer.append(")");
        }
        if (!attr2Oc.containsKey(attrName)) {
          attr2Oc.put(attrName, ocName);
        }
        nColumns++;
      }

      if (attrsBuffer.toString().length() != 0) {
        attrsBuffer.append(", PRIMARY KEY(eid, mid))");
      }

      String attrsString = attrsBuffer.toString();

      try {
        stmt = sqlConn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS " +
          "`" + ocName + "`" + " (" +
          "eid bigint unsigned NOT NULL, " +
          "mid int unsigned NOT NULL" +
          (attrsString.length() != 0 ? ", " +
           attrsString : ", PRIMARY KEY(eid, mid))") +
          " ENGINE=ndb PARTITION BY KEY(eid)");
      } catch (SQLException ex) {
        throw new InitializationException(
          ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
            ocName + " failed: " + ex.getMessage()));
      } finally {
        // release resources.
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException ex) {
          }
          stmt = null;
        }
      }
    }

    // Initialize operational attributes table.
    int nColumns = 0;
    StringBuilder attrsBuffer = new StringBuilder();

    for (String attrName : operationalAttributes) {
      if (nColumns > 0) {
        attrsBuffer.append(", ");
      }
      attrsBuffer.append("`");
      attrsBuffer.append(attrName);
      attrsBuffer.append("`");
      attrsBuffer.append(" VARCHAR(");
      attrsBuffer.append(ATTRLEN_STRING);
      attrsBuffer.append(")");
      nColumns++;
    }

    if (attrsBuffer.toString().length() != 0) {
      attrsBuffer.append(", PRIMARY KEY(eid))");
    }

    String attrsString = attrsBuffer.toString();

    try {
      stmt = sqlConn.createStatement();
      stmt.execute("CREATE TABLE IF NOT EXISTS " +
        "`" + OPATTRS_TABLE + "`" + " (" +
        "eid bigint unsigned NOT NULL" +
        (attrsString.length() != 0 ? ", " +
        attrsString : ", PRIMARY KEY(eid))") +
        " ENGINE=ndb PARTITION BY KEY(eid)");
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
        OPATTRS_TABLE + " failed: " + ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {
        }
        stmt = null;
      }
    }

    // Initialize attribute options table.
    try {
      stmt = sqlConn.createStatement();
      stmt.execute("CREATE TABLE IF NOT EXISTS " +
        TAGS_TABLE + "(" +
        "eid bigint unsigned NOT NULL, " +
        "attr VARCHAR(" + ATTRLEN_STRING + "), " +
        "mid int unsigned NOT NULL, " +
        "tags VARCHAR(" + ATTRLEN_STRING + "), " +
        "PRIMARY KEY (eid, attr, mid))" +
        " ENGINE=ndb PARTITION BY KEY(eid)");
    } catch (SQLException ex) {
      throw new InitializationException(
        ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
          TAGS_TABLE + " failed: " + ex.getMessage()));
    } finally {
      // release resources.
      if (stmt != null) {
        try {
          stmt.close();
        } catch (SQLException ex) {}
        stmt = null;
      }
    }

    // Initialize configured indexes.
    for (String idx : cfg.listNdbIndexes()) {
      NdbIndexCfg indexCfg = cfg.getNdbIndex(idx);
      // TODO: Substring indexes.
      AttributeType attrType = indexCfg.getAttribute();
      String attrName = attrType.getNameOrOID();
      indexes.add(attrName);
      try {
        stmt = sqlConn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS " +
          IDX_TABLE_PREFIX + attrName + "(" +
          "eid bigint unsigned NOT NULL, " +
          "mid int unsigned NOT NULL, " +
          "value VARCHAR(" + ATTRLEN_STRING + "), " +
          "PRIMARY KEY (eid, mid), " +
          "KEY value (value)" +
          ") ENGINE=ndb PARTITION BY KEY(eid)");
      } catch (SQLException ex) {
        throw new InitializationException(
          ERR_NDB_DATABASE_EXCEPTION.get("CREATE TABLE " +
          IDX_TABLE_PREFIX + attrName + " failed: " +
          ex.getMessage()));
      } finally {
        // release resources.
        if (stmt != null) {
          try {
            stmt.close();
          } catch (SQLException ex) {
          }
          stmt = null;
        }
      }
    }

    // Open Root Container.
    if (rootContainer == null) {
      rootContainer = initializeRootContainer();
    }

    try
    {
      // Log an informational message about the number of entries.
      Message message = NOTE_NDB_BACKEND_STARTED.get(
          cfg.getBackendId(), rootContainer.getEntryCount());
      logError(message);
    }
    catch(NdbApiException ex)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ex);
      }
      Message message =
          WARN_NDB_GET_ENTRY_COUNT_FAILED.get(ex.getMessage());
      throw new InitializationException(message, ex);
    }

    WorkflowConfigurationMode workflowConfigMode =
      DirectoryServer.getWorkflowConfigurationMode();
    DirectoryServer.setWorkflowConfigurationMode(
      WorkflowConfigurationMode.MANUAL);

    for (DN dn : cfg.getBaseDN())
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
        WorkflowImpl workflowImpl = createWorkflow(dn);
        registerWorkflowWithDefaultNetworkGroup(workflowImpl);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            String.valueOf(dn), String.valueOf(e));
        throw new InitializationException(message, e);
      }
    }

    DirectoryServer.setWorkflowConfigurationMode(workflowConfigMode);

    // Register as an AlertGenerator.
    DirectoryServer.registerAlertGenerator(this);
    // Register this backend as a change listener.
    cfg.addNdbChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeBackend()
  {
    // Deregister as a change listener.
    cfg.removeNdbChangeListener(this);

    WorkflowConfigurationMode workflowConfigMode =
      DirectoryServer.getWorkflowConfigurationMode();
    DirectoryServer.setWorkflowConfigurationMode(
      WorkflowConfigurationMode.MANUAL);

    // Deregister our base DNs.
    for (DN dn : rootContainer.getBaseDNs())
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
        deregisterWorkflowWithDefaultNetworkGroup(dn);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    DirectoryServer.setWorkflowConfigurationMode(workflowConfigMode);

    // Deregister our monitor providers.
    for (MonitorProvider<?> monitor : monitorProviders)
    {
      DirectoryServer.deregisterMonitorProvider(
           monitor.getMonitorInstanceName().toLowerCase());
    }
    monitorProviders = new ArrayList<MonitorProvider<?>>();

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close the database.
    try
    {
      rootContainer.close();
      rootContainer = null;
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_NDB_DATABASE_EXCEPTION.get(e.getMessage());
      logError(message);
    }

    try {
      if (sqlConn != null) {
        sqlConn.close();
      }
    } catch (SQLException e) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_NDB_DATABASE_EXCEPTION.get(e.getMessage());
      logError(message);
    }

    // Checksum this db environment and register its offline state id/checksum.
    DirectoryServer.registerOfflineBackendStateID(this.getBackendID(), 0);

    //Deregister the alert generator.
    DirectoryServer.deregisterAlertGenerator(this);

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);
    threadWriteCount.set(0);

    // Log an informational message.
    Message message = NOTE_BACKEND_OFFLINE.get(cfg.getBackendId());
    logError(message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // Substring indexing NYI.
    if (indexType == IndexType.SUBSTRING) {
      return false;
    }
    String attrName = attributeType.getNameOrOID();
    if (indexes.contains(attrName)) {
      return true;
    }
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFExport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long getEntryCount()
  {
    if (rootContainer != null)
    {
      try
      {
        return rootContainer.getEntryCount();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    return -1;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    long ret = numSubordinates(entryDN, false);
    if (ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    else if (ret == 0)
    {
      return ConditionResult.FALSE;
    }
    else
    {
      return ConditionResult.TRUE;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    if (ec == null)
    {
      return -1;
    }

    readerBegin();
    ec.sharedLock.lock();
    try
    {
      return ec.getNumSubordinates(entryDN, subtree);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    readerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    Entry entry;
    try
    {
      entry = ec.getEntry(entryDN);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }

    return entry;
  }



  /**
   * Retrieves the requested entry from this backend.  Note that the
   * caller must hold a read or write lock on the specified DN. Note
   * that the lock is held after this method has completed execution.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   * @param  txn      Abstarct transaction for this operation.
   * @param  lockMode Lock mode for this operation.
   *
   * @return  The requested entry, or {@code null} if the entry does
   *          not exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              retrieve the entry.
   */
  public Entry getEntryNoCommit(DN entryDN, AbstractTransaction txn,
    NdbOperation.LockMode lockMode) throws DirectoryException
  {
    readerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    Entry entry;
    try
    {
      entry = ec.getEntryNoCommit(entryDN, txn, lockMode);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }

    return entry;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw createDirectoryException(new UnsupportedOperationException());
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure
   * that the entry is appropriate for the backend and that no entry
   * already exists with the same DN.  The caller must hold a write
   * lock on the DN of the provided entry.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry
   *                       is associated.  This may be {@code null}
   *                       for adds performed internally.
   * @param  txn           Abstract transaction for this operation.
   *
   * @throws DirectoryException  If a problem occurs while trying to
   *                             add the entry.
   *
   * @throws CanceledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the add
   *                                       operation.
   */
  public void addEntry(Entry entry, AddOperation addOperation,
    AbstractTransaction txn)
      throws DirectoryException, CanceledOperationException
  {
    writerBegin();
    DN entryDN = entry.getDN();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    try
    {
      ec.addEntry(entry, addOperation, txn);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw createDirectoryException(new UnsupportedOperationException());
  }



  /**
   * Removes the specified entry from this backend.  This method must
   * ensure that the entry exists and that it does not have any
   * subordinate entries (unless the backend supports a subtree delete
   * operation and the client included the appropriate information in
   * the request).  The caller must hold a write lock on the provided
   * entry DN.
   *
   * @param  entryDN          The DN of the entry to remove from this
   *                          backend.
   * @param  entry            The entry to delete.
   * @param  deleteOperation  The delete operation with which this
   *                          action is associated.  This may be
   *                          {@code null} for deletes performed
   *                          internally.
   * @param  txn              Abstract transaction for this operation.
   *
   * @throws DirectoryException  If a problem occurs while trying to
   *                             remove the entry.
   *
   * @throws CanceledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the
   *                                       delete operation.
   */
  public void deleteEntry(DN entryDN, Entry entry,
    DeleteOperation deleteOperation, AbstractTransaction txn)
    throws DirectoryException, CanceledOperationException
  {
    writerBegin();

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();
    try
    {
      ec.deleteEntry(entryDN, entry, deleteOperation, txn);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry oldEntry, Entry newEntry,
    ModifyOperation modifyOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw createDirectoryException(new UnsupportedOperationException());
  }



  /**
   * Replaces the specified entry with the provided entry in this
   * backend. The backend must ensure that an entry already exists
   * with the same DN as the provided entry. The caller must hold a
   * write lock on the DN of the provided entry.
   *
   * @param oldEntry
   *          The original entry that is being replaced.
   * @param newEntry
   *          The new entry to use in place of the existing entry with
   *          the same DN.
   * @param modifyOperation
   *          The modify operation with which this action is
   *          associated. This may be {@code null} for modifications
   *          performed internally.
   * @param txn
   *          Abstract transaction for this operation.
   * @throws DirectoryException
   *           If a problem occurs while trying to replace the entry.
   * @throws CanceledOperationException
   *           If this backend noticed and reacted to a request to
   *           cancel or abandon the modify operation.
   */
  public void replaceEntry(Entry oldEntry, Entry newEntry,
    ModifyOperation modifyOperation, AbstractTransaction txn)
      throws DirectoryException, CanceledOperationException
  {
    writerBegin();

    DN entryDN = newEntry.getDN();
    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(entryDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    ec.sharedLock.lock();

    try
    {
      ec.replaceEntry(oldEntry, newEntry, modifyOperation, txn);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      ec.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw createDirectoryException(new UnsupportedOperationException());
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering
   * any subordinate entries as necessary. This must ensure that an
   * entry already exists with the provided current DN, and that no
   * entry exists with the target DN of the provided entry. The caller
   * must hold write locks on both the current DN and the new DN for
   * the entry.
   *
   * @param currentDN
   *          The current DN of the entry to be replaced.
   * @param entry
   *          The new content to use for the entry.
   * @param modifyDNOperation
   *          The modify DN operation with which this action is
   *          associated. This may be {@code null} for modify DN
   *          operations performed internally.
   * @param txn
   *          Abstract transaction for this operation.
   * @throws DirectoryException
   *           If a problem occurs while trying to perform the rename.
   * @throws CanceledOperationException
   *           If this backend noticed and reacted to a request to
   *           cancel or abandon the modify DN operation.
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation,
                          AbstractTransaction txn)
      throws DirectoryException, CanceledOperationException
  {
    writerBegin();

    EntryContainer currentContainer;
    if (rootContainer != null)
    {
      currentContainer = rootContainer.getEntryContainer(currentDN);
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }

    EntryContainer container = rootContainer.getEntryContainer(entry.getDN());

    if (currentContainer != container)
    {
      // FIXME: No reason why we cannot implement a move between containers
      // since the containers share the same database environment.
      Message msg = WARN_NDB_FUNCTION_NOT_SUPPORTED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                   msg);
    }
    try
    {
      currentContainer.sharedLock.lock();

      currentContainer.renameEntry(currentDN, entry, modifyDNOperation, txn);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    finally
    {
      currentContainer.sharedLock.unlock();
      writerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void search(SearchOperation searchOperation)
      throws DirectoryException, CanceledOperationException
  {
    // Abort any Group or ACI bulk search operations.
    List<Control> requestControls = searchOperation.getRequestControls();
    if (requestControls != null) {
      for (Control c : requestControls) {
        if (c.getOID().equals(OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE)) {
          return;
        }
      }
    }

    EntryContainer ec;
    if (rootContainer != null)
    {
      ec = rootContainer.getEntryContainer(searchOperation.getBaseDN());
    }
    else
    {
      Message message = ERR_ROOT_CONTAINER_NOT_INITIALIZED.get(getBackendID());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
              message);
    }
    ec.sharedLock.lock();

    readerBegin();

    try
    {
      ec.search(searchOperation);
    }
    catch (NdbApiException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw createDirectoryException(e);
    }
    catch (NDBException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_NDB_DATABASE_EXCEPTION.get(e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      ec.sharedLock.unlock();
      readerEnd();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    if (!DirectoryServer.isRunning()) {
      // No offline export for now.
      Message message = ERR_NDB_EXPORT_OFFLINE_NOT_SUPPORTED.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    try
    {
      if (rootContainer == null) {
        rootContainer = initializeRootContainer();
      }

      ExportJob exportJob = new ExportJob(exportConfig);
      exportJob.exportLDIF(rootContainer);
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
      }
      Message message = ERR_NDB_IO_ERROR.get(ioe.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    catch (NdbApiException nae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, nae);
      }
      throw createDirectoryException(nae);
    }
    catch (LDIFException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessageObject());
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ie.getMessageObject());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ce.getMessageObject());
    }
    finally
    {
      // leave the backend in the same state.
      if (rootContainer != null)
      {
        try
        {
          rootContainer.close();
          rootContainer = null;
        }
        catch (NdbApiException e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    if (!DirectoryServer.isRunning()) {
      // No offline import for now.
      Message message = ERR_NDB_IMPORT_OFFLINE_NOT_SUPPORTED.get();
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    try
    {
      Importer importer = new Importer(importConfig);
      if (rootContainer == null) {
        rootContainer = initializeRootContainer();
      }
      return importer.processImport(rootContainer);
    }
    catch (IOException ioe)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ioe);
      }
      Message message = ERR_NDB_IO_ERROR.get(ioe.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    catch (NDBException ne)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ne);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ne.getMessageObject());
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ie.getMessageObject());
    }
    catch (ConfigException ce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ce);
      }
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   ce.getMessageObject());
    }
    finally
    {
      // leave the backend in the same state.
      try
      {
        if (rootContainer != null)
        {
          rootContainer.close();
          rootContainer = null;
        }
      }
      catch (NdbApiException nae)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, nae);
        }
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
      throws DirectoryException
  {
    // Not supported, do nothing.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    // Not supported, do nothing.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
      throws DirectoryException
  {
    // Not supported, do nothing.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(Configuration configuration,
                                           List<Message> unacceptableReasons)
  {
    NdbBackendCfg config = (NdbBackendCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      NdbBackendCfg cfg,
      List<Message> unacceptableReasons)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(NdbBackendCfg newCfg)
  {
    ConfigChangeResult ccr;
    ResultCode resultCode = ResultCode.SUCCESS;
    ArrayList<Message> messages = new ArrayList<Message>();

    ccr = new ConfigChangeResult(resultCode, false, messages);
    return ccr;
  }

  /**
   * Returns a handle to the root container currently used by this backend.
   * The rootContainer could be NULL if the backend is not initialized.
   *
   * @return The RootContainer object currently used by this backend.
   */
  public RootContainer getRootContainer()
  {
    return rootContainer;
  }

  /**
   * Creates a customized DirectoryException from the NdbApiException
   * thrown by NDB backend.
   *
   * @param  e The NdbApiException to be converted.
   * @return  DirectoryException created from exception.
   */
  DirectoryException createDirectoryException(Exception e)
  {
    ResultCode resultCode = DirectoryServer.getServerErrorResultCode();
    Message message = null;

    String jeMessage = e.getMessage();
    if (jeMessage == null)
    {
      jeMessage = stackTraceToSingleLineString(e);
    }
    message = ERR_NDB_DATABASE_EXCEPTION.get(jeMessage);
    return new DirectoryException(resultCode, message, e);
  }

  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_BACKEND_ENVIRONMENT_UNUSABLE,
               ALERT_DESCRIPTION_BACKEND_ENVIRONMENT_UNUSABLE);
    return alerts;
  }

  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return cfg.dn();
  }

  private RootContainer initializeRootContainer()
      throws ConfigException, InitializationException
  {
    // Open the database environment
    try
    {
      RootContainer rc = new RootContainer(this, cfg);
      rc.open();
      return rc;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message = ERR_NDB_OPEN_ENV_FAIL.get(e.getMessage());
      throw new InitializationException(message, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void preloadEntryCache() throws
    UnsupportedOperationException
  {
    throw new UnsupportedOperationException("Operation not supported");
  }

  /**
   * Creates one workflow for a given base DN in a backend.
   *
   * @param baseDN   the base DN of the workflow to create
   * @param backend  the backend handled by the workflow
   *
   * @return the newly created workflow
   *
   * @throws DirectoryException  If the workflow ID for the provided
   *                             workflow conflicts with the workflow
   *                             ID of an existing workflow.
   */
  private WorkflowImpl createWorkflow(DN baseDN) throws DirectoryException
  {
    String backendID = this.getBackendID();

    // Create a root workflow element to encapsulate the backend
    NDBWorkflowElement rootWE =
        NDBWorkflowElement.createAndRegister(backendID, this);

    // The workflow ID is "backendID + baseDN".
    // We cannot use backendID as workflow identifier because a backend
    // may handle several base DNs. We cannot use baseDN either because
    // we might want to configure several workflows handling the same
    // baseDN through different network groups. So a mix of both
    // backendID and baseDN should be ok.
    String workflowID = backendID + "#" + baseDN.toString();

    // Create the worklfow for the base DN and register the workflow with
    // the server.
    WorkflowImpl workflowImpl = new WorkflowImpl(workflowID, baseDN,
      rootWE.getWorkflowElementID(), (WorkflowElement) rootWE);
    workflowImpl.register();

    return workflowImpl;
  }

  /**
   * Registers a workflow with the default network group.
   *
   * @param workflowImpl  The workflow to register with the
   *                      default network group
   *
   * @throws  DirectoryException  If the workflow is already registered with
   *                              the default network group
   */
  private void registerWorkflowWithDefaultNetworkGroup(
      WorkflowImpl workflowImpl
      ) throws DirectoryException
  {
    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    defaultNetworkGroup.registerWorkflow(workflowImpl);
  }

  /**
   * Deregisters a workflow with the default network group and
   * deregisters the workflow with the server. This method is
   * intended to be called when workflow configuration mode is
   * auto.
   *
   * @param baseDN  the DN of the workflow to deregister
   */
  private void deregisterWorkflowWithDefaultNetworkGroup(
      DN baseDN
      )
  {
    String backendID = this.getBackendID();

    // Get the default network group and deregister all the workflows
    // being configured for the backend (there is one worklfow per
    // backend base DN).
    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    Workflow workflow = defaultNetworkGroup.deregisterWorkflow(baseDN);
    WorkflowImpl workflowImpl = (WorkflowImpl) workflow;

    // The workflow ID is "backendID + baseDN".
    // We cannot use backendID as workflow identifier because a backend
    // may handle several base DNs. We cannot use baseDN either because
    // we might want to configure several workflows handling the same
    // baseDN through different network groups. So a mix of both
    // backendID and baseDN should be ok.
    String workflowID = backendID + "#" + baseDN.toString();

    NDBWorkflowElement.remove(backendID);
    workflowImpl.deregister(workflowID);
  }
}
