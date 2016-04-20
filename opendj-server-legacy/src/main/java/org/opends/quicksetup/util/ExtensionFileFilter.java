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

  @Override
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

  @Override
  public String getDescription()
  {
    return description;
  }
}
