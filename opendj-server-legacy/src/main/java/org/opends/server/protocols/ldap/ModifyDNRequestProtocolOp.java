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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.ldap;


import java.io.IOException;

import org.forgerock.opendj.io.*;
import org.forgerock.opendj.ldap.ByteString;

import org.forgerock.i18n.slf4j.LocalizedLogger;
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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The current entry DN for this modify DN request. */
  private ByteString entryDN;

  /** The new RDN for this modify DN request. */
  private ByteString newRDN;

  /** The new superior DN for this modify DN request. */
  private ByteString newSuperior;

  /** Indicates whether to delete the current RDN value(s). */
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
  @Override
  public byte getType()
  {
    return OP_TYPE_MODIFY_DN_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
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
  @Override
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
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ModifyDNRequest(dn=").append(entryDN);
    buffer.append(", newRDN=").append(newRDN);
    buffer.append(", deleteOldRDN=").append(deleteOldRDN);

    if (newSuperior != null)
    {
      buffer.append(", newSuperior=").append(newSuperior);
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
  @Override
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
    buffer.append(entryDN);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  New RDN:  ");
    buffer.append(newRDN);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Delete Old RDN:  ");
    buffer.append(deleteOldRDN);
    buffer.append(EOL);

    if (newSuperior != null)
    {
      buffer.append(indentBuf);
      buffer.append("  New Superior:  ");
      buffer.append(newSuperior);
      buffer.append(EOL);
    }
  }
}

