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
import java.util.Comparator;




/**
 * This class implements a comparator that can compare two files based on the
 * time that they were last modified.
 */
class FileComparator implements Comparator<File>
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
  @Override
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

