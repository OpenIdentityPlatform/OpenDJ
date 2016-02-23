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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.tools;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.RecordingOutputStream;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a utility that can be used to write LDAP messages over a
 * provided socket.
 */
public class LDAPWriter implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private Socket socket;
  private ASN1Writer asn1Writer;
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
    if(logger.isTraceEnabled())
    {
      logger.trace(message.toString());
      debugOutputStream.setRecordingEnabled(true);
    }

    message.write(asn1Writer);
    asn1Writer.flush();

    if(debugOutputStream.isRecordingEnabled())
    {
      ByteString bytesRead = debugOutputStream.getRecordedBytes();
      debugOutputStream.clearRecordedBytes();

      logger.trace("bytes written to wire(len=" + bytesRead.length() + "):"
          + ServerConstants.EOL + bytesRead.toHexPlusAsciiString(4));
    }
  }

  /**
   * Closes this LDAP writer and the underlying socket.
   */
  @Override
  public void close()
  {
    StaticUtils.close(asn1Writer, debugOutputStream);
    StaticUtils.close(socket);
  }
}
