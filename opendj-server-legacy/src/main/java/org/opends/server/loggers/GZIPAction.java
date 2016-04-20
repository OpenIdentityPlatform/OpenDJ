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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * This class implements a post rotation action that compresses
 * the file using GZIP compression.
 */
class GZIPAction implements PostRotationAction
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  private File originalFile;
  private File newFile;
  private boolean deleteOriginal;

  /**
   * Create the action based on the original file, the new file after
   * compression and whether the original file should be deleted.
   *
   * @param origFile    The source file name to compress.
   * @param newFile     The compressed file name.
   * @param deleteOrig  Whether the source file should be deleted after
   *                    compression or not.
   */
  GZIPAction(String origFile, String newFile, boolean deleteOrig)
  {
    this.originalFile = new File(origFile);
    this.newFile = new File(newFile);
    this.deleteOriginal = deleteOrig;
  }

  /**
   * The compression action that is executed. Returns true if the
   * compression succeeded and false otherwise.
   *
   * @return  <CODE>true</CODE> if the compression succeeded, or
   *          <CODE>false</CODE> if it did not.
   */
  @Override
  public boolean execute()
  {
    try
    {
      if(!originalFile.exists())
      {
        System.err.println("Source file does not exist:" + originalFile);
        return false;
      }

      try (FileInputStream fis = new FileInputStream(originalFile);
          FileOutputStream fos = new FileOutputStream(newFile);
          GZIPOutputStream gzip = new GZIPOutputStream(fos);)
      {
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) != -1)
        {
          gzip.write(buf, 0, n);
        }
      }

      if(deleteOriginal && !originalFile.delete())
      {
        System.err.println("Cannot delete original file:" + originalFile);
        return false;
      }

      return true;
    } catch(IOException ioe)
    {
      logger.traceException(ioe);
      return false;
    }
  }
}
