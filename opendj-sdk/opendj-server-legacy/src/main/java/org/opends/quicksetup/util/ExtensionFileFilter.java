/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.quicksetup.util;

import java.io.File;

import javax.swing.filechooser.FileFilter;
import static com.forgerock.opendj.util.OperatingSystem.isWindows;

/**
 * This is a class used to be able to filter on certain type of files
 * in the File Browser dialog.
 */
public class ExtensionFileFilter extends FileFilter
{
  private String extension;

  private String description;

  /**
   * The ExtensionFiter constructor.
   * @param extension the extension of the file we want to filter on.
   * @param description the description for the extension.
   */
  public ExtensionFileFilter(String extension, String description)
  {
    this.extension = extension;
    this.description = description;
  }

  /** {@inheritDoc} */
  public boolean accept(File f)
  {
    boolean accept = false;
    if (f != null)
    {
      if (f.isDirectory())
      {
        accept = true;
      } else if (isWindows())
      {
        accept =
            f.getName().toLowerCase().endsWith("." + extension.toLowerCase());
      } else
      {
        accept = f.getName().endsWith("." + extension);
      }
    }
    return accept;
  }

  /** {@inheritDoc} */
  public String getDescription()
  {
    return description;
  }
}
