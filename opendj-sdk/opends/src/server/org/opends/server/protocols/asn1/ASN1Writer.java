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
package org.opends.server.protocols.asn1;



import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;


/**
 * This class defines a utility that can be used to write ASN.1 elements over a
 * provided socket or output stream.
 */
public class ASN1Writer
{



  // The output stream to which the encoded elements should be written.
  private OutputStream outputStream;

  // The socket with which the output stream is associated.
  private Socket socket;



  /**
   * Creates a new ASN.1 writer that will write elements over the provided
   * socket.
   *
   * @param  socket  The socket to use to write ASN.1 elements.
   *
   * @throws  IOException  If a problem occurs while trying to get the output
   *                       stream for the socket.
   */
  public ASN1Writer(Socket socket)
         throws IOException
  {

    this.socket  = socket;
    outputStream = socket.getOutputStream();
  }



  /**
   * Creates a new ASN.1 writer that will write elements over the provided
   * output stream.
   *
   * @param  outputStream  The output stream to use to write ASN.1 elements.
   */
  public ASN1Writer(OutputStream outputStream)
  {

    this.outputStream = outputStream;
    socket            = null;
  }



  /**
   * Writes the provided ASN.1 element over the output stream associated with
   * this ASN.1 writer.
   *
   * @param  element  The element to be written.
   *
   * @return  The number of bytes actually written over the output stream.
   *
   * @throws  IOException  If a problem occurs while trying to write the
   *                       information over the output stream.
   */
  public int writeElement(ASN1Element element)
         throws IOException
  {

    byte[] elementBytes = element.encode();
    outputStream.write(elementBytes);
    outputStream.flush();

    return elementBytes.length;
  }



  /**
   * Closes this ASN.1 writer and the underlying output stream/socket.
   */
  public void close()
  {
    try
    {
      outputStream.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }


    if (socket != null)
    {
      try
      {
        socket.close();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}

