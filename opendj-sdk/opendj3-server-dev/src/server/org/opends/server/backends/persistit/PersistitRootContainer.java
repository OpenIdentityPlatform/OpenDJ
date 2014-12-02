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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.persistit;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.LocalDBBackendCfg;
import org.opends.server.backends.pluggable.NotImplementedException;
import org.opends.server.backends.pluggable.RootContainer;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;

import com.persistit.Persistit;
import com.persistit.Volume;
import com.persistit.exception.PersistitException;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Persistit implementation of a {@link RootContainer}.
 */
class PersistitRootContainer implements RootContainer<PersistitSuffixContainer>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final PersistitBackend backend;
  private final LocalDBBackendCfg cfg;
  private Persistit db;

  /** The base DNs contained in this root container. */
  private final ConcurrentHashMap<DN, PersistitSuffixContainer> suffixContainers =
      new ConcurrentHashMap<DN, PersistitSuffixContainer>();

  /**
   * Constructor for this class.
   *
   * @param backend
   *          the persistit backend
   * @param config
   *          the configuration object
   */
  PersistitRootContainer(PersistitBackend backend, LocalDBBackendCfg config)
  {
    this.backend = backend;
    this.cfg = config;
  }

  /**
   * Returns the persistit backend.
   *
   * @return the persistit backend
   */
  public final PersistitBackend getBackend()
  {
    return backend;
  }

  /** {@inheritDoc} */
  @Override
  public Map<DN, PersistitSuffixContainer> getSuffixContainers()
  {
    return suffixContainers;
  }

  /**
   * Code copied from
   * {@link org.opends.server.backends.jeb. RootContainer#open(com.sleepycat.je.EnvironmentConfig)}
   *
   * @throws ConfigException
   *           If an configuration error occurs while creating the environment.
   */
  void open() throws ConfigException
  {
    // Determine the backend database directory.
    File parentDirectory = getFileForPath(cfg.getDBDirectory());
    File backendDirectory = new File(parentDirectory, cfg.getBackendId());

    // Create the directory if it doesn't exist.
    if (!backendDirectory.exists())
    {
      if (!backendDirectory.mkdirs())
      {
        throw new ConfigException(ERR_JEB_CREATE_FAIL.get(backendDirectory.getPath()));
      }
    }
    // Make sure the directory is valid.
    else if (!backendDirectory.isDirectory())
    {
      throw new ConfigException(ERR_JEB_DIRECTORY_INVALID.get(backendDirectory.getPath()));
    }

    FilePermission backendPermission;
    try
    {
      backendPermission = FilePermission.decodeUNIXMode(cfg.getDBDirectoryPermissions());
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_CONFIG_BACKEND_MODE_INVALID.get(cfg.dn()));
    }

    // Make sure the mode will allow the server itself access to the database
    if (!backendPermission.isOwnerWritable()
        || !backendPermission.isOwnerReadable()
        || !backendPermission.isOwnerExecutable())
    {
      throw new ConfigException(ERR_CONFIG_BACKEND_INSANE_MODE.get(cfg.getDBDirectoryPermissions()));
    }

    // Get the backend database backendDirectory permissions and apply
    if (FilePermission.canSetPermissions())
    {
      try
      {
        if (!FilePermission.setPermissions(backendDirectory, backendPermission))
        {
          logger.warn(WARN_JEB_UNABLE_SET_PERMISSIONS, backendPermission, backendDirectory);
        }
      }
      catch (Exception e)
      {
        // Log an warning that the permissions were not set.
        logger.warn(WARN_JEB_SET_PERMISSIONS_FAILED, backendDirectory, e);
      }
    }

    openAndRegisterSuffixContainers(backendDirectory);
  }

  private void openAndRegisterSuffixContainers(File backendDirectory)
  {
    DN[] baseDNs = backend.getBaseDNs();
    final Properties properties = new Properties();
    properties.setProperty("datapath", backendDirectory.toString());
    properties.setProperty("logpath", backendDirectory + "/log");
    properties.setProperty("logfile", "${logpath}/dj_${timestamp}.log");
    properties.setProperty("buffer.count.16384", "64K");
    for (int i = 0; i < baseDNs.length; i++)
    {
      // TODO JNR in the replace() down below,
      // persistit does not like commas and does not know how to escape them
      final String baseDN = toVolumeName(baseDNs[i]);
      properties.setProperty("volume." + (i + 1), "${datapath}/" + baseDN + ",create,pageSize:16K,"
          + "initialSize:50M,extensionSize:1M,maximumSize:10G");
    }
    properties.setProperty("journalpath", "${datapath}/dj_journal");
    try
    {
      db = new Persistit(properties);
      db.initialize();

      openAndRegisterSuffixContainers(baseDNs);

      if (logger.isTraceEnabled())
      {
        logger.trace("Persistit (%s) environment opened with the following config: %n%s",
            Persistit.VERSION, properties);

        // Get current size of heap in bytes
        long heapSize = Runtime.getRuntime().totalMemory();

        // Get maximum size of heap in bytes. The heap cannot grow beyond this size.
        // Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory();

        // Get amount of free memory within the heap in bytes. This size will increase
        // after garbage collection and decrease as new objects are created.
        long heapFreeSize = Runtime.getRuntime().freeMemory();

        logger.trace("Current size of heap: %d bytes", heapSize);
        logger.trace("Max size of heap: %d bytes", heapMaxSize);
        logger.trace("Free memory in heap: %d bytes", heapFreeSize);
      }
    }
    catch (Exception e)
    {
      throw new NotImplementedException(e);
    }
  }

  private void openAndRegisterSuffixContainers(DN[] baseDNs) throws PersistitException, InitializationException,
      ConfigException, DirectoryException
  {
    for (DN baseDN : baseDNs)
    {
      PersistitSuffixContainer sc = openSuffixContainer(baseDN, null);
      registerSuffixContainer(baseDN, sc);
    }
  }

  private PersistitSuffixContainer openSuffixContainer(DN baseDN, String name) throws PersistitException,
      DirectoryException
  {
    String databasePrefix;
    if (name == null || name.equals(""))
    {
      databasePrefix = baseDN.toNormalizedString();
    }
    else
    {
      databasePrefix = name;
    }

    final Volume volume = db.loadVolume(toVolumeName(baseDN));
    final PersistitSuffixContainer suffixContainer =
        new PersistitSuffixContainer(baseDN, databasePrefix, this, db, volume);
    suffixContainer.open();
    return suffixContainer;
  }

  private String toVolumeName(DN dn)
  {
    return dn.toString().replace(",", "_");
  }

  private void registerSuffixContainer(DN baseDN, PersistitSuffixContainer suffixContainer)
      throws InitializationException
  {
    PersistitSuffixContainer sc = suffixContainers.get(baseDN);
    if (sc != null)
    {
      // If an entry container for this baseDN is already opened we do not allow
      // another to be opened.
      throw new InitializationException(ERR_JEB_ENTRY_CONTAINER_ALREADY_REGISTERED.get(sc.getIndexPrefix(), baseDN));
    }

    suffixContainers.put(baseDN, suffixContainer);
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    for (PersistitSuffixContainer sc : suffixContainers.values())
    {
      sc.close();
    }
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    boolean couldDetermineAllCounts = true;
    long result = 0;
    for (SuffixContainer suffixContainer : getSuffixContainers().values())
    {
      final long suffixCount = suffixContainer.getEntryCount();
      if (suffixCount != -1)
      {
        result += suffixCount;
      }
      else
      {
        couldDetermineAllCounts = false;
      }
    }
    return couldDetermineAllCounts ? result : -1;
  }

  /**
   * Return the suffix container holding a specific DN.
   *
   * @param entryDN
   *          The DN for which to return the suffix container.
   * @return The suffix container holding the DN
   */
  PersistitSuffixContainer getSuffixContainer(DN entryDN)
  {
    PersistitSuffixContainer sc = null;
    DN nodeDN = entryDN;

    while (sc == null && nodeDN != null)
    {
      sc = suffixContainers.get(nodeDN);
      if (sc == null)
      {
        nodeDN = nodeDN.getParentDNInSuffix();
      }
    }

    return sc;
  }
}
