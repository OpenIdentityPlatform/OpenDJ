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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.protocols.ldap;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.opends.server.types.RawFilter;
import org.forgerock.opendj.ldap.SearchScope;

import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines the structures and methods for an LDAP search request
 * protocol op, which is used to locate entries based on a set of criteria.
 */
public class SearchRequestProtocolOp
       extends ProtocolOp
{

  /** The typesOnly flag for this search request. */
  private boolean typesOnly;

  /** The alias dereferencing policy for this search request. */
  private DereferenceAliasesPolicy dereferencePolicy;

  /** The base DN for this search request. */
  private ByteString baseDN;

  /** The size limit for this search request. */
  private int sizeLimit;

  /** The time limit for this search request. */
  private int timeLimit;

  /** The filter for this search request. */
  private RawFilter filter;

  /** The set of requested attributes for this search request. */
  private Set<String> attributes;

  /** The scope for this search request. */
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
                                 DereferenceAliasesPolicy dereferencePolicy,
                                 int sizeLimit, int timeLimit,
                                 boolean typesOnly, RawFilter filter,
                                 Set<String> attributes)
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
      this.attributes = new LinkedHashSet<>(0);
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
  public DereferenceAliasesPolicy getDereferencePolicy()
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
  public Set<String> getAttributes()
  {
    return attributes;
  }



  /**
   * Retrieves the BER type for this protocol op.
   *
   * @return  The BER type for this protocol op.
   */
  @Override
  public byte getType()
  {
    return OP_TYPE_SEARCH_REQUEST;
  }



  /**
   * Retrieves the name for this protocol op type.
   *
   * @return  The name for this protocol op type.
   */
  @Override
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
  @Override
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
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SearchRequest(baseDN=").append(baseDN);
    buffer.append(", scope=").append(scope);
    buffer.append(", derefPolicy=").append(dereferencePolicy);
    buffer.append(", sizeLimit=").append(sizeLimit);
    buffer.append(", timeLimit=").append(timeLimit);
    buffer.append(", typesOnly=").append(typesOnly);
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
  @Override
  public void toString(StringBuilder buffer, int indent)
  {
    StringBuilder indentBuf = new StringBuilder(indent);
    for (int i=0 ; i < indent; i++)
    {
      indentBuf.append(' ');
    }

    buffer.append(indentBuf).append("Search Request").append(EOL);
    buffer.append(indentBuf).append("  Base DN:  ").append(baseDN).append(EOL);
    buffer.append(indentBuf).append("  Scope:  ").append(scope).append(EOL);
    buffer.append(indentBuf).append("  Dereference Policy:  ").append(dereferencePolicy).append(EOL);
    buffer.append(indentBuf).append("  Size Limit:  ").append(sizeLimit).append(EOL);
    buffer.append(indentBuf).append("  Time Limit:  ").append(timeLimit).append(EOL);
    buffer.append(indentBuf).append("  Types Only:  ").append(typesOnly).append(EOL);

    buffer.append(indentBuf);
    buffer.append("  Filter:  ");
    filter.toString(buffer);
    buffer.append(EOL);

    buffer.append(indentBuf).append("  Attributes:").append(EOL);

    if (attributes != null)
    {
      for (String attribute : attributes)
      {
        buffer.append(indentBuf).append("    ").append(attribute).append(EOL);
      }
    }
  }
}

