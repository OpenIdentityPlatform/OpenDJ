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
 * Portions Copyright 2015-2016 ForgeRock AS.
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
  /** The MakeLDIF input stream that this thread is feeding. */
  private MakeLDIFInputStream inputStream;

  /** The template file to use to generate the entries. */
  private TemplateFile templateFile;



  /**
   * Creates a new instance of this MakeLDIF input stream thread that will feed
   * the provided input stream.
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
  @Override
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

