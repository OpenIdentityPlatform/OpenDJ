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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.config.ConfigParam;

import org.opends.server.api.Backend;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.CancelledOperationException;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.InitializationException;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.monitors.DatabaseEnvironmentMonitor;
import org.opends.server.util.LDIFException;
import org.opends.server.loggers.Debug;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.JebMessages.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_BASE_DN;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.OID_SUBTREE_DELETE_CONTROL;
import static org.opends.server.util.ServerConstants.OID_PAGED_RESULTS_CONTROL;
import static org.opends.server.util.ServerConstants.OID_MANAGE_DSAIT_CONTROL;

/**
 * This is an implementation of a Directory Server Backend which stores entries
 * locally in a Sleepycat JE database.
 */
public class BackendImpl extends Backend implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.BackendImpl";

  /**
   * The DN of the configuration entry for this backend.
   */
  private DN configDN;

  /**
   * The configuration of this JE backend.
   */
  private Config config;

  /**
   * The pathname of the directory containing persistent storage for the
   * backend.
   */
  private String backendDirectory;

  /**
   * The base DNs contained in this backend.
   */
  private ConcurrentHashMap<DN, EntryContainer> baseDNs;

  /**
   * The JE environment for this backend instance.  Each instance has its own
   * environment.
   */
  private com.sleepycat.je.Environment dbEnv;

  /**
   * A configurable component to handle changes to the configuration of
   * the database environment.
   */
  private ConfigurableEnvironment configurableEnv = null;

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
  private ArrayList<MonitorProvider> monitorProviders =
       new ArrayList<MonitorProvider>();

  /**
   * The controls supported by this backend.
   */
  private static HashSet<String> supportedControls;

  /**
   * The set of configuration attributes associated with the backend in its role
   * as a configurable component.
   */
  private static ArrayList<ConfigAttribute> configAttrs;

  /**
   * The configuration attribute stub for the backendBaseDN attribute.
   */
  private static DNConfigAttribute baseDNStub;



  static
  {
    // Set our supported controls.
    supportedControls = new HashSet<String>();
    supportedControls.add(OID_SUBTREE_DELETE_CONTROL);
    supportedControls.add(OID_PAGED_RESULTS_CONTROL);
    supportedControls.add(OID_MANAGE_DSAIT_CONTROL);

    configAttrs = new ArrayList<ConfigAttribute>();

    // ds-cfg-backendBaseDN configuration attribute.
    int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
    baseDNStub = new DNConfigAttribute(ATTR_BACKEND_BASE_DN,
                                       getMessage(msgID),
                                       true, true, true);

    configAttrs.add(baseDNStub);

  }



  /**
   * Begin a Backend API method that reads the database.
   */
  private void readerBegin()
  {
    assert debugEnter(CLASS_NAME, "readerBegin");

    threadTotalCount.getAndIncrement();
  }



  /**
   * End a Backend API method that reads the database.
   */
  private void readerEnd()
  {
    assert debugEnter(CLASS_NAME, "readerEnd");

    threadTotalCount.getAndDecrement();
  }



  /**
   * Begin a Backend API method that writes the database.
   */
  private void writerBegin()
  {
    assert debugEnter(CLASS_NAME, "writerBegin");

    threadTotalCount.getAndIncrement();
    threadWriteCount.getAndIncrement();
  }



  /**
   * End a Backend API method that writes the database.
   */
  private void writerEnd()
  {
    assert debugEnter(CLASS_NAME, "writerEnd");

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
    assert debugEnter(CLASS_NAME, "waitUntilQuiescent");

    while (threadTotalCount.get() > 0)
    {
      // Still have threads in the database so sleep a little
      try
      {
        Thread.sleep(500);
      }
      catch (InterruptedException e)
      {
        assert debugException(CLASS_NAME, "waitUntilQuiescent", e);
      }
    }
  }



  /**
   * Determine the container of an entry DN.
   *
   * @param dn The DN of an entry.
   * @return The container of the entry.
   * @throws DirectoryException If the entry DN does not match any of the base
   *                            DNs.
   */
  private EntryContainer getContainer(DN dn) throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getContainer");

    EntryContainer ec = null;
    DN nodeDN = dn;

    while (ec == null && nodeDN != null)
    {
      ec = baseDNs.get(nodeDN);
      if (ec == null)
      {
        nodeDN = nodeDN.getParent();
      }
    }

    if (ec == null)
    {
      // The operation should not have been routed to this backend.
      String message = getMessage(MSGID_JEB_INCORRECT_ROUTING,
                                  dn.toString());
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, message,
                                   MSGID_JEB_INCORRECT_ROUTING);
    }

    return ec;
  }



  /**
   * This method constructs a container name from a base DN. Only alphanumeric
   * characters are preserved, all other characters are replaced with an
   * underscore.
   *
   * @param dn The base DN.
   * @return The container name for the base DN.
   */
  public static String getContainerName(DN dn)
  {
    assert debugEnter(CLASS_NAME, "getContainerName");

    String normStr = dn.toNormalizedString();
    StringBuilder builder = new StringBuilder(normStr.length());
    for (int i = 0; i < normStr.length(); i++)
    {
      char ch = normStr.charAt(i);
      if (Character.isLetterOrDigit(ch))
      {
        builder.append(ch);
      }
      else
      {
        builder.append('_');
      }
    }
    return builder.toString();
  }



  /**
   * Initializes this backend based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this backend.
   * @param  baseDNs      The set of base DNs that have been configured for this
   *                      backend.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeBackend(ConfigEntry configEntry, DN[] baseDNs)
       throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeBackend");

    configDN = configEntry.getDN();

    // Initialize a config object
    config = new Config();
    config.initializeConfig(configEntry, baseDNs);

    // Get the backend database directory.
    backendDirectory = config.getBackendDirectory();

    File dir = new File(backendDirectory);
    if (!dir.isDirectory())
    {
      String message = getMessage(MSGID_JEB_DIRECTORY_INVALID,
                                  backendDirectory);
      throw new InitializationException(MSGID_JEB_DIRECTORY_INVALID,
                                        message);
    }

    // FIXME: Currently assuming every base DN is also a suffix.
    for (DN dn : baseDNs)
    {
      DirectoryServer.registerSuffix(dn, this);
    }

/*
    {
      String message = getMessage(MSGID_JEB_SUFFIXES_NOT_SPECIFIED);
      throw new InitializationException(MSGID_JEB_SUFFIXES_NOT_SPECIFIED,
                                        message);
    }
*/

    // Open the database environment
    try
    {
      dbEnv = new Environment(new File(backendDirectory),
                              config.getEnvironmentConfig());

      Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                         CLASS_NAME, "initializeBackend",
                         dbEnv.getConfig().toString());

