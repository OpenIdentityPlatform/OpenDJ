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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.ldap.LDAPMessage;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.RecordingOutputStream;
import org.opends.server.types.ByteString;
import org.opends.server.util.ServerConstants;

import java.net.Socket;
import java.io.IOException;
import java.io.BufferedOutputStream;

/**
 * This class defines a utility that can be used to write LDAP messages over a
 * provided socket.
 */
public class LDAPWriter
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  Socket socket;
  ASN1Writer asn1Writer;
  private RecordingOutputStream debugOutputStream;


  /**
   * Creates a new LDAP writer that will write messages to the provided
   * socket and trace the messages using a provided tracer.
   *
   * @param  socket  The socket to use to write LDAP messages.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       output stream for the socket.
   */
  public LDAPWriter(Socket socket)
       throws IOException
  {
    this.socket = socket;
    this.debugOutputStream =
        new RecordingOutputStream(
        new BufferedOutputStream(socket.getOutputStream(), 4096));
    this.asn1Writer = ASN1.getWriter(debugOutputStream);
  }

  /**
   * Writes an LDAP message to the associated output stream.
   *
   * @param   message      The message to be written.
   *
   * @throws  IOException  If a problem occurs while trying to write the
   *                       information over the output stream.
   */
  public void writeMessage(LDAPMessage message)
       throws IOException
  {
    if(debugEnabled())
    {
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, message.toString());
      debugOutputStream.setRecordingEnabled(true);
    }

    message.write(asn1Writer);
    asn1Writer.flush();

    if(debugOutputStream.isRecordingEnabled())
    {
      ByteString bytesRead = debugOutputStream.getRecordedBytes();
      debugOutputStream.clearRecordedBytes();

      StringBuilder builder = new StringBuilder();
      builder.append("bytes written to wire(len=");
      builder.append(bytesRead.length());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      bytesRead.toHexPlusAscii(builder, 4);

      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, builder.toString());
    }
  }

  /**
   * Closes this LDAP writer and the underlying socket.
   */
  public void close()
  {
    try
    {
      asn1Writer.close();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
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
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }
}
