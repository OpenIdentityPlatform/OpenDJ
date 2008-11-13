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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools;

import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.util.ServerConstants;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A utility class for the LDAP client tools that performs verbose tracing of
 * LDAP and ASN.1 messages.
 */
public class VerboseTracer
{
  /**
   * Indicates whether verbose mode is on or off.
   */
  private boolean verbose;

  /**
   * The print stream where tracing will be sent.
   */
  private PrintStream err;

  /**
   * The time in milliseconds of the first message traced.
   */
  private long firstMessageTimestamp = 0;

  /**
   * The time in millseconds of the previous message traced.
   */
  private long lastMessageTimestamp = 0;

  /**
   * The format used for trace timestamps.
   */
  private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  /**
   * Constructs a tracer with a specified verbosity and print stream.
   * @param verbose Indicates whether verbose mode is on or off.
   * @param err The print stream where tracing will be sent.
   */
  public VerboseTracer(boolean verbose, PrintStream err)
  {
    this.verbose = verbose;
    this.err = err;
  }

  /**
   * Trace an incoming or outgoing message.
   * @param messageDirection  Use "C>S" to indicate outgoing client to server.
   *                          Use "S>C" to indicate incoming server to client.
   * @param message The LDAP message to be traced.
   * @param element The ASN.1 element of the message.
   */
  private synchronized void traceMessage(String messageDirection,
                                         LDAPMessage message,
                                         ASN1Element element)
  {
    StringBuilder header = new StringBuilder();
    StringBuilder builder = new StringBuilder();

    long timestamp = System.currentTimeMillis();
    long timeSinceLast;

    if (firstMessageTimestamp == 0)
    {
      firstMessageTimestamp = timestamp;
    }

    if (lastMessageTimestamp == 0)
    {
      lastMessageTimestamp = timestamp;
    }

    timeSinceLast = timestamp - lastMessageTimestamp;
    if (timeSinceLast < 0)
    {
      timeSinceLast = 0;
    }

    String timestampString = dateFormat.format(new Date(timestamp));

    header.append(messageDirection);
    header.append(' ');
    header.append(timestampString);

    // Include the number of milliseconds since the previous traced message.
    header.append(" (");
    header.append(timeSinceLast);
    header.append("ms) ");


    builder.append("LDAP: ");
    builder.append(header);
    builder.append(message);
    builder.append(ServerConstants.EOL);

    builder.append("ASN1: ");
    builder.append(header);
    element.toString(builder, 0);

    err.print(builder);

    if (timestamp > lastMessageTimestamp)
    {
      lastMessageTimestamp = timestamp;
    }
  }

  /**
   * Trace an incoming message.
   * @param message The LDAP message to be traced.
   * @param element The ASN.1 element of the message.
   */
  public void traceIncomingMessage(LDAPMessage message,
                                                ASN1Element element)
  {
    if (verbose)
    {
      traceMessage("S>C", message, element);
    }
  }


  /**
   * Trace an outgoing message.
   * @param message The LDAP message to be traced.
   * @param element The ASN.1 element of the message.
   */
  public void traceOutgoingMessage(LDAPMessage message,
                                                ASN1Element element)
  {
    if (verbose)
    {
      traceMessage("C>S", message, element);
    }
  }

}
