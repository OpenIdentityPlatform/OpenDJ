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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.FilenameFilter;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.util.TimeThread;

/** A file name policy that names files suffixed by the time it was created. */
public class TimeStampNaming implements FileNamingPolicy
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private File file;
  private TimeStampNamingFilter filter;

  /**
   * The FilenameFilter implementation for this naming policy to filter
   * for all the files named by this policy.
   */
  private class TimeStampNamingFilter implements FilenameFilter
  {
    /**
     * Select only files that are named by this policy.
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

      String initialFileName = file.getName();

      // Make sure it is the expected length.
      if(name.length() != initialFileName.length() + 16)
      {
        return false;
      }

      int pos;
      // Make sure we got the expected name prefix.
      for(pos = 0; pos < initialFileName.length(); pos++)
      {
        if(name.charAt(pos) != initialFileName.charAt(pos))
        {
          return false;
        }
      }

      // Make sure there is a period between the prefix and timestamp.
      if(name.charAt(pos) != '.')
      {
        return false;
      }

      char c;
      // Make sure there are 14 numbers for the timestamp.
      for(pos++; pos < name.length() - 1; pos++)
      {
        c = name.charAt(pos);
        if(c < 48 || c > 57)
        {
          return false;
        }
      }

      // And ends with an Z.
      return name.charAt(pos) == 'Z';

    }
  }

  /**
   * Create a new instance of the TimeStampNaming policy. Files will be created
   * with the names in the prefix.utctime format.
   *
   * @param file the file to use as the naming prefix.
   */
  public TimeStampNaming(File file)
  {
    this.file = file;
    this.filter = new TimeStampNamingFilter();
  }

  @Override
  public File getInitialName()
  {
    return file;
  }

  @Override
  public File getNextName()
  {
    return new File(file + "." + TimeThread.getGMTTime());
  }

  @Override
  public FilenameFilter getFilenameFilter()
  {
    return filter;
  }

  @Override
  public File[] listFiles()
  {
    File directory = file.getParentFile();
    File[] files =  directory.listFiles(filter);

    if(files == null)
    {
      logger.trace("Unable to list files named by policy " +
          "with initial file %s in directory %s", file, directory);
    }

    return files;
  }

}
