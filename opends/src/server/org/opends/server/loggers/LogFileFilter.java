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
import java.io.FilenameFilter;


/**
 * This class defines a filename filter that will be used for log files.
 */
public class LogFileFilter implements FilenameFilter
{
  private String prefix;

  /**
   * Create the filter with the specified file name prefix.
   *
   * @param  prefix  The filename prefix to use for the filter.
   */
  public LogFileFilter(String prefix)
  {
    this.prefix = prefix;
  }

  /**
   * Select only files that begin with the specified prefix.
   *
   * @param dir  The directory to search.
   * @param name The filename to which to apply the filter.
   *
   * @return  <CODE>true</CODE> if the given filename matches the filter, or
   *          <CODE>false</CODE> if it does not.
   */
  public boolean accept(File dir, String name)
  {
    if(new File(dir, name).isDirectory())
    {
      return false;
    }
    name = name.toLowerCase();
    return name.startsWith(prefix);
  }

}

