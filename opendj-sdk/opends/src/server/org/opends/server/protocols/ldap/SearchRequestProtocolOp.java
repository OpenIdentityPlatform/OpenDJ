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


import java.util.Iterator;
import java.util.LinkedHashSet;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.types.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines the structures and methods for an LDAP search request
 * protocol op, which is used to locate entries based on a set of criteria.
 */
public class SearchRequestProtocolOp
       extends ProtocolOp
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The typesOnly flag for this search request.
  private boolean typesOnly;

  // The alias dereferencing policy for this search request.
  private DereferencePolicy dereferencePolicy;

  // The base DN for this search request.
  private ByteString baseDN;

  // The size limit for this search request.
  private int sizeLimit;

  // The time limit for this search request.
  private int timeLimit;

  // The filter for this search request.
  private RawFilter filter;

  // The set of requested attributes for this search request.
  private LinkedHashSet<String> attributes;

  // The scope for this search request.
  private SearchScope scope;



  /**
   * Creates a new search request protocol op with the provided information.
   *
   * @param  baseDN             The base DN for this search request.
   * @param  scope              The scope for this search request.
   * @param  dereferencePolicy  The alias dereferencing policy for this search
   *                            request.
   * @param  sizeLimit          The size limit for this search request.
   * @param  timeLimit          The time limit for this search request.
   * @param  typesOnly          The typesOnly flag for this search request.
   * @param  filter             The filter for this search request.
   * @param  attributes         The set of requested attributes for this search
   *                            request.
   */
  public SearchRequestProtocolOp(ByteString baseDN, SearchScope scope,
                                 DereferencePolicy dereferencePolicy,
                                 int sizeLimit, int timeLimit,
                                 boolean typesOnly, RawFilter filter,
                                 LinkedHashSet<String> attributes)
  {
    this.baseDN            = baseDN;
    this.scope             = scope;
    this.dereferencePolicy = dereferencePolicy;
    this.sizeLimit         = sizeLimit;
    this.timeLimit         = timeLimit;
    this.typesOnly         = typesOnly;
    this.filter            = filter;

    if (attributes == null)
    {
      this.attributes = new LinkedHashSet<String>(0);
    }
    else
    {
      this.attributes = attributes;
    }
  }



  /**
   * Retrieves the base DN for this search request.
   *
   * @return  The base DN for this search request.
   */
  public ByteString getBaseDN()
  {
    return baseDN;
  }


  /**
   * Retrieves the scope for this search request.
   *
   * @return  The scope for this search request.
   */
  public SearchScope getScope()
  {
    return scope;
  }


  /**
   * Retrieves the alias dereferencing policy for this search request.
   *
   * @return  The alias dereferencing policy for this search request.
   */
  public DereferencePolicy getDereferencePolicy()
  {
    return dereferencePolicy;
  }



  /**
   * Retrieves the size limit for this search request.
   *
   * @return  The size limit for this search request.
   */
  public int getSizeLimit()
  {
    return sizeLimit;
  }



  /**
   * Retrieves the time limit for this search request.
   *
   * @return  The time limit for this search request.
   */
  public int getTimeLimit()
  {
    return timeLimit;
  }



  /**
   * Retrieves the value of the typesOnly flag for this search request.
   *
   * @return  The value of tye typesOnly flag for this search request.
   */
  public boolean getTypesOnly()
  {
    return typesOnly;
  }



  /**
   * Retrieves the filter for this search request.
   *
   * @return  The filter for this search request.
   */
  public RawFilter getFilter()
  {
    return filter;
  }



  /**
   * Retrieves the set of requested attributes for this search request.  The
   * returned list may be modified by the caller.
   *
   * @return  The set of requested attributes for this search request.
   */
  public LinkedHashSet<String> getAttributes()
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
    return OP_TYPE_SEARCH_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  public String getProtocolOpName()
  {
    return "Search Request";
  }

  /**
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
    stream.writeStartSequence(OP_TYPE_SEARCH_REQUEST);
    stream.writeOctetString(baseDN);
    stream.writeEnumerated(scope.intValue());
    stream.writeEnumerated(dereferencePolicy.intValue());
    stream.writeInteger(sizeLimit);
    stream.writeInteger(timeLimit);
    stream.writeBoolean(typesOnly);
    filter.write(stream);

    stream.writeStartSequence();
    for(String attribute : attributes)
    {
      stream.writeOctetString(attribute);
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
    buffer.append("SearchRequest(baseDN=");
    buffer.append(baseDN.toString());
    buffer.append(", scope=");
    buffer.append(String.valueOf(scope));
    buffer.append(", derefPolicy=");
    buffer.append(String.valueOf(dereferencePolicy));
    buffer.append(", sizeLimit=");
    buffer.append(sizeLimit);
    buffer.append(", timeLimit=");
    buffer.append(timeLimit);
    buffer.append(", typesOnly=");
    buffer.append(typesOnly);
    buffer.append(", filter=");
    filter.toString(buffer);
    buffer.append(", attributes={");

    if ((attributes != null) && (! attributes.isEmpty()))
    {
      Iterator<String> iterator = attributes.iterator();
      buffer.append(iterator.next());

      while (iterator.hasNext())
      {
        buffer.append(", ");
        buffer.append(iterator.next());
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
    buffer.append("Search Request");
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Base DN:  ");
    buffer.append(baseDN.toString());
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Scope:  ");
    buffer.append(String.valueOf(scope));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Dereference Policy:  ");
    buffer.append(String.valueOf(dereferencePolicy));
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Size Limit:  ");
    buffer.append(sizeLimit);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Time Limit:  ");
    buffer.append(timeLimit);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Types Only:  ");
    buffer.append(typesOnly);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Filter:  ");
    filter.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Attributes:");
    buffer.append(EOL);

    if (attributes != null)
    {
      for (String attribute : attributes)
      {
        buffer.append(indentBuf);
        buffer.append("    ");
        buffer.append(attribute);
        buffer.append(EOL);
      }
    }
  }
}

