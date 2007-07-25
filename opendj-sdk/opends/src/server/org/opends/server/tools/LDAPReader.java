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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.LDAPException;

import java.io.IOException;
import java.net.Socket;

/**
 * This class defines a utility that can be used to read LDAP messages from a
 * provided socket.
 */
public class LDAPReader
{
  private ASN1Reader asn1Reader;
  private VerboseTracer tracer;

  /**
   * Creates a new LDAP reader that will read messages from the provided
   * socket.
   *
   * @param  socket  The socket from which to read the LDAP messages.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       ASN.1 reader for the socket.
   */
  public LDAPReader(Socket socket)
       throws IOException
  {
    this(socket, null);
  }

  /**
   * Creates a new LDAP reader that will read messages from the provided
   * socket and trace the messages using a provided tracer.
   *
   * @param  socket   The socket from which to read the LDAP messages.
   *
   * @param  tracer   Specifies a tracer to be used for tracing messages read.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       input stream for the socket.
   */
  public LDAPReader(Socket socket, VerboseTracer tracer)
       throws IOException
  {
    this.asn1Reader = new ASN1Reader(socket);
    this.tracer = tracer;
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
    ASN1Element element = asn1Reader.readElement();
    if (element == null)
    {
      return null;
    }

    ASN1Sequence sequence = ASN1Sequence.decodeAsSequence(element);
    LDAPMessage message = LDAPMessage.decode(sequence);
    if (tracer != null)
    {
      tracer.traceIncomingMessage(message, sequence);
    }

    return message;
  }

  /**
   * Closes this LDAP reader and the underlying socket.
   */
  public void close()
  {
    asn1Reader.close();
  }

  /**
   * Get the underlying ASN1 reader.
   *
   * @return  The underlying ASN1 reader.
   */
  public ASN1Reader getASN1Reader()
  {
    return asn1Reader;
  }


}
