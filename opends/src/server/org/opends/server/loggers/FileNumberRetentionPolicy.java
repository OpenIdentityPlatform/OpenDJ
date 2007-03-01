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
import java.util.Arrays;

/**
 * This class implements a retention policy based on the number of files.
 * Files will be cleaned up based on the number of files on disk.
 */
public class FileNumberRetentionPolicy implements RetentionPolicy
{

  private int numFiles = 0;
  private File directory = null;
  private String prefix = null;

  /**
   * Create the retention policy based on the number of files.
   *
   * @param dir      The directory in which the log files reside.
   * @param prefix   The prefix for the log file names.
   * @param numFiles The number of files on disk.
   */
  public FileNumberRetentionPolicy(String dir, String prefix, int numFiles)
  {
    this.numFiles = numFiles;
    this.directory = new File(dir);
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

    File[] selectedFiles = directory.listFiles(new LogFileFilter(prefix));
    if (selectedFiles.length <= numFiles)
    {
      return 0;
    }

    // Sort files based on last modified time.
    Arrays.sort(selectedFiles, new FileComparator());

    for (int j = numFiles; j < selectedFiles.length; j++)
    {
      // System.out.println("Deleting log file:" + selectedFiles[j]);
      selectedFiles[j].delete();
      count++;
    }

    return count;
  }

}

