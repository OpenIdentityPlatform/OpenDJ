/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.server.tools;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RecordingInputStream;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.StaticUtils;

/**
 * This class defines a utility that can be used to read LDAP messages from a
 * provided socket.
 */
public class LDAPReader implements Closeable
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

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
   * @throws  DecodeException  If a problem occurs while attempting to decode the
   *                         data read as an ASN.1 sequence.

   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         LDAP message.
   */
  public LDAPMessage readMessage()
       throws IOException, DecodeException, LDAPException
  {
    debugInputStream.setRecordingEnabled(logger.isTraceEnabled());

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

      logger.trace("bytes read from wire(len=" + bytesRead.length() + "):"
          + ServerConstants.EOL + bytesRead.toHexPlusAsciiString(4));
      logger.trace(message.toString());
    }

    return message;
  }

  /**
   * Closes this LDAP reader and the underlying socket.
   */
  @Override
  public void close()
  {
    StaticUtils.close(asn1Reader);
    StaticUtils.close(socket);
  }
}
