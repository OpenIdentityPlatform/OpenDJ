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
package org.opends.server.loggers;

import java.io.File;
import java.util.Comparator;




/**
 * This class implements a comparator that can compare two files based on the
 * time that they were last modified.
 */
public class FileComparator implements Comparator<File>
{
  /**
   * Compare two files based on file modification time.
   *
   * @param  o1  The first file to be compared.
   * @param  o2  The second file to be compared.
   *
   * @return  A negative value if the first file was the most recently modified,
   *          a positive value if the second was the most recently modified, or
   *          zero if there is no discernible difference between the last modify
   *          times.
   */
  public int compare(File o1, File o2)
  {
    if(o1 == o2)
    {
      return 0;
    }

    if (o1.lastModified() > o2.lastModified())
    {
      return -1;
    } else if (o1.lastModified() < o2.lastModified())
    {
      return 1;
    } else
    {
      return 0;
    }
  }

}

