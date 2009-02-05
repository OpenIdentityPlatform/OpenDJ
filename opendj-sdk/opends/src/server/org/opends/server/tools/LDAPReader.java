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

import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.LDAPException;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.RecordingInputStream;
import org.opends.server.types.ByteString;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.util.ServerConstants;

import java.io.IOException;
import java.net.Socket;

/**
 * This class defines a utility that can be used to read LDAP messages from a
 * provided socket.
 */
public class LDAPReader
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private Socket socket;
  private ASN1Reader asn1Reader;
  private RecordingInputStream debugInputStream;

  /**
   * Creates a new LDAP reader that will read messages from the provided
   * socket and trace the messages using a provided tracer.
   *
   * @param  socket   The socket from which to read the LDAP messages.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       input stream for the socket.
   */
  public LDAPReader(Socket socket)
       throws IOException
  {
    this.socket = socket;
    this.debugInputStream = new RecordingInputStream(socket.getInputStream());
    this.asn1Reader = ASN1.getReader(debugInputStream);
  }

  /**
   * Reads an LDAP message from the associated input stream.
   *
   * @return  The LDAP message read from the associated input stream, or
   *          <CODE>null</CODE> if the end of the stream has been reached.
   *
   * @throws  IOException  If a problem occurs while attempting to read from the
   *                       input stream.
   *
   * @throws  ASN1Exception  If a problem occurs while attempting to decode the
   *                         data read as an ASN.1 sequence.

   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         LDAP message.
   */
  public LDAPMessage readMessage()
       throws IOException, ASN1Exception, LDAPException
  {
    debugInputStream.setRecordingEnabled(debugEnabled());

    if(!asn1Reader.hasNextElement())
    {
      // EOF was reached...
      return null;
    }

    LDAPMessage message =
        org.opends.server.protocols.ldap.LDAPReader.readMessage(asn1Reader);

    if(debugInputStream.isRecordingEnabled())
    {
      ByteString bytesRead = debugInputStream.getRecordedBytes();
      debugInputStream.clearRecordedBytes();

      StringBuilder builder = new StringBuilder();
      builder.append("bytes read from wire(len=");
      builder.append(bytesRead.length());
      builder.append("):");
      builder.append(ServerConstants.EOL);
      bytesRead.toHexPlusAscii(builder, 4);

      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, builder.toString());
      TRACER.debugProtocolElement(DebugLogLevel.VERBOSE, message.toString());
    }

    return message;
  }

  /**
   * Closes this LDAP reader and the underlying socket.
   */
  public void close()
  {
    try
    {
      asn1Reader.close();
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
