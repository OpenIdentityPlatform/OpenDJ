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
 *       Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;

import static org.opends.messages.CoreMessages.
    ERR_DISK_SPACE_MONITOR_UPDATE_FAILED;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
public class DiskSpaceMonitor extends MonitorProvider<MonitorProviderCfg>
    implements Runnable
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private volatile File directory;
  private volatile long lowThreshold;
  private volatile long fullThreshold;
  private final DiskSpaceMonitorHandler handler;
  private final int interval;
  private final TimeUnit unit;
  private final String instanceName;
  private int lastState = 0;

  /**
   * Constructs a new DiskSpaceMonitor that will notify the specified
   * DiskSpaceMonitorHandler when the specified disk
   * falls below the provided thresholds.
   *
   * @param instanceName A unique name for this monitor.
   * @param directory The directory to monitor.
   * @param lowThreshold The "low" threshold.
   * @param fullThreshold   The "full" threshold.
   * @param interval  The polling interval for checking free space.
   * @param unit the time unit of the interval parameter.
   * @param handler The handler to get notified when the provided thresholds are
   *                reached or <code>null</code> if no notification is needed;
   */
  public DiskSpaceMonitor(String instanceName, File directory,
                          long lowThreshold,
                          long fullThreshold, int interval, TimeUnit unit,
                          DiskSpaceMonitorHandler handler) {
    this.directory = directory;
    this.lowThreshold = lowThreshold;
    this.fullThreshold = fullThreshold;
    this.interval = interval;
    this.unit = unit;
    this.handler = handler;
    this.instanceName = instanceName+",cn=Disk Space Monitor";
  }

  /**
   * Retrieves the directory currently being monitored.
   *
   * @return The directory currently being monitored.
   */
  public File getDirectory() {
    return directory;
  }

  /**
   * Sets the directory to monitor.
   *
   * @param directory The directory to monitor.
   */
  public void setDirectory(File directory) {
    this.directory = directory;
  }

  /**
   * Retrieves the currently "low" space threshold currently being enforced.
   *
   * @return The currently "low" space threshold currently being enforced.
   */
  public long getLowThreshold() {
    return lowThreshold;
  }

  /**
   * Sets the "low" space threshold to enforce.
   *
   * @param lowThreshold The "low" space threshold to enforce.
   */
  public void setLowThreshold(long lowThreshold) {
    this.lowThreshold = lowThreshold;
  }

  /**
   * Retrieves the currently full threshold currently being enforced.
   *
   * @return The currently full space threshold currently being enforced.
   */
  public long getFullThreshold() {
    return fullThreshold;
  }

  /**
   * Sets the full threshold to enforce.
   *
   * @param fullThreshold The full space threshold to enforce.
   */
  public void setFullThreshold(long fullThreshold) {
    this.fullThreshold = fullThreshold;
  }

  /**
   * Retrieves the free space currently on the disk.
   *
   * @return The free space currently on the disk.
   */
  public long getFreeSpace() {
    return directory.getUsableSpace();
  }

  /**
   * Indicates if the "full" threshold is reached.
   *
   * @return <code>true</code> if the free space is lower than the "full"
   *         threshold or <code>false</code> otherwise.
   */
  public boolean isFullThresholdReached()
  {
    return lastState >= 2;
  }

  /**
   * Indicates if the "low" threshold is reached.
   *
   * @return <code>true</code> if the free space is lower than the "low"
   *         threshold or <code>false</code> otherwise.
   */
  public boolean isLowThresholdReached()
  {
    return lastState >= 1;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException, InitializationException {
    scheduleUpdate(this, 0, interval, unit);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getMonitorInstanceName() {
    return instanceName;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Attribute> getMonitorData() {
    ArrayList<Attribute> monitorAttrs = new ArrayList<Attribute>();

    AttributeType attrType =
        DirectoryServer.getDefaultAttributeType("disk-dir",
            DirectoryServer.getDefaultStringSyntax());
    monitorAttrs.add(Attributes.create(attrType, directory.getPath()));

    attrType =
        DirectoryServer.getDefaultAttributeType("disk-free",
            DirectoryServer.getDefaultIntegerSyntax());
    monitorAttrs.add(Attributes.create(attrType,
        String.valueOf(getFreeSpace())));

    attrType =
        DirectoryServer.getDefaultAttributeType("disk-state",
            DirectoryServer.getDefaultStringSyntax());
    switch(lastState)
    {
      case 0 : monitorAttrs.add(Attributes.create(attrType, "normal"));
        break;
      case 1 : monitorAttrs.add(Attributes.create(attrType, "low"));
        break;
      case 2 : monitorAttrs.add(Attributes.create(attrType, "full"));
        break;
    }


    return monitorAttrs;
  }

  /**
   * {@inheritDoc}
   */
  public void run() {
    try
    {
      long lastFreeSpace = directory.getUsableSpace();

      if(debugEnabled())
      {
        TRACER.debugInfo("Free space for %s: %d, " +
            "low threshold: %d, full threshold: %d, state: %d",
            directory.getPath(), lastFreeSpace, lowThreshold, fullThreshold,
            lastState);
      }

      if(lastFreeSpace < fullThreshold)
      {
        if(lastState < 2)
        {
          if(debugEnabled())
          {
            TRACER.debugInfo("State change: %d -> %d", lastState, 2);
          }

          lastState = 2;
          if(handler != null)
          {
            handler.diskFullThresholdReached(this);
          }
        }
      }
      else if(lastFreeSpace < lowThreshold)
      {
        if(lastState < 1)
        {
          if(debugEnabled())
          {
            TRACER.debugInfo("State change: %d -> %d", lastState, 1);
          }
          lastState = 1;
          if(handler != null)
          {
            handler.diskLowThresholdReached(this);
          }
        }
      }
      else if(lastState != 0)
      {
        if(debugEnabled())
        {
          TRACER.debugInfo("State change: %d -> %d", lastState, 0);
        }
        lastState = 0;
        if(handler != null)
        {
          handler.diskSpaceRestored(this);
        }
      }
    }
    catch(Exception e)
    {
      ErrorLogger.logError(ERR_DISK_SPACE_MONITOR_UPDATE_FAILED.get(
          directory.getPath(), e.toString()));

      TRACER.debugCaught(DebugLogLevel.ERROR, e);
    }
  }
}
