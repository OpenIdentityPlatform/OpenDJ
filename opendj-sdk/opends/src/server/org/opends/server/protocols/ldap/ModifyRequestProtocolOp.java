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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap;


import java.util.ArrayList;
import java.util.Iterator;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.RawModification;
import org.opends.server.types.ByteString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the structures and methods for an LDAP modify request
 * protocol op, which is used to alter the contents of an entry in the Directory
 * Server.
 */
public class ModifyRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of modifications for this modify request.
  private ArrayList<RawModification> modifications;

  // The DN for this modify request.
  private ByteString dn;



  /**
   * Creates a new LDAP modify request protocol op with the specified DN and no
   * modifications.
   *
   * @param  dn  The DN for this modify request.
   */
  public ModifyRequestProtocolOp(ByteString dn)
  {
    this.dn            = dn;
    this.modifications = new ArrayList<RawModification>();
  }



  /**
   * Creates a new LDAP modify request protocol op with the specified DN and set
   * of modifications.
   *
   * @param  dn             The DN for this modify request.
   * @param  modifications  The set of modifications for this modify request.
   */
  public ModifyRequestProtocolOp(ByteString dn,
                                 ArrayList<RawModification> modifications)
  {
    this.dn = dn;

    if (modifications == null)
    {
      this.modifications = new ArrayList<RawModification>();
    }
    else
    {
      this.modifications = modifications;
    }
  }



  /**
   * Retrieves the DN for this modify request.
   *
   * @return  The DN for this modify request.
   */
  public ByteString getDN()
  {
    return dn;
  }



  /**
   * Retrieves the set of modifications for this modify request.  The returned
   * list may be altered by the caller.
   *
   * @return  The set of modifications for this modify request.
   */
  public ArrayList<RawModification> getModifications()
  {
    return modifications;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_MODIFY_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Modify Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_MODIFY_REQUEST);
    stream.writeOctetString(dn);

    stream.writeStartSequence();
    for(RawModification mod : modifications)
    {
      mod.write(stream);
    }
    stream.writeEndSequence();

    stream.writeEndSequence();
  }



  /**
   * Appends a string representation of this LDAP protocol op to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ModifyRequest(dn=");
    buffer.append(dn.toString());
    buffer.append(", mods={");

    if (! modifications.isEmpty())
    {
      Iterator<RawModification> iterator = modifications.iterator();
      iterator.next().toString(buffer);

      while (iterator.hasNext())
      {
        buffer.append(", ");
        iterator.next().toString(buffer);
      }
    }

    buffer.append("})");
  }



  /**
   * Appends a multi-line string representation of this LDAP protocol op to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   * @param  indent  The number of spaces from the margin that the lines should
   *                 be indented.
   */
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf);
    buffer.append("Modify Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  DN:  ");
    buffer.append(dn.toString());
    buffer.append(EOL);

    buffer.append("  Modifications:");
    buffer.append(EOL);

    for (RawModification mod : modifications)
    {
      mod.toString(buffer, indent+4);
    }
  }
}