//      cleanDatabase();
    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "initializeBackend", e);
      String message = getMessage(MSGID_JEB_OPEN_ENV_FAIL,
                                  e.getMessage());
      throw new InitializationException(MSGID_JEB_OPEN_ENV_FAIL, message, e);
    }

    // Create and register a monitor provider for the environment.
    MonitorProvider monitorProvider = new DatabaseEnvironmentMonitor(dbEnv);
    monitorProviders.add(monitorProvider);
    DirectoryServer.registerMonitorProvider(monitorProvider);

    // Open all the databases.
    this.baseDNs = new ConcurrentHashMap<DN, EntryContainer>(baseDNs.length);
    for (DN dn : baseDNs)
    {
      String containerName = getContainerName(dn);
      Container container = new Container(dbEnv, containerName);
      EntryContainer ec = new EntryContainer(this, config, container);

      try
      {
        ec.open();
      }
      catch (DatabaseException databaseException)
      {
        assert debugException(CLASS_NAME, "initializeBackend",
                              databaseException);
        String message = getMessage(MSGID_JEB_OPEN_DATABASE_FAIL,
                                    databaseException.getMessage());
        throw new InitializationException(MSGID_JEB_OPEN_DATABASE_FAIL, message,
                                          databaseException);
      }

      this.baseDNs.put(dn, ec);
    }

    // Preload the database cache.
    preload();

    // Determine the next entry ID and the total number of entries.
    EntryID highestID = null;
    long entryCount = 0;
    for (EntryContainer ec : this.baseDNs.values())
    {
      try
      {
        EntryID id = ec.getHighestEntryID();
        if (highestID == null || id.compareTo(highestID) > 0)
        {
          highestID = id;
        }
        entryCount += ec.getEntryCount();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeBackend", e);
        String message = getMessage(MSGID_JEB_HIGHEST_ID_FAIL);
        throw new InitializationException(MSGID_JEB_HIGHEST_ID_FAIL,
                                          message, e);
      }
    }
    EntryID.initialize(highestID);

    // Log an informational message about the number of entries.
    int msgID = MSGID_JEB_BACKEND_STARTED;
    String message = getMessage(msgID, entryCount);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

    // Register this backend as a configurable component.
    DirectoryServer.registerConfigurableComponent(this);

    // Register the database environment as a configurable component.
    DN envConfigDN = config.getEnvConfigDN();
    if (envConfigDN != null)
    {
      configurableEnv = new ConfigurableEnvironment(envConfigDN, dbEnv);
      DirectoryServer.registerConfigurableComponent(configurableEnv);
    }
  }



  /**
   * Synchronously invokes the cleaner on the database environment then forces a
   * checkpoint to delete the log files that are no longer in use.
   *
   * @throws DatabaseException If an error occurs while cleaning the database
   * environment.
   */
  private void cleanDatabase()
       throws DatabaseException
  {
    assert debugEnter(CLASS_NAME, "cleanDatabase");

    int msgID;
    String message;

    FilenameFilter filenameFilter = new FilenameFilter()
    {
      public boolean accept(File d, String name)
      {
        return name.endsWith(".jdb");
      }
    };

    File f = new File(backendDirectory);
    int beforeLogfileCount = f.list(filenameFilter).length;

    msgID = MSGID_JEB_CLEAN_DATABASE_START;
    message = getMessage(msgID, beforeLogfileCount, backendDirectory);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

    int currentCleaned = 0;
    int totalCleaned = 0;
    while ((currentCleaned = dbEnv.cleanLog()) > 0)
    {
      totalCleaned += currentCleaned;
    }

    msgID = MSGID_JEB_CLEAN_DATABASE_MARKED;
    message = getMessage(msgID, totalCleaned);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

    if (totalCleaned > 0)
    {
      CheckpointConfig force = new CheckpointConfig();
      force.setForce(true);
      dbEnv.checkpoint(force);
    }

    int afterLogfileCount = f.list(filenameFilter).length;

    msgID = MSGID_JEB_CLEAN_DATABASE_FINISH;
    message = getMessage(msgID, afterLogfileCount);
    logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
             msgID);

  }



  /**
   * Performs any necessary work to finalize this backend, including closing any
   * underlying databases or connections and deregistering any suffixes that it
   * manages with the Directory Server.  This may be called during the Directory
   * Server shutdown process or if a backend is disabled with the server online.
   * It must not return until the backend is closed. <BR><BR> This method may
   * not throw any exceptions.  If any problems are encountered, then they may
   * be logged but the closure should progress as completely as possible.
   */
  public void finalizeBackend()
  {
    assert debugEnter(CLASS_NAME, "finalizeBackend");

    // Deregister our configurable components.
    if (configurableEnv != null)
    {
      DirectoryServer.deregisterConfigurableComponent(configurableEnv);
      configurableEnv = null;
    }
    DirectoryServer.deregisterConfigurableComponent(this);

    // Deregister our suffixes.
    // FIXME: Currently assuming every base DN is also a suffix.
    for (DN dn : baseDNs.keySet())
    {
      try
      {
        DirectoryServer.deregisterSuffix(dn);
      }
      catch (ConfigException e)
      {
        assert debugException(CLASS_NAME, "finalizeBackend", e);
      }
    }

    // Deregister our monitor providers.
    for (MonitorProvider monitor : monitorProviders)
    {
      DirectoryServer.deregisterMonitorProvider(
           monitor.getMonitorInstanceName().toLowerCase());
    }
    monitorProviders = new ArrayList<MonitorProvider>();

    // We presume the server will prevent more operations coming into this
    // backend, but there may be existing operations already in the
    // backend. We need to wait for them to finish.
    waitUntilQuiescent();

    // Close the database.
    try
    {
      for (EntryContainer ec : baseDNs.values())
      {
        ec.close();
      }
      dbEnv.close();
    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "finalizeBackend", e);
      int msgID = MSGID_JEB_DATABASE_EXCEPTION;
      String message = getMessage(msgID, e.getMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
               message, msgID);
    }

    // Make sure the thread counts are zero for next initialization.
    threadTotalCount.set(0);
    threadWriteCount.set(0);

    // We will not reuse the config object.
    config = null;
  }



  /**
   * Indicates whether the data associated with this backend may be considered
   * local (i.e., in a repository managed by the Directory Server) rather than
   * remote (i.e., in an external repository accessed by the Directory Server
   * but managed through some other means).
   *
   * @return <CODE>true</CODE> if the data associated with this backend may be
   *         considered local, or <CODE>false</CODE> if it is remote.
   */
  public boolean isLocal()
  {
    assert debugEnter(CLASS_NAME, "isLocal");

    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to export the data it
   * contains to an LDIF file.
   *
   * @return <CODE>true</CODE> if this backend provides an LDIF export
   *         mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFExport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFExport");

    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to import its data from
   * an LDIF file.
   *
   * @return <CODE>true</CODE> if this backend provides an LDIF import
   *         mechanism, or <CODE>false</CODE> if not.
   */
  public boolean supportsLDIFImport()
  {
    assert debugEnter(CLASS_NAME, "supportsLDIFImport");

    return true;
  }



  /**
   * Indicates whether this backend provides a backup mechanism of any kind.
   * This method is used by the backup process when backing up all backends to
   * determine whether this backend is one that should be skipped.  It should
   * only return <CODE>true</CODE> for backends that it is not possible to
   * archive directly (e.g., those that don't store their data locally, but
   * rather pass through requests to some other repository).
   *
   * @return <CODE>true</CODE> if this backend provides any kind of backup
   *         mechanism, or <CODE>false</CODE> if it does not.
   */
  public boolean supportsBackup()
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");

    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to perform a backup of
   * its contents in a form that can be restored later, based on the provided
   * configuration.
   *
   * @param backupConfig      The configuration of the backup for which to make
   *                          the determination.
   * @param unsupportedReason A buffer to which a message can be appended
   *                          explaining why the requested backup is not
   *                          supported.
   * @return <CODE>true</CODE> if this backend provides a mechanism for
   *         performing backups with the provided configuration, or
   *         <CODE>false</CODE> if not.
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    assert debugEnter(CLASS_NAME, "supportsBackup");

    return true;
  }



  /**
   * Indicates whether this backend provides a mechanism to restore a backup.
   *
   * @return <CODE>true</CODE> if this backend provides a mechanism for
   *         restoring backups, or <CODE>false</CODE> if not.
   */
  public boolean supportsRestore()
  {
    assert debugEnter(CLASS_NAME, "supportsRestore");

    return true;
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this backend.
   *
   * @return The OIDs of the features that may be supported by this backend.
   */
  public HashSet<String> getSupportedFeatures()
  {
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

    return null;  //NYI
  }



  /**
   * Indicates whether this backend supports the specified feature.
   *
   * @param featureOID The OID of the feature for which to make the
   *                   determination.
   * @return <CODE>true</CODE> if this backend does support the requested
   *         feature, or <CODE>false</CODE>
   */
  public boolean supportsFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "supportsFeature");

    return false;  //NYI
  }



  /**
   * Retrieves the OIDs of the controls that may be supported by this backend.
   *
   * @return The OIDs of the controls that may be supported by this backend.
   */
  public HashSet<String> getSupportedControls()
  {
    assert debugEnter(CLASS_NAME, "getSupportedControls");

    return supportedControls;
  }



  /**
   * Indicates whether this backend supports the specified control.
   *
   * @param controlOID The OID of the control for which to make the
   *                   determination.
   * @return <CODE>true</CODE> if this backend does support the requested
   *         control, or <CODE>false</CODE>
   */
  public boolean supportsControl(String controlOID)
  {
    assert debugEnter(CLASS_NAME, "supportsControl");

    return supportedControls.contains(controlOID);
  }



  /**
   * Retrieves the set of base-level DNs that may be used within this backend.
   *
   * @return The set of base-level DNs that may be used within this backend.
   */
  public DN[] getBaseDNs()
  {
    assert debugEnter(CLASS_NAME, "getBaseDNs");

    return config.getBaseDNs();
  }



  /**
   * Retrieves the requested entry from this backend.  Note that the caller must
   * hold a read or write lock on the specified DN.
   *
   * @param entryDN The distinguished name of the entry to retrieve.
   * @return The requested entry, or <CODE>null</CODE> if the entry does not
   *         exist.
   * @throws DirectoryException If a problem occurs while trying to retrieve the
   *                            entry.
   */
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getEntry");

    readerBegin();
    try
    {
      EntryContainer ec = getContainer(entryDN);
      Entry entry;
      try
      {
        entry = ec.getEntry(entryDN);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "getEntry", e);
        String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                    e.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, MSGID_JEB_DATABASE_EXCEPTION);
      }
      catch (JebException e)
      {
        assert debugException(CLASS_NAME, "getEntry", e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     e.getMessage(),
                                     e.getMessageID());
      }

      return entry;
    }
    finally
    {
      readerEnd();
    }
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure that the
   * entry is appropriate for the backend and that no entry already exists with
   * the same DN.
   *
   * @param entry        The entry to add to this backend.
   * @param addOperation The add operation with which the new entry is
   *                     associated.  This may be <CODE>null</CODE> for adds
   *                     performed internally.
   * @throws DirectoryException If a problem occurs while trying to add the
   *                            entry.
   */
  public void addEntry(Entry entry, AddOperation addOperation)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "addEntry");

    writerBegin();
    try
    {
      DN entryDN = entry.getDN();
      EntryContainer ec = getContainer(entryDN);

      try
      {
        ec.addEntry(entry, addOperation);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "addEntry", e);
        String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                    e.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, MSGID_JEB_DATABASE_EXCEPTION);
      }
      catch (JebException e)
      {
        assert debugException(CLASS_NAME, "addEntry", e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     e.getMessage(),
                                     e.getMessageID());
      }
    }
    finally
    {
      writerEnd();
    }
  }



  /**
   * Removes the specified entry from this backend.  This method must ensure
   * that the entry exists and that it does not have any subordinate entries
   * (unless the backend supports a subtree delete operation and the client
   * included the appropriate information in the request).
   *
   * @param entryDN         The DN of the entry to remove from this backend.
   * @param deleteOperation The delete operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        deletes performed internally.
   * @throws DirectoryException If a problem occurs while trying to remove the
   *                            entry.
   */
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "deleteEntry");

    writerBegin();
    try
    {
      EntryContainer ec = getContainer(entryDN);
      try
      {
        ec.deleteEntry(entryDN, deleteOperation);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "deleteEntry", e);
        String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                    e.getMessage());
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, MSGID_JEB_DATABASE_EXCEPTION);
      }
      catch (JebException e)
      {
        assert debugException(CLASS_NAME, "deleteEntry", e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     e.getMessage(),
                                     e.getMessageID());
      }
    }
    finally
    {
      writerEnd();
    }
  }



  /**
   * Replaces the specified entry with the provided entry in this backend.  The
   * backend must ensure that an entry already exists with the same DN as the
   * provided entry.
   *
   * @param entry           The new entry to use in place of the existing entry
   *                        with the same DN.
   * @param modifyOperation The modify operation with which this action is
   *                        associated.  This may be <CODE>null</CODE> for
   *                        modifications performed internally.
   * @throws DirectoryException If a problem occurs while trying to replace the
   *                            entry.
   */
  public void replaceEntry(Entry entry, ModifyOperation modifyOperation)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "replaceEntry");

    writerBegin();
    try
    {
      DN entryDN = entry.getDN();
      EntryContainer ec = getContainer(entryDN);

      try
      {
        ec.replaceEntry(entry, modifyOperation);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "replaceEntry", e);
        String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, MSGID_JEB_DATABASE_EXCEPTION);
      }
      catch (JebException e)
      {
        assert debugException(CLASS_NAME, "replaceEntry", e);
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     e.getMessage(),
                                     e.getMessageID());
      }
    }
    finally
    {
      writerEnd();
    }
  }



  /**
   * Moves and/or renames the provided entry in this backend, altering any
   * subordinate entries as necessary.  This must ensure that an entry already
   * exists with the provided current DN, and that no entry exists with the
   * target DN of the provided entry.  The caller must hold write locks on both
   * the current DN and the new DN for the entry.
   *
   * @param currentDN         The current DN of the entry to be replaced.
   * @param entry             The new content to use for the entry.
   * @param modifyDNOperation The modify DN operation with which this action is
   *                          associated.  This may be <CODE>null</CODE> for
   *                          modify DN operations performed internally.
   * @throws org.opends.server.core.DirectoryException
   *          If a problem occurs while trying to perform the rename.
   * @throws org.opends.server.core.CancelledOperationException
   *          If this backend noticed and reacted to a request to cancel or
   *          abandon the modify DN operation.
   */
  public void renameEntry(DN currentDN, Entry entry,
                          ModifyDNOperation modifyDNOperation)
       throws DirectoryException, CancelledOperationException
  {
    assert debugEnter(CLASS_NAME, "renameEntry");

    writerBegin();
    try
    {
      EntryContainer currentContainer = getContainer(currentDN);
      EntryContainer container = getContainer(entry.getDN());

      if (currentContainer != container)
      {
        // No reason why we cannot implement a move between containers
        // since the containers share the same database environment.
        int msgID = MSGID_JEB_FUNCTION_NOT_SUPPORTED;
        String msg = getMessage(MSGID_JEB_FUNCTION_NOT_SUPPORTED);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                     msg, msgID);
      }

      currentContainer.renameEntry(currentDN, entry, modifyDNOperation);
    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "renameEntry", e);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }
    catch (JebException e)
    {
      assert debugException(CLASS_NAME, "renameEntry", e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }
    finally
    {
      writerEnd();
    }
  }



  /**
   * Processes the specified search in this backend.  Matching entries should be
   * provided back to the core server using the
   * <CODE>SearchOperation.returnEntry</CODE> method.
   *
   * @param searchOperation The search operation to be processed.
   * @throws org.opends.server.core.DirectoryException
   *          If a problem occurs while processing the search.
   */
  public void search(SearchOperation searchOperation)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "search");

    readerBegin();
    try
    {
      EntryContainer ec = getContainer(searchOperation.getBaseDN());
      ec.search(searchOperation);
    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "search", e);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }
    finally
    {
      readerEnd();
    }
  }



  /**
   * Exports the contents of this backend to LDIF.  This method should only be
   * called if <CODE>supportsLDIFExport</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param configEntry  The configuration entry for this backend.
   * @param baseDNs      The set of base DNs configured for this backend.
   * @param exportConfig The configuration to use when performing the export.
   * @throws org.opends.server.core.DirectoryException
   *          If a problem occurs while performing the LDIF export.
   */
  public void exportLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFExportConfig exportConfig)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "exportLDIF");

    // Initialize a config object.
    config = new Config();

    try
    {
      config.initializeConfig(configEntry, baseDNs);
    }
    catch (ConfigException e)
    {
      assert debugException(CLASS_NAME, "exportLDIF", e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }

    // If the backend already has the environment open, we must use the same
    // underlying environment instance and its configuration.
    Environment env;
    try
    {
      EnvironmentConfig envConfig;
      Environment existingEnv = dbEnv;
      if (existingEnv == null)
      {
        // Open the environment read-only.
        envConfig = config.getEnvironmentConfig();
        envConfig.setReadOnly(true);
        envConfig.setAllowCreate(false);
        envConfig.setTransactional(false);
      }
      else
      {
        envConfig = existingEnv.getConfig();
      }

      // Open a new environment handle.
      String backendDirectory = config.getBackendDirectory();
      env = new Environment(new File(backendDirectory), envConfig);

      Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                         CLASS_NAME, "exportLDIF",
                         env.getConfig().toString());

    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "exportLDIF", e);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                  e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }

    try
    {
      ExportJob exportJob = new ExportJob(this, config, exportConfig);
      exportJob.exportLDIF(env);
    }
    catch (IOException ioe)
    {
      assert debugException(CLASS_NAME, "exportLDIF", ioe);
      int msgID = MSGID_JEB_IO_ERROR;
      String message = getMessage(msgID, ioe.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    catch (JebException je)
    {
      assert debugException(CLASS_NAME, "exportLDIF", je);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   je.getMessage(),
                                   je.getMessageID());
    }
    catch (DatabaseException de)
    {
      assert debugException(CLASS_NAME, "exportLDIF", de);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                  de.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }
    catch (LDIFException e)
    {
      assert debugException(CLASS_NAME, "exportLDIF", e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }
    finally
    {
      try
      {
        env.close();
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "exportLDIF", e);
      }
    }
  }



  /**
   * Imports information from an LDIF file into this backend.  This method
   * should only be called if <CODE>supportsLDIFImport</CODE> returns
   * <CODE>true</CODE>.  Note that the server will not explicitly initialize
   * this backend before calling this method.
   *
   * @param configEntry  The configuration entry for this backend.
   * @param baseDNs      The set of base DNs configured for this backend.
   * @param importConfig The configuration to use when performing the import.
   * @throws DirectoryException If a problem occurs while performing the LDIF
   *                            import.
   */
  public void importLDIF(ConfigEntry configEntry, DN[] baseDNs,
                         LDIFImportConfig importConfig)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "importLDIF");

    // Initialize a config object.
    config = new Config();

    try
    {
      config.initializeConfig(configEntry, baseDNs);
    }
    catch (ConfigException e)
    {
      assert debugException(CLASS_NAME, "importLDIF", e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }

    try
    {
      ImportJob importJob = new ImportJob(this, config, importConfig);
      importJob.importLDIF();
    }
    catch (IOException ioe)
    {
      assert debugException(CLASS_NAME, "importLDIF", ioe);
      int msgID = MSGID_JEB_IO_ERROR;
      String message = getMessage(msgID, ioe.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, msgID);
    }
    catch (JebException je)
    {
      assert debugException(CLASS_NAME, "importLDIF", je);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   je.getMessage(),
                                   je.getMessageID());
    }
    catch (DatabaseException de)
    {
      assert debugException(CLASS_NAME, "importLDIF", de);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                  de.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }
  }



  /**
   * Verify the integrity of the backend instance.
   * @param verifyConfig The verify configuration.
   * @param configEntry The backend instance configuration entry.
   * @param  baseDNs      The set of base DNs that have been configured for this
   *                      backend.
   * @throws  ConfigException  If an unrecoverable problem arises during
   *                           initialization.
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public void verifyBackend(VerifyConfig verifyConfig, ConfigEntry configEntry,
                            DN[] baseDNs)
       throws InitializationException, ConfigException, DirectoryException
  {
    assert debugEnter(CLASS_NAME, "verifyBackend");

    // Initialize a config object.
    config = new Config();
    config.initializeConfig(configEntry, baseDNs);

    VerifyJob verifyJob = new VerifyJob(this, config, verifyConfig);
    try
    {
      verifyJob.verifyBackend();
    }
    catch (DatabaseException e)
    {
      assert debugException(CLASS_NAME, "verifyBackend", e);
      String message = getMessage(MSGID_JEB_DATABASE_EXCEPTION,
                                  e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, MSGID_JEB_DATABASE_EXCEPTION);
    }
    catch (JebException e)
    {
      assert debugException(CLASS_NAME, "verifyBackend", e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   e.getMessage(),
                                   e.getMessageID());
    }
  }



  /**
   * Creates a backup of the contents of this backend in a form that may be
   * restored at a later date if necessary.  This method should only be called
   * if <CODE>supportsBackup</CODE> returns <CODE>true</CODE>.  Note that the
   * server will not explicitly initialize this backend before calling this
   * method.
   *
   * @param configEntry  The configuration entry for this backend.
   * @param backupConfig The configuration to use when performing the backup.
   * @throws DirectoryException If a problem occurs while performing the
   *                            backup.
   */
  public void createBackup(ConfigEntry configEntry, BackupConfig backupConfig)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "createBackup");

    BackupManager backupManager =
         new BackupManager(getBackendID());
    backupManager.createBackup(configEntry, backupConfig);
  }



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param backupDirectory The backup directory structure with which the
   *                        specified backup is associated.
   * @param backupID        The backup ID for the backup to be removed.
   * @throws DirectoryException If it is not possible to remove the specified
   *                            backup for some reason (e.g., no such backup
   *                            exists or there are other backups that are
   *                            dependent upon it).
   */
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "removeBackup");

    BackupManager backupManager =
         new BackupManager(getBackendID());
    backupManager.removeBackup(backupDirectory, backupID);
  }



  /**
   * Restores a backup of the contents of this backend.  This method should only
   * be called if <CODE>supportsRestore</CODE> returns <CODE>true</CODE>.  Note
   * that the server will not explicitly initialize this backend before calling
   * this method.
   *
   * @param configEntry   The configuration entry for this backend.
   * @param restoreConfig The configuration to use when performing the restore.
   * @throws DirectoryException If a problem occurs while performing the
   *                            restore.
   */
  public void restoreBackup(ConfigEntry configEntry,
                            RestoreConfig restoreConfig)
       throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "restoreBackup");

    BackupManager backupManager =
         new BackupManager(getBackendID());
    backupManager.restoreBackup(configEntry, restoreConfig);
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return The DN of the configuration entry with which this component is
   *         associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return The set of configuration attributes that are associated with this
   *         configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");

    return configAttrs;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param configEntry         The configuration entry for which to make the
   *                            determination.
   * @param unacceptableReasons A list that can be used to hold messages about
   *                            why the provided entry does not have an
   *                            acceptable configuration.
   * @return <CODE>true</CODE> if the provided entry has an acceptable
   *         configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration");

    DN[] baseDNs = null;
    boolean acceptable = true;

    try
    {
      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
      if (baseDNAttr == null)
      {
        int msgID = MSGID_CONFIG_BACKEND_NO_BASE_DNS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()));
        unacceptableReasons.add(message);
      }
      else
      {
        baseDNs = baseDNAttr.activeValues().toArray(new DN[0]);
      }
    }
    catch (ConfigException e)
    {
      unacceptableReasons.add(e.getMessage());
      acceptable = false;
    }

    Config newConfig = new Config();
    try
    {
      newConfig.initializeConfig(configEntry, baseDNs);
    }
    catch (ConfigException e)
    {
      unacceptableReasons.add(e.getMessage());
      acceptable = false;
    }

    return acceptable;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param configEntry     The entry containing the new configuration to apply
   *                        for this component.
   * @param detailedResults Indicates whether detailed information about the
   *                        processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration");

    ConfigChangeResult ccr;

    try
    {
      DN[] baseDNs = null;

      DNConfigAttribute baseDNAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);

      baseDNs = baseDNAttr.activeValues().toArray(new DN[0]);

      // Create a new Config object.
      Config newConfig = new Config();
      newConfig.initializeConfig(configEntry, baseDNs);

      // Check for changes to the base DNs.
      for (DN baseDN : config.getBaseDNs())
      {
        boolean found = false;
        for (DN dn : newConfig.getBaseDNs())
        {
          if (dn.equals(baseDN))
          {
            found = true;
          }
        }
        if (!found)
        {
          // The base DN was deleted.
          // FIXME This is not thread-safe.
          // Even though access to the entry container map is safe, there may be
          // operation threads with a handle on the entry container being
          // closed.
          DirectoryServer.deregisterSuffix(baseDN);
          EntryContainer ec = this.baseDNs.remove(baseDN);
          ec.close();
          ec.removeContainer();
        }
      }

      for (DN baseDN : newConfig.getBaseDNs())
      {
        if (!this.baseDNs.containsKey(baseDN))
        {
          // The base DN was added.
          String containerName = getContainerName(baseDN);
          Container container = new Container(dbEnv, containerName);
          EntryContainer ec = new EntryContainer(this, config, container);
          ec.open();
          this.baseDNs.put(baseDN, ec);
          DirectoryServer.registerSuffix(baseDN, this);
        }
      }

      // Check if any JE non-mutable properties were changed.
      EnvironmentConfig oldEnvConfig = config.getEnvironmentConfig();
      EnvironmentConfig newEnvConfig = newConfig.getEnvironmentConfig();
      Map paramsMap = EnvironmentParams.SUPPORTED_PARAMS;
      for (Object o : paramsMap.values())
      {
        ConfigParam param = (ConfigParam)o;
        if (!param.isMutable())
        {
          String oldValue = oldEnvConfig.getConfigParam(param.getName());
          String newValue = newEnvConfig.getConfigParam(param.getName());
          if (!oldValue.equalsIgnoreCase(newValue))
          {
            System.out.println("The change to the following property will " +
                               "take effect when the backend is restarted: " +
                               param.getName());
          }
        }
      }

      // This takes care of changes to the JE environment for those
      // properties that are mutable at runtime.
      dbEnv.setMutableConfig(newConfig.getEnvironmentConfig());

      Debug.debugMessage(DebugLogCategory.BACKEND, DebugLogSeverity.INFO,
                         CLASS_NAME, "applyNewConfiguration",
                         dbEnv.getConfig().toString());

      // Put the new configuration in place.
      config = newConfig;
    }
    catch (Exception e)
    {
      ArrayList<String> messages = new ArrayList<String>();
      messages.add(e.getMessage());
      ccr = new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                   false, messages);
      return ccr;
    }

    ccr = new ConfigChangeResult(ResultCode.SUCCESS, false);
    return ccr;
  }



  /**
   * Preload the database cache. There is no preload if the configured preload
   * time limit is zero.
   */
  private void preload()
  {
    assert debugEnter(CLASS_NAME, "preload");

    long timeLimit = config.getPreloadTimeLimit();

    if (timeLimit > 0)
    {
      // Get a list of all the databases used by the backend.
      ArrayList<Database> dbList = new ArrayList<Database>();
      for (EntryContainer ec : baseDNs.values())
      {
        ec.listDatabases(dbList);
      }

      // Sort the list in order of priority.
      Collections.sort(dbList, new DbPreloadComparator());

      // Preload each database until we reach the time limit or the cache
      // is filled.
      try
      {
        long timeEnd = System.currentTimeMillis() + timeLimit;

        // Configure preload of Leaf Nodes (LNs) containing the data values.
        PreloadConfig preloadConfig = new PreloadConfig();
        preloadConfig.setLoadLNs(true);

        for (Database db : dbList)
        {
          // Calculate the remaining time.
          long timeRemaining = timeEnd - System.currentTimeMillis();
          if (timeRemaining <= 0)
          {
            break;
          }

          preloadConfig.setMaxMillisecs(timeRemaining);
          PreloadStats preloadStats = db.preload(preloadConfig);
/*
          System.out.println("file=" + db.getDatabaseName() +
                             " LNs=" + preloadStats.getNLNsLoaded());
*/

          // Stop if the cache is full or the time limit has been exceeded.
          if (preloadStats.getStatus() != PreloadStatus.SUCCESS)
          {
            break;
          }
        }

        // Log an informational message about the size of the cache.
        EnvironmentStats stats = dbEnv.getStats(new StatsConfig());
        long total = stats.getCacheTotalBytes();

        int msgID = MSGID_JEB_CACHE_SIZE_AFTER_PRELOAD;
        String message = getMessage(msgID, total / (1024 * 1024));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.NOTICE, message,
                 msgID);
      }
      catch (DatabaseException e)
      {
        assert debugException(CLASS_NAME, "preload", e);
      }
    }
  }
}
