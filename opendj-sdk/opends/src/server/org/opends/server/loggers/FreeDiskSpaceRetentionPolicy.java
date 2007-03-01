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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;

/**
 * This class implements a retention policy based on the free disk
 * space available expressed as a percentage. This policy is only
 * available on Java 6.
 */
public class FreeDiskSpaceRetentionPolicy implements RetentionPolicy
{

  private long freeDiskSpace = 0;
  private File directory = null;
  private String prefix = null;

  /**
   * Create the retention policy based on the free disk space available.
   *
   * @param dir           The directory in which the log files reside.
   * @param prefix        The prefix for the log file names.
   * @param freeDiskSpace The free disk space needed.
   */
  public FreeDiskSpaceRetentionPolicy(String dir, String prefix,
                                 long freeDiskSpace)
  {

    this.directory = new File(dir);
    this.freeDiskSpace = freeDiskSpace;
    this.prefix = prefix;
  }


  /**
   * This method deletes files based on the policy.
   *
   * @return number of files deleted.
   */
  public int deleteFiles()
  {

    int count = 0;
    long freeSpace = 0;
    try
    {
      // Use reflection to see use the getFreeSpace method if available.
      // this method is only available on Java 6.
      Method meth = File.class.getMethod("getFreeSpace", new Class[0]);
      Object value = meth.invoke(this.directory);
      freeSpace = ((Long) value).longValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
      return 0;
    }

    if (freeSpace > freeDiskSpace)
    {
      // No cleaning needed.
      return 0;
    }

    long freeSpaceNeeded = freeDiskSpace - freeSpace;
    File[] selectedFiles = directory.listFiles(new LogFileFilter(prefix));

    // Sort files based on last modified time.
    Arrays.sort(selectedFiles, new FileComparator());

    long freedSpace = 0;
    for (int j = selectedFiles.length - 1; j < 1; j--)
    {
      freedSpace += selectedFiles[j].length();
      // System.out.println("Deleting log file:" + selectedFiles[j]);
      selectedFiles[j].delete();
      if (freedSpace >= freeSpaceNeeded)
      {
        break;
      }

      count++;
    }

    return count;
  }

}

