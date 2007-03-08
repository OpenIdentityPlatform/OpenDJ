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

import org.opends.server.util.TimeThread;

import java.io.File;

/**
 * A file name policy that names files suffixed by the time it was created.
 */
public class TimeStampNaming implements FileNamingPolicy
{
  File file;

  /**
   * Create a new instance of the TimeStampNaming policy. Files will be created
   * with the names in the prefix.utctime format.
   *
   * @param file the file to use as the naming prefix.
   */
  public TimeStampNaming(File file)
  {
    this.file = file;
  }
  /**
   * Initializes the policy and returns the current name to use.
   *
   * @return the initial file.
   */
  public File getInitialName()
  {
    return file;
  }

  /**
   * Gets the next name to use.
   *
   * @return the next file.
   */
  public File getNextName()
  {
    return new File(file + "." + TimeThread.getGMTTime());
  }
}
