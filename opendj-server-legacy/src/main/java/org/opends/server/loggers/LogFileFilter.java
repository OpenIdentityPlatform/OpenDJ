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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.FilenameFilter;


/** This class defines a filename filter that will be used for log files. */
class LogFileFilter implements FilenameFilter
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
  @Override
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

