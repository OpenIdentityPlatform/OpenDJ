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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.FilenameFilter;

/**
 * A FileNamingPolicy is used by a MultiFileWriter to generate the
 * sequence of file names to use when writing.
 */
public interface FileNamingPolicy
{
  /**
   * Initializes the policy and returns the current name to use.
   *
   * @return the initial file.
   */
  public File getInitialName();

  /**
   * Gets the next name to use.
   *
   * @return the next file.
   */
  public File getNextName();

  /**
   * Gets the filename filter that can be used to filter files named by this
   * policy.
   *
   * @return The FilenameFilter that can filter files named by this policy.
   */
  public FilenameFilter getFilenameFilter();

  /**
   * Gets all the existing files named by this policy in the parent directoy
   * of the initial file. The initial file is excluded from this list if it
   * exists.
   *
   * @return The files named by this policy or <code>null</code> if an
   *         error occured.
   */
  public File[] listFiles();


}
