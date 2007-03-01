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
package org.opends.server.tools.makeldif;



import java.io.IOException;

import org.opends.server.api.DirectoryThread;



/**
 * This class defines a thread that will be used in conjunction with the
 * MakeLDIF input stream to actually generate the data.
 */
class MakeLDIFInputStreamThread
      extends DirectoryThread
{
  // The MakeLDIF input stream that this thread is feeding.
  private MakeLDIFInputStream inputStream;

  // The template file to use to generate the entries.
  private TemplateFile templateFile;



  /**
   * Creates a new instance of this MakeLDIF input stream thread that will feed
   * the provided input strema.
   *
   * @param  inputStream   The MakeLDIF input stream that this thread will feed.
   * @param  templateFile  The template file to use to generate the entries.
   */
  public MakeLDIFInputStreamThread(MakeLDIFInputStream inputStream,
                                   TemplateFile templateFile)
  {
    super("MakeLDIF Input Stream Thread");

    this.inputStream  = inputStream;
    this.templateFile = templateFile;
  }



  /**
   * Operates in a loop, generating entries and feeding them to the input stream
   * until either all entries have been generated or the input stream is closed.
   */
  public void run()
  {
    try
    {
      templateFile.generateLDIF(inputStream);
    }
    catch (MakeLDIFException mle)
    {
      inputStream.setIOException(new IOException(mle.getMessage()));
      inputStream.closeEntryWriter();
    }
    catch (IOException ioe)
    {
      inputStream.setIOException(ioe);
      inputStream.closeEntryWriter();
    }
  }
}

