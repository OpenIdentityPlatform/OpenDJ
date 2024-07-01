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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.api.MonitorData;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;

/**
 * This class provides an application-wide disk space monitoring service.
 * It provides the ability for registered handlers to receive notifications
 * when the free disk space falls below a certain threshold.
 *
 * The handler will only be notified once when when the free space
 * have dropped below any of the thresholds. Once the "full" threshold
 * have been reached, the handler will not be notified again until the
 * free space raises above the "low" threshold.
 */
public class DiskSpaceMonitor extends MonitorProvider<MonitorProviderCfg> implements Runnable, AlertGenerator,
    ServerShutdownListener
{
  /** Helper class for each requestor for use with cn=monitor reporting and users of a specific mountpoint. */
  private class MonitoredDirectory extends MonitorProvider<MonitorProviderCfg>
  {
    private volatile File directory;
    private volatile long lowThreshold;
    private volatile long fullThreshold;
    private final DiskSpaceMonitorHandler handler;
    private final String instanceName;
    private final String baseName;
    private int lastState;

    private MonitoredDirectory(File directory, String instanceName, String baseName, DiskSpaceMonitorHandler handler)
    {
      this.directory = directory;
      this.instanceName = instanceName;
      this.baseName = baseName;
      this.handler = handler;
    }

    @Override
    public String getMonitorInstanceName() {
      return instanceName + "," + "cn=" + baseName;
    }

    @Override
    public void initializeMonitorProvider(MonitorProviderCfg configuration)
        throws ConfigException, InitializationException {
    }

    @Override
    public MonitorData getMonitorData()
    {
      final MonitorData monitorAttrs = new MonitorData(3);
      monitorAttrs.add("disk-dir", directory.getPath());
      monitorAttrs.add("disk-free", getFreeSpace());
      monitorAttrs.add("disk-state", getState());
      return monitorAttrs;
    }

    private File getDirectory() {
      return directory;
    }

    private long getFreeSpace() {
      return directory.getUsableSpace();
    }

    private long getFullThreshold() {
      return fullThreshold;
    }

    private long getLowThreshold() {
      return lowThreshold;
    }

    private void setFullThreshold(long fullThreshold) {
      this.fullThreshold = fullThreshold;
    }

    private void setLowThreshold(long lowThreshold) {
      this.lowThreshold = lowThreshold;
    }

    private String getState()
    {
      switch(lastState)
      {
      case NORMAL:
        return "normal";
      case LOW:
        return "low";
      case FULL:
        return "full";
      default:
        return null;
      }
    }
  }

  /**
   * Helper class for building temporary list of handlers to notify on threshold hits.
   * One object per directory per state will hold all the handlers matching directory and state.
   */
  private class HandlerNotifier {
    private File directory;
    private int state;
    /** Printable list of handlers names, for reporting backend names in alert messages. */
    private final StringBuilder diskNames = new StringBuilder();
    private final List<MonitoredDirectory> allHandlers = new ArrayList<>();

    private HandlerNotifier(File directory, int state)
    {
      this.directory = directory;
      this.state = state;
    }

    private void notifyHandlers()
    {
      for (MonitoredDirectory mdElem : allHandlers)
      {
        switch (state)
        {
        case FULL:
          mdElem.handler.diskFullThresholdReached(mdElem.getDirectory(), mdElem.getFullThreshold());
          break;
        case LOW:
          mdElem.handler.diskLowThresholdReached(mdElem.getDirectory(), mdElem.getLowThreshold());
          break;
        case NORMAL:
          mdElem.handler.diskSpaceRestored(mdElem.getDirectory(), mdElem.getLowThreshold(),
              mdElem.getFullThreshold());
          break;
        }
      }
    }

    private boolean isEmpty()
    {
      return allHandlers.isEmpty();
    }

    private void addHandler(MonitoredDirectory handler)
    {
      logger.trace("State change: %d -> %d", handler.lastState, state);
      handler.lastState = state;
      if (handler.handler != null)
      {
        allHandlers.add(handler);
      }
      appendName(diskNames, handler.instanceName);
    }

    private void appendName(StringBuilder strNames, String strVal)
    {
      if (strNames.length() > 0)
      {
        strNames.append(", ");
      }
      strNames.append(strVal);
    }
  }

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int NORMAL = 0;
  private static final int LOW = 1;
  private static final int FULL = 2;
  private static final String INSTANCENAME = "Disk Space Monitor";
  private final HashMap<File, List<MonitoredDirectory>> monitoredDirs = new HashMap<>();

  /**
   * Constructs a new DiskSpaceMonitor that will notify registered DiskSpaceMonitorHandler objects when filesystems
   * on which configured directories reside, fall below the provided thresholds.
   */
  public DiskSpaceMonitor()
  {
  }

  /** Starts periodic monitoring of all registered directories. */
  public void startDiskSpaceMonitor()
  {
    DirectoryServer.registerMonitorProvider(this);
    DirectoryServer.registerShutdownListener(this);
    scheduleUpdate(this, 0, 5, TimeUnit.SECONDS);
  }

  /**
   * Registers or reconfigures a directory for monitoring.
   * If possible, we will try to get and use the mountpoint where the directory resides and monitor it instead.
   * If the directory is already registered for the same <code>handler</code>, simply change its configuration.
   * @param instanceName A name for the handler, as used by cn=monitor
   * @param directory The directory to monitor
   * @param lowThresholdBytes Disk slow threshold expressed in bytes
   * @param fullThresholdBytes Disk full threshold expressed in bytes
   * @param handler The class requesting to be called when a transition in disk space occurs
   */
  public void registerMonitoredDirectory(String instanceName, File directory, long lowThresholdBytes,
      long fullThresholdBytes, DiskSpaceMonitorHandler handler)
  {
    File fsMountPoint;
    try
    {
      fsMountPoint = getMountPoint(directory);
    }
    catch (IOException ioe)
    {
      logger.warn(ERR_DISK_SPACE_GET_MOUNT_POINT, directory.getAbsolutePath(), ioe.getLocalizedMessage());
      fsMountPoint = directory;
    }
    MonitoredDirectory newDSH = new MonitoredDirectory(directory, instanceName, INSTANCENAME, handler);
    newDSH.setFullThreshold(fullThresholdBytes);
    newDSH.setLowThreshold(lowThresholdBytes);

    synchronized (monitoredDirs)
    {
      List<MonitoredDirectory> diskHelpers = monitoredDirs.get(fsMountPoint);
      if (diskHelpers == null)
      {
        monitoredDirs.put(fsMountPoint, newArrayList(newDSH));
      }
      else
      {
        for (MonitoredDirectory elem : diskHelpers)
        {
          if (elem.handler.equals(handler) && elem.getDirectory().equals(directory))
          {
            elem.setFullThreshold(fullThresholdBytes);
            elem.setLowThreshold(lowThresholdBytes);
            return;
          }
        }
        diskHelpers.add(newDSH);
      }
      DirectoryServer.registerMonitorProvider(newDSH);
    }
  }

  private File getMountPoint(File directory) throws IOException
  {
    Path mountPoint = directory.getAbsoluteFile().toPath();
    Path parentDir = mountPoint.getParent();
    FileStore dirFileStore = Files.getFileStore(mountPoint);
    /*
     * Since there is no concept of mount point in the APIs, iterate on all parents of
     * the given directory until the FileSystem Store changes (hint of a different
     * device, hence a mount point) or we get to root, which works too.
     */
    while (parentDir != null)
    {
      if (!Files.getFileStore(parentDir).equals(dirFileStore))
      {
        return mountPoint.toFile();
      }
      mountPoint = mountPoint.getParent();
      parentDir = parentDir.getParent();
    }
    return mountPoint.toFile();
  }

  /**
   * Removes a directory from the set of monitored directories.
   *
   * @param directory The directory to stop monitoring on
   * @param handler The class that requested monitoring
   */
  public void deregisterMonitoredDirectory(File directory, DiskSpaceMonitorHandler handler)
  {
    synchronized (monitoredDirs)
    {
      List<MonitoredDirectory> directories = monitoredDirs.get(directory);
      if (directories != null)
      {
        Iterator<MonitoredDirectory> itr = directories.iterator();
        while (itr.hasNext())
        {
          MonitoredDirectory curDirectory = itr.next();
          if (curDirectory.handler.equals(handler))
          {
            DirectoryServer.deregisterMonitorProvider(curDirectory);
            itr.remove();
          }
        }
        if (directories.isEmpty())
        {
          monitoredDirs.remove(directory);
        }
      }
    }
  }

  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException, InitializationException {
    // Not used...
  }

  @Override
  public String getMonitorInstanceName() {
    return INSTANCENAME;
  }

  @Override
  public MonitorData getMonitorData()
  {
    return new MonitorData(0);
  }

  @Override
  public void run()
  {
    List<HandlerNotifier> diskFull = new ArrayList<>();
    List<HandlerNotifier> diskLow = new ArrayList<>();
    List<HandlerNotifier> diskRestored = new ArrayList<>();

    synchronized (monitoredDirs)
    {
      for (Entry<File, List<MonitoredDirectory>> dirElem : monitoredDirs.entrySet())
      {
        File directory = dirElem.getKey();
        HandlerNotifier diskFullClients = new HandlerNotifier(directory, FULL);
        HandlerNotifier diskLowClients = new HandlerNotifier(directory, LOW);
        HandlerNotifier diskRestoredClients = new HandlerNotifier(directory, NORMAL);
        try
        {
          long lastFreeSpace = directory.getUsableSpace();
          for (MonitoredDirectory handlerElem : dirElem.getValue())
          {
            if (lastFreeSpace < handlerElem.getFullThreshold() && handlerElem.lastState < FULL)
            {
              diskFullClients.addHandler(handlerElem);
            }
            else if (lastFreeSpace < handlerElem.getLowThreshold() && handlerElem.lastState < LOW)
            {
              diskLowClients.addHandler(handlerElem);
            }
            else if (handlerElem.lastState != NORMAL)
            {
              diskRestoredClients.addHandler(handlerElem);
            }
          }
          addToList(diskFull, diskFullClients);
          addToList(diskLow, diskLowClients);
          addToList(diskRestored, diskRestoredClients);
        }
        catch(Exception e)
        {
          logger.error(ERR_DISK_SPACE_MONITOR_UPDATE_FAILED, directory, e);
          logger.traceException(e);
        }
      }
    }
    // It is probably better to notify handlers outside of the synchronized section.
    sendNotification(diskFull, FULL, ALERT_DESCRIPTION_DISK_FULL);
    sendNotification(diskLow, LOW, ALERT_TYPE_DISK_SPACE_LOW);
    sendNotification(diskRestored, NORMAL, null);
  }

  private void addToList(List<HandlerNotifier> hnList, HandlerNotifier notifier)
  {
    if (!notifier.isEmpty())
    {
      hnList.add(notifier);
    }
  }

  private void sendNotification(List<HandlerNotifier> diskList, int state, String alert)
  {
    for (HandlerNotifier dirElem : diskList)
    {
      String dirPath = dirElem.directory.getAbsolutePath();
      String handlerNames = dirElem.diskNames.toString();
      long freeSpace = dirElem.directory.getFreeSpace();
      if (state == FULL)
      {
        DirectoryServer.sendAlertNotification(this, alert,
            ERR_DISK_SPACE_FULL_THRESHOLD_REACHED.get(dirPath, handlerNames, freeSpace));
      }
      else if (state == LOW)
      {
        DirectoryServer.sendAlertNotification(this, alert,
            ERR_DISK_SPACE_LOW_THRESHOLD_REACHED.get(dirPath, handlerNames, freeSpace));
      }
      else
      {
        logger.error(NOTE_DISK_SPACE_RESTORED.get(freeSpace, dirPath));
      }
      dirElem.notifyHandlers();
    }
  }

  @Override
  public DN getComponentEntryDN()
  {
    try
    {
      return DN.valueOf("cn=" + INSTANCENAME);
    }
    catch (LocalizedIllegalArgumentException ignored)
    {
      return DN.rootDN();
    }
  }

  @Override
  public String getClassName()
  {
    return DiskSpaceMonitor.class.getName();
  }

  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<>();
    alerts.put(ALERT_TYPE_DISK_SPACE_LOW, ALERT_DESCRIPTION_DISK_SPACE_LOW);
    alerts.put(ALERT_TYPE_DISK_FULL, ALERT_DESCRIPTION_DISK_FULL);
    return alerts;
  }

  @Override
  public String getShutdownListenerName()
  {
    return INSTANCENAME;
  }

  @Override
  public void processServerShutdown(LocalizableMessage reason)
  {
    synchronized (monitoredDirs)
    {
      for (Entry<File, List<MonitoredDirectory>> dirElem : monitoredDirs.entrySet())
      {
        for (MonitoredDirectory handlerElem : dirElem.getValue())
        {
          DirectoryServer.deregisterMonitorProvider(handlerElem);
        }
      }
    }
  }
}
