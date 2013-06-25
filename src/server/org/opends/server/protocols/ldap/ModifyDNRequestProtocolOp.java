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


import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.ByteString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;


/**
 * This class defines the structures and methods for an LDAP modify DN request
 * protocol op, which is used to move or rename an entry or subtree within the
 * Directory Server.
 */
public class ModifyDNRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The current entry DN for this modify DN request.
  private ByteString entryDN;

  // The new RDN for this modify DN request.
  private ByteString newRDN;

  // The new superior DN for this modify DN request.
  private ByteString newSuperior;

  // Indicates whether to delete the current RDN value(s).
  private boolean deleteOldRDN;



  /**
   * Creates a new modify DN request protocol op with the provided information.
   *
   * @param  entryDN       The current entry DN for this modify DN request.
   * @param  newRDN        The new RDN for this modify DN request.
   * @param  deleteOldRDN  Indicates whether to delete the current RDN value(s).
   */
  public ModifyDNRequestProtocolOp(ByteString entryDN,
                                   ByteString newRDN, boolean deleteOldRDN)
  {
    this.entryDN      = entryDN;
    this.newRDN       = newRDN;
    this.deleteOldRDN = deleteOldRDN;
    this.newSuperior  = null;
  }



  /**
   * Creates a new modify DN request protocol op with the provided information.
   *
   * @param  entryDN       The current entry DN for this modify DN request.
   * @param  newRDN        The new RDN for this modify DN request.
   * @param  deleteOldRDN  Indicates whether to delete the current RDN value(s).
   * @param  newSuperior   The new superior DN for this modify DN request.
   */
  public ModifyDNRequestProtocolOp(ByteString entryDN,
                                   ByteString newRDN, boolean deleteOldRDN,
                                   ByteString newSuperior)
  {
    this.entryDN      = entryDN;
    this.newRDN       = newRDN;
    this.deleteOldRDN = deleteOldRDN;
    this.newSuperior  = newSuperior;
  }



  /**
   * Retrieves the current entry DN for this modify DN request.
   *
   * @return  The current entry DN for this modify DN request.
   */
  public ByteString getEntryDN()
  {
    return entryDN;
  }



  /**
   * Retrieves the new RDN for this modify DN request.
   *
   * @return  The new RDN for this modify DN request.
   */
  public ByteString getNewRDN()
  {
    return newRDN;
  }



  /**
   * Indicates whether the current RDN value(s) should be deleted.
   *
   * @return  <CODE>true</CODE> if the current RDN value(s) should be deleted,
   *          or <CODE>false</CODE> if not.
   */
  public boolean deleteOldRDN()
  {
    return deleteOldRDN;
  }



  /**
   * Retrieves the new superior DN for this modify DN request.
   *
   * @return  The new superior DN for this modify DN request, or
   *          <CODE>null</CODE> if none was provided.
   */
  public ByteString getNewSuperior()
  {
    return newSuperior;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_MODIFY_DN_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Modify DN Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_MODIFY_DN_REQUEST);
    stream.writeOctetString(entryDN);
    stream.writeOctetString(newRDN);
    stream.writeBoolean(deleteOldRDN);

    if(newSuperior != null)
    {
      stream.writeOctetString(TYPE_MODIFY_DN_NEW_SUPERIOR, newSuperior);
    }

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
    buffer.append("ModifyDNRequest(dn=");
    buffer.append(entryDN.toString());
    buffer.append(", newRDN=");
    buffer.append(newRDN.toString());
    buffer.append(", deleteOldRDN=");
    buffer.append(deleteOldRDN);

    if (newSuperior != null)
    {
      buffer.append(", newSuperior=");
      buffer.append(newSuperior.toString());
    }

    buffer.append(")");
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
    buffer.append("Modify DN Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Entry DN:  ");
    buffer.append(entryDN.toString());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  New RDN:  ");
    buffer.append(newRDN.toString());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Delete Old RDN:  ");
    buffer.append(deleteOldRDN);
    buffer.append(EOL);

    if (newSuperior != null)
    {
      buffer.append(indentBuf);
      buffer.append("  New Superior:  ");
      buffer.append(newSuperior.toString());
      buffer.append(EOL);
    }
  }
}

