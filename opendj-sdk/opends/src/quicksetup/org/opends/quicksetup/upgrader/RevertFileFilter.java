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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import java.io.File;
import java.util.HashSet;

/**
   * Filter defining files we want to manage in the upgrade
 * process.
 */
class RevertFileFilter extends UpgradeFileFilter {

  /**
   * Creates a filter for ignoring in an OpenDS installation at
   * <code>root</code>certain OpenDS files below root.
   * @param root the root of the installation
   */
  public RevertFileFilter(File root) {
    super(root);
    this.filesToIgnore = new HashSet<File>();
    for (String rootFileNamesToIgnore :
            Upgrader.ROOT_FILES_TO_IGNORE_DURING_BACKUP) {
      filesToIgnore.add(new File(root, rootFileNamesToIgnore));
    }
  }
}

