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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;

/**
 * This class implements a post rotation action that compresses
 * the file using ZIP compression.
 */
public class ZIPAction implements PostRotationAction
{

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
  public ZIPAction(String origFile, String newFile, boolean deleteOrig)
  {
    this.originalFile = new File(origFile);
    this.newFile = new File(newFile);
    this.deleteOriginal = deleteOrig;
  }

  /**
   * The compression action that is executed. Returns true if the
   * compression succeeded and false otherwise.
   *
   * @return  <CODE>true</CODE> if the compression was successful, or
   *          <CODE>false</CODE> if it was not.
   */
  public boolean execute()
  {
    FileInputStream fis = null;
    ZipOutputStream zip = null;
    boolean inputStreamOpen = false;
    boolean outputStreamOpen = false;

    try
    {
      if(!originalFile.exists())
      {
        System.err.println("Source file does not exist:" + originalFile);
        return false;
      }

      fis = new FileInputStream(originalFile);
      inputStreamOpen = true;
      FileOutputStream fos = new FileOutputStream(newFile);
      zip = new ZipOutputStream(fos);
      outputStreamOpen = true;

      ZipEntry zipEntry = new ZipEntry(originalFile.getName());
      zip.putNextEntry(zipEntry);

      byte[] buf = new byte[8192];
      int n;

      while((n = fis.read(buf)) != -1)
      {
        zip.write(buf, 0, n);
      }

      zip.close();
      outputStreamOpen = false;
      fis.close();
      inputStreamOpen = false;

      if(deleteOriginal)
      {
        if(!originalFile.delete())
        {
          System.err.println("Cannot delete original file:" + originalFile);
          return false;
        }
      }

      return true;
    } catch(IOException ioe)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, ioe);
      }
      if (inputStreamOpen)
      {
        try
        {
          fis.close();
        }
        catch (Exception fe)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, fe);
          }
          // Cannot do much. Ignore.
        }
      }
      if (outputStreamOpen)
      {
        try
        {
          zip.close();
        }
        catch (Exception ze)
        {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, ze);
          }
          // Cannot do much. Ignore.
        }
      }
      return false;
    }
  }


}

