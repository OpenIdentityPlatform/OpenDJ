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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.FilenameFilter;

/**
 * A FileNamingPolicy is used by a MultiFileWriter to generate the
 * sequence of file names to use when writing.
 */
interface FileNamingPolicy
{
  /**
   * Initializes the policy and returns the current name to use.
   *
   * @return the initial file.
   */
  File getInitialName();

  /**
   * Gets the next name to use.
   *
   * @return the next file.
   */
  File getNextName();

  /**
   * Gets the filename filter that can be used to filter files named by this
   * policy.
   *
   * @return The FilenameFilter that can filter files named by this policy.
   */
  FilenameFilter getFilenameFilter();

  /**
   * Gets all the existing files named by this policy in the parent directory
   * of the initial file. The initial file is excluded from this list if it
   * exists.
   *
   * @return The files named by this policy or <code>null</code> if an
   *         error occurred.
   */
  File[] listFiles();


}
