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
import java.util.List;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.ByteString;
import org.opends.server.util.Base64;


import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the structures and methods for an LDAP add request
 * protocol op, which is used to add a new entry to the Directory Server.
 */
public class AddRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of attributes for this add request.
  private List<RawAttribute> attributes;

  // The DN for this add request.
  private ByteString dn;



  /**
   * Creates a new LDAP add request protocol op with the specified DN and no
   * attributes.
   *
   * @param  dn  The DN for this add request.
   */
  public AddRequestProtocolOp(ByteString dn)
  {
    this.dn         = dn;
    this.attributes = new ArrayList<RawAttribute>();
  }



  /**
   * Creates a new LDAP add request protocol op with the specified DN and set of
   * attributes.
   *
   * @param  dn          The DN for this add request.
   * @param  attributes  The set of attributes for this add request.
   */
  public AddRequestProtocolOp(ByteString dn,
                              ArrayList<RawAttribute> attributes)
  {
    this.dn = dn;

    if (attributes == null)
    {
      this.attributes = new ArrayList<RawAttribute>();
    }
    else
    {
      this.attributes = attributes;
    }
  }



  /**
   * Retrieves the DN for this add request.
   *
   * @return  The DN for this add request.
   */
  public ByteString getDN()
  {
    return dn;
  }


  /**
   * Retrieves the set of attributes for this add request.  The returned list
   * may be altered by the caller.
   *
   * @return  The set of attributes for this add request.
   */
  public List<RawAttribute> getAttributes()
  {
    return attributes;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  public byte getType()
  {
    return OP_TYPE_ADD_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Add Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_ADD_REQUEST);
    stream.writeOctetString(dn);

    // Write the attributes
    stream.writeStartSequence();
    for(RawAttribute attr : attributes)
    {
      attr.write(stream);
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
    buffer.append("AddRequest(dn=");
    buffer.append(dn.toString());
    buffer.append(", attrs={");

    if (! attributes.isEmpty())
    {
      Iterator<RawAttribute> iterator = attributes.iterator();
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
    buffer.append("Add Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  DN:  ");
    buffer.append(dn.toString());
    buffer.append(EOL);

    buffer.append("  Attributes:");
    buffer.append(EOL);

    for (RawAttribute attribute : attributes)
    {
      attribute.toString(buffer, indent+4);
    }
  }



  /**
   * Appends an LDIF representation of the entry to the provided buffer.
   *
   * @param  buffer      The buffer to which the entry should be appended.
   * @param  wrapColumn  The column at which long lines should be wrapped.
   */
  public void toLDIF(StringBuilder buffer, int wrapColumn)
  {
    // Add the DN to the buffer.
    String dnString;
    int    colsRemaining;
    if (needsBase64Encoding(dn))
    {
      dnString = Base64.encode(dn);
      buffer.append("dn:: ");

      colsRemaining = wrapColumn - 5;
    }
    else
    {
      dnString = dn.toString();
      buffer.append("dn: ");

      colsRemaining = wrapColumn - 4;
    }

    int dnLength = dnString.length();
    if ((dnLength <= colsRemaining) || (colsRemaining <= 0))
    {
      buffer.append(dnString);
      buffer.append(EOL);
    }
    else
    {
      buffer.append(dnString.substring(0, colsRemaining));
      buffer.append(EOL);

      int startPos = colsRemaining;
      while ((dnLength - startPos) > (wrapColumn - 1))
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos, (startPos+wrapColumn-1)));
        buffer.append(EOL);

        startPos += (wrapColumn-1);
      }

      if (startPos < dnLength)
      {
        buffer.append(" ");
        buffer.append(dnString.substring(startPos));
        buffer.append(EOL);
      }
    }


    // Add the attributes to the buffer.
    for (RawAttribute a : attributes)
    {
      String name       = a.getAttributeType();
      int    nameLength = name.length();

      for (ByteString v : a.getValues())
      {
        String valueString;
        if (needsBase64Encoding(v))
        {
          valueString = Base64.encode(v);
          buffer.append(name);
          buffer.append(":: ");

          colsRemaining = wrapColumn - nameLength - 3;
        }
        else
        {
          valueString = v.toString();
          buffer.append(name);
          buffer.append(": ");

          colsRemaining = wrapColumn - nameLength - 2;
        }

        int valueLength = valueString.length();
        if ((valueLength <= colsRemaining) || (colsRemaining <= 0))
        {
          buffer.append(valueString);
          buffer.append(EOL);
        }
        else
        {
          buffer.append(valueString.substring(0, colsRemaining));
          buffer.append(EOL);

          int startPos = colsRemaining;
          while ((valueLength - startPos) > (wrapColumn - 1))
          {
            buffer.append(" ");
            buffer.append(valueString.substring(startPos,
                                                (startPos+wrapColumn-1)));
            buffer.append(EOL);

            startPos += (wrapColumn-1);
          }

          if (startPos < valueLength)
          {
            buffer.append(" ");
            buffer.append(valueString.substring(startPos));
            buffer.append(EOL);
          }
        }
      }
    }


    // Make sure to add an extra blank line to ensure that there will be one
    // between this entry and the next.
    buffer.append(EOL);
  }
}

