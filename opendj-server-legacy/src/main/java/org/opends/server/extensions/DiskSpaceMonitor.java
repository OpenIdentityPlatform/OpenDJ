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
 *
 *       Copyright 2010 Sun Microsystems, Inc.
 *       Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.DiskSpaceMonitorHandler;
import org.opends.server.api.MonitorProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
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
public class DiskSpaceMonitor extends MonitorProvider<MonitorProviderCfg>
    implements Runnable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final int NORMAL = 0;
  private static final int LOW = 1;
  private static final int FULL = 2;

  private volatile File directory;
  private volatile long lowThreshold;
  private volatile long fullThreshold;
  private final DiskSpaceMonitorHandler handler;
  private final int interval;
  private final TimeUnit unit;
  private final String instanceName;
  private int lastState;

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
    return lastState >= FULL;
  }

  /**
   * Indicates if the "low" threshold is reached.
   *
   * @return <code>true</code> if the free space is lower than the "low"
   *         threshold or <code>false</code> otherwise.
   */
  public boolean isLowThresholdReached()
  {
    return lastState >= LOW;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeMonitorProvider(MonitorProviderCfg configuration)
      throws ConfigException, InitializationException {
    scheduleUpdate(this, 0, interval, unit);
  }

  /** {@inheritDoc} */
  @Override
  public String getMonitorInstanceName() {
    return instanceName;
  }

  /** {@inheritDoc} */
  @Override
  public List<Attribute> getMonitorData() {
    final ArrayList<Attribute> monitorAttrs = new ArrayList<Attribute>();
    monitorAttrs.add(attr("disk-dir", getDefaultStringSyntax(), directory.getPath()));
    monitorAttrs.add(attr("disk-free", getDefaultIntegerSyntax(), getFreeSpace()));
    monitorAttrs.add(attr("disk-state", getDefaultStringSyntax(), getState()));
    return monitorAttrs;
  }

  private Attribute attr(String name, AttributeSyntax<?> syntax, Object value)
  {
    AttributeType attrType = DirectoryServer.getDefaultAttributeType(name, syntax);
    return Attributes.create(attrType, String.valueOf(value));
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

  /** {@inheritDoc} */
  @Override
  public void run() {
    try
    {
      long lastFreeSpace = directory.getUsableSpace();

      if(logger.isTraceEnabled())
      {
        logger.trace("Free space for %s: %d, " +
            "low threshold: %d, full threshold: %d, state: %d",
            directory.getPath(), lastFreeSpace, lowThreshold, fullThreshold,
            lastState);
      }

      if(lastFreeSpace < fullThreshold)
      {
        if (lastState < FULL)
        {
          if(logger.isTraceEnabled())
          {
            logger.trace("State change: %d -> %d", lastState, FULL);
          }

          lastState = FULL;
          if(handler != null)
          {
            handler.diskFullThresholdReached(this);
          }
        }
      }
      else if(lastFreeSpace < lowThreshold)
      {
        if (lastState < LOW)
        {
          if(logger.isTraceEnabled())
          {
            logger.trace("State change: %d -> %d", lastState, LOW);
          }
          lastState = LOW;
          if(handler != null)
          {
            handler.diskLowThresholdReached(this);
          }
        }
      }
      else if (lastState != NORMAL)
      {
        if(logger.isTraceEnabled())
        {
          logger.trace("State change: %d -> %d", lastState, NORMAL);
        }
        lastState = NORMAL;
        if(handler != null)
        {
          handler.diskSpaceRestored(this);
        }
      }
    }
    catch(Exception e)
    {
      logger.error(ERR_DISK_SPACE_MONITOR_UPDATE_FAILED, directory.getPath(), e);
      logger.traceException(e);
    }
  }
}
