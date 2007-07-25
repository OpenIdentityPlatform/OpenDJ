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

import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.ldap.LDAPMessage;

import java.net.Socket;
import java.io.IOException;

/**
 * This class defines a utility that can be used to write LDAP messages over a
 * provided socket.
 */
public class LDAPWriter
{
  ASN1Writer asn1Writer;
  VerboseTracer tracer;

  /**
   * Creates a new LDAP writer that will write messages to the provided
   * socket.
   *
   * @param  socket  The socket to use to write LDAP messages.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       ASN.1 reader for the socket.
   */
  public LDAPWriter(Socket socket)
       throws IOException
  {
    this(socket, null);
  }


  /**
   * Creates a new LDAP writer that will write messages to the provided
   * socket and trace the messages using a provided tracer.
   *
   * @param  socket  The socket to use to write LDAP messages.
   *
   * @param  tracer  Specifies a tracer to be used for tracing messages written.
   *
   * @throws  IOException  If a problem occurs while attempting to obtain an
   *                       output stream for the socket.
   */
  public LDAPWriter(Socket socket, VerboseTracer tracer)
       throws IOException
  {
    this.asn1Writer = new ASN1Writer(socket);
    this.tracer = tracer;
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
    ASN1Element element = message.encode();
    if (tracer != null)
    {
      tracer.traceOutgoingMessage(message, element);
    }
    asn1Writer.writeElement(element);
  }

  /**
   * Closes this LDAP writer and the underlying socket.
   */
  public void close()
  {
    asn1Writer.close();
  }

  /**
   * Get the underlying ASN1 writer.
   *
   * @return  The underlying ASN1 writer.
   */
  public ASN1Writer getASN1Writer()
  {
    return asn1Writer;
  }

}
