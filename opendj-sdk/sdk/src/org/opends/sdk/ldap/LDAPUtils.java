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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldap;



import static org.opends.sdk.ldap.LDAPConstants.*;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.opends.sdk.ByteSequence;
import org.opends.sdk.Filter;
import org.opends.sdk.FilterVisitor;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.responses.SearchResultEntry;
import org.opends.sdk.schema.Schema;



/**
 * Common LDAP utility methods which may be used when implementing new
 * controls and extension.
 */
public final class LDAPUtils
{

  private static final FilterVisitor<IOException, ASN1Writer> ASN1_ENCODER = new FilterVisitor<IOException, ASN1Writer>()
  {

    public IOException visitAndFilter(ASN1Writer writer,
        List<Filter> subFilters)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_AND);
        for (Filter subFilter : subFilters)
        {
          IOException e = subFilter.accept(this, writer);
          if (e != null)
          {
            return e;
          }
        }
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitApproxMatchFilter(ASN1Writer writer,
        String attributeDescription, ByteSequence assertionValue)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_APPROXIMATE);
        writer.writeOctetString(attributeDescription);
        writer.writeOctetString(assertionValue);
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitEqualityMatchFilter(ASN1Writer writer,
        String attributeDescription, ByteSequence assertionValue)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_EQUALITY);
        writer.writeOctetString(attributeDescription);
        writer.writeOctetString(assertionValue);
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitExtensibleMatchFilter(ASN1Writer writer,
        String matchingRule, String attributeDescription,
        ByteSequence assertionValue, boolean dnAttributes)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_EXTENSIBLE_MATCH);

        if (matchingRule != null)
        {
          writer.writeOctetString(TYPE_MATCHING_RULE_ID, matchingRule);
        }

        if (attributeDescription != null)
        {
          writer.writeOctetString(TYPE_MATCHING_RULE_TYPE,
              attributeDescription);
        }

        writer.writeOctetString(TYPE_MATCHING_RULE_VALUE,
            assertionValue);

        if (dnAttributes)
        {
          writer.writeBoolean(TYPE_MATCHING_RULE_DN_ATTRIBUTES, true);
        }

        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitGreaterOrEqualFilter(ASN1Writer writer,
        String attributeDescription, ByteSequence assertionValue)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_GREATER_OR_EQUAL);
        writer.writeOctetString(attributeDescription);
        writer.writeOctetString(assertionValue);
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitLessOrEqualFilter(ASN1Writer writer,
        String attributeDescription, ByteSequence assertionValue)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_LESS_OR_EQUAL);
        writer.writeOctetString(attributeDescription);
        writer.writeOctetString(assertionValue);
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitNotFilter(ASN1Writer writer,
        Filter subFilter)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_NOT);
        IOException e = subFilter.accept(this, writer);
        if (e != null)
        {
          return e;
        }
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitOrFilter(ASN1Writer writer,
        List<Filter> subFilters)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_OR);
        for (Filter subFilter : subFilters)
        {
          IOException e = subFilter.accept(this, writer);
          if (e != null)
          {
            return e;
          }
        }
        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitPresentFilter(ASN1Writer writer,
        String attributeDescription)
    {
      try
      {
        writer.writeOctetString(TYPE_FILTER_PRESENCE,
            attributeDescription);
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitSubstringsFilter(ASN1Writer writer,
        String attributeDescription, ByteSequence initialSubstring,
        List<ByteSequence> anySubstrings, ByteSequence finalSubstring)
    {
      try
      {
        writer.writeStartSequence(TYPE_FILTER_SUBSTRING);
        writer.writeOctetString(attributeDescription);

        writer.writeStartSequence();
        if (initialSubstring != null)
        {
          writer.writeOctetString(TYPE_SUBINITIAL, initialSubstring);
        }

        for (ByteSequence anySubstring : anySubstrings)
        {
          writer.writeOctetString(TYPE_SUBANY, anySubstring);
        }

        if (finalSubstring != null)
        {
          writer.writeOctetString(TYPE_SUBFINAL, finalSubstring);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }



    public IOException visitUnrecognizedFilter(ASN1Writer writer,
        byte filterTag, ByteSequence filterBytes)
    {
      try
      {
        writer.writeOctetString(filterTag, filterBytes);
        return null;
      }
      catch (IOException e)
      {
        return e;
      }
    }
  };



  /**
   * Reads the next ASN.1 element from the provided {@code ASN1Reader}
   * as a {@code Filter}.
   *
   * @param reader
   *          The {@code ASN1Reader} from which the ASN.1 encoded
   *          {@code Filter} should be read.
   * @return The decoded {@code Filter}.
   * @throws IOException
   *           If an error occurs while reading from {@code reader}.
   */
  public static Filter decodeFilter(ASN1Reader reader)
      throws IOException
  {
    byte type = reader.peekType();

    switch (type)
    {
    case TYPE_FILTER_AND:
      return decodeAndFilter(reader);

    case TYPE_FILTER_OR:
      return decodeOrFilter(reader);

    case TYPE_FILTER_NOT:
      return decodeNotFilter(reader);

    case TYPE_FILTER_EQUALITY:
      return decodeEqualityMatchFilter(reader);

    case TYPE_FILTER_GREATER_OR_EQUAL:
      return decodeGreaterOrEqualMatchFilter(reader);

    case TYPE_FILTER_LESS_OR_EQUAL:
      return decodeLessOrEqualMatchFilter(reader);

    case TYPE_FILTER_APPROXIMATE:
      return decodeApproxMatchFilter(reader);

    case TYPE_FILTER_SUBSTRING:
      return decodeSubstringsFilter(reader);

    case TYPE_FILTER_PRESENCE:
      return Filter.newPresentFilter(reader
          .readOctetStringAsString(type));

    case TYPE_FILTER_EXTENSIBLE_MATCH:
      return decodeExtensibleMatchFilter(reader);

    default:
      return Filter.newUnrecognizedFilter(type, reader
          .readOctetString(type));
    }
  }



  /**
   * Reads the next ASN.1 element from the provided {@code ASN1Reader}
   * as a {@code SearchResultEntry}.
   *
   * @param reader
   *          The {@code ASN1Reader} from which the ASN.1 encoded
   *          {@code SearchResultEntry} should be read.
   * @param schema
   *          The schema to use when decoding the entry.
   * @return The decoded {@code SearchResultEntry}.
   * @throws IOException
   *           If an error occurs while reading from {@code reader}.
   */
  public static SearchResultEntry decodeSearchResultEntry(
      ASN1Reader reader, Schema schema) throws IOException
  {
    return LDAPDecoder.decodeEntry(reader, schema);
  }



  /**
   * Writes the ASN.1 encoding of the provided {@code Filter} to the
   * provided {@code ASN1Writer}.
   *
   * @param writer
   *          The {@code ASN1Writer} to which the ASN.1 encoding of the
   *          provided {@code Filter} should be written.
   * @param filter
   *          The filter to be encoded.
   * @return The updated {@code ASN1Writer}.
   * @throws IOException
   *           If an error occurs while writing to {@code writer}.
   */
  public static ASN1Writer encodeFilter(ASN1Writer writer, Filter filter)
      throws IOException
  {
    IOException e = filter.accept(ASN1_ENCODER, writer);
    if (e != null)
    {
      throw e;
    }
    else
    {
      return writer;
    }
  }



  /**
   * Writes the ASN.1 encoding of the provided {@code SearchResultEntry}
   * to the provided {@code ASN1Writer}.
   *
   * @param writer
   *          The {@code ASN1Writer} to which the ASN.1 encoding of the
   *          provided {@code SearchResultEntry} should be written.
   * @param entry
   *          The Search Result Entry to be encoded.
   * @return The updated {@code ASN1Writer}.
   * @throws IOException
   *           If an error occurs while writing to {@code writer}.
   */
  public static ASN1Writer encodeSearchResultEntry(ASN1Writer writer,
      SearchResultEntry entry) throws IOException
  {
    LDAPEncoder.encodeEntry(writer, entry);
    return writer;
  }



  // Decodes an and filter.
  private static Filter decodeAndFilter(ASN1Reader reader)
      throws IOException
  {
    Filter filter;

    reader.readStartSequence(TYPE_FILTER_AND);
    try
    {
      if (reader.hasNextElement())
      {
        List<Filter> subFilters = new LinkedList<Filter>();
        do
        {
          subFilters.add(decodeFilter(reader));
        } while (reader.hasNextElement());
        filter = Filter.newAndFilter(subFilters);
      }
      else
      {
        // No sub-filters - this is an RFC 4526 absolute true filter.
        filter = Filter.getAbsoluteTrueFilter();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    return filter;
  }



  // Decodes an approximate match filter.
  private static Filter decodeApproxMatchFilter(ASN1Reader reader)
      throws IOException
  {
    String attributeDescription;
    ByteSequence assertionValue;

    reader.readStartSequence(TYPE_FILTER_APPROXIMATE);
    try
    {
      attributeDescription = reader.readOctetStringAsString();
      assertionValue = reader.readOctetString();
    }
    finally
    {
      reader.readEndSequence();
    }

    return Filter.newApproxMatchFilter(attributeDescription,
        assertionValue);
  }



  // Decodes an equality match filter.
  private static Filter decodeEqualityMatchFilter(ASN1Reader reader)
      throws IOException
  {
    String attributeDescription;
    ByteSequence assertionValue;

    reader.readStartSequence(TYPE_FILTER_EQUALITY);
    try
    {
      attributeDescription = reader.readOctetStringAsString();
      assertionValue = reader.readOctetString();
    }
    finally
    {
      reader.readEndSequence();
    }

    return Filter.newEqualityMatchFilter(attributeDescription,
        assertionValue);
  }



  // Decodes an extensible match filter.
  private static Filter decodeExtensibleMatchFilter(ASN1Reader reader)
      throws IOException
  {
    String matchingRule;
    String attributeDescription;
    boolean dnAttributes;
    ByteSequence assertionValue;

    reader.readStartSequence(TYPE_FILTER_EXTENSIBLE_MATCH);
    try
    {
      matchingRule = null;
      if (reader.peekType() == TYPE_MATCHING_RULE_ID)
      {
        matchingRule = reader
            .readOctetStringAsString(TYPE_MATCHING_RULE_ID);
      }
      attributeDescription = null;
      if (reader.peekType() == TYPE_MATCHING_RULE_TYPE)
      {
        attributeDescription = reader
            .readOctetStringAsString(TYPE_MATCHING_RULE_TYPE);
      }
      dnAttributes = false;
      if (reader.hasNextElement()
          && (reader.peekType() == TYPE_MATCHING_RULE_DN_ATTRIBUTES))
      {
        dnAttributes = reader.readBoolean();
      }
      assertionValue = reader.readOctetString(TYPE_MATCHING_RULE_VALUE);
    }
    finally
    {
      reader.readEndSequence();
    }

    return Filter.newExtensibleMatchFilter(matchingRule,
        attributeDescription, assertionValue, dnAttributes);
  }



  // Decodes a greater than or equal filter.
  private static Filter decodeGreaterOrEqualMatchFilter(
      ASN1Reader reader) throws IOException
  {
    String attributeDescription;
    ByteSequence assertionValue;

    reader.readStartSequence(TYPE_FILTER_GREATER_OR_EQUAL);
    try
    {
      attributeDescription = reader.readOctetStringAsString();
      assertionValue = reader.readOctetString();
    }
    finally
    {
      reader.readEndSequence();
    }
    return Filter.newGreaterOrEqualFilter(attributeDescription,
        assertionValue);
  }



  // Decodes a less than or equal filter.
  private static Filter decodeLessOrEqualMatchFilter(ASN1Reader reader)
      throws IOException
  {
    String attributeDescription;
    ByteSequence assertionValue;

    reader.readStartSequence(TYPE_FILTER_LESS_OR_EQUAL);
    try
    {
      attributeDescription = reader.readOctetStringAsString();
      assertionValue = reader.readOctetString();
    }
    finally
    {
      reader.readEndSequence();
    }

    return Filter.newLessOrEqualFilter(attributeDescription,
        assertionValue);
  }



  // Decodes a not filter.
  private static Filter decodeNotFilter(ASN1Reader reader)
      throws IOException
  {
    Filter subFilter;

    reader.readStartSequence(TYPE_FILTER_NOT);
    try
    {
      subFilter = decodeFilter(reader);
    }
    finally
    {
      reader.readEndSequence();
    }

    return Filter.newNotFilter(subFilter);
  }



  // Decodes an or filter.
  private static Filter decodeOrFilter(ASN1Reader reader)
      throws IOException
  {
    Filter filter;

    reader.readStartSequence(TYPE_FILTER_OR);
    try
    {
      if (reader.hasNextElement())
      {
        List<Filter> subFilters = new LinkedList<Filter>();
        do
        {
          subFilters.add(decodeFilter(reader));
        } while (reader.hasNextElement());
        filter = Filter.newOrFilter(subFilters);
      }
      else
      {
        // No sub-filters - this is an RFC 4526 absolute false filter.
        filter = Filter.getAbsoluteFalseFilter();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    return filter;
  }



  // Decodes a sub-strings filter.
  private static Filter decodeSubstringsFilter(ASN1Reader reader)
      throws IOException
  {
    ByteSequence initialSubstring = null;
    List<ByteSequence> anySubstrings = null;
    ByteSequence finalSubstring = null;
    String attributeDescription;

    reader.readStartSequence(TYPE_FILTER_SUBSTRING);
    try
    {
      attributeDescription = reader.readOctetStringAsString();
      reader.readStartSequence();
      try
      {
        // FIXME: There should be at least one element in this substring
        // filter sequence.
        if (reader.peekType() == TYPE_SUBINITIAL)
        {
          initialSubstring = reader.readOctetString(TYPE_SUBINITIAL);
        }
        if (reader.hasNextElement()
            && (reader.peekType() == TYPE_SUBANY))
        {
          anySubstrings = new LinkedList<ByteSequence>();
          do
          {
            anySubstrings.add(reader.readOctetString(TYPE_SUBANY));
          } while (reader.hasNextElement()
              && (reader.peekType() == TYPE_SUBANY));
        }
        if (reader.hasNextElement()
            && (reader.peekType() == TYPE_SUBFINAL))
        {
          finalSubstring = reader.readOctetString(TYPE_SUBFINAL);
        }
      }
      finally
      {
        reader.readEndSequence();
      }
    }
    finally
    {
      reader.readEndSequence();
    }

    if (anySubstrings == null)
    {
      anySubstrings = Collections.emptyList();
    }

    return Filter.newSubstringsFilter(attributeDescription,
        initialSubstring, anySubstrings, finalSubstring);
  }



  /**
   * Prevent instantiation.
   */
  private LDAPUtils()
  {
    // Nothing to do.
  }
}
