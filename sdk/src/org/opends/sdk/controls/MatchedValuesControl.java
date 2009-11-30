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

package org.opends.sdk.controls;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.sdk.util.StaticUtils.getExceptionMessage;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.opends.messages.Message;
import org.opends.sdk.AbstractFilterVisitor;
import org.opends.sdk.DecodeException;
import org.opends.sdk.Filter;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.ldap.LDAPUtils;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.*;



/**
 * This class implements the matched values control as defined in RFC
 * 3876. It may be included in a search request to indicate that only
 * attribute values matching one or more filters contained in the
 * matched values control should be returned to the client.
 */
public class MatchedValuesControl extends Control
{
  /**
   * The OID for the matched values control used to specify which
   * particular attribute values should be returned in a search result
   * entry.
   */
  public static final String OID_MATCHED_VALUES = "1.2.826.0.1.3344810.2.3";



  /**
   * Visitor for validating matched values filters.
   */
  private static final class FilterValidator extends
      AbstractFilterVisitor<LocalizedIllegalArgumentException, Filter>
  {

    @Override
    public LocalizedIllegalArgumentException visitAndFilter(Filter p,
        List<Filter> subFilters)
    {
      Message message = ERR_MVFILTER_BAD_FILTER_AND.get(p.toString());
      return new LocalizedIllegalArgumentException(message);
    }



    @Override
    public LocalizedIllegalArgumentException visitExtensibleMatchFilter(
        Filter p, String matchingRule, String attributeDescription,
        ByteSequence assertionValue, boolean dnAttributes)
    {
      if (dnAttributes)
      {
        Message message = ERR_MVFILTER_BAD_FILTER_EXT.get(p.toString());
        return new LocalizedIllegalArgumentException(message);
      }
      else
      {
        return null;
      }
    }



    @Override
    public LocalizedIllegalArgumentException visitNotFilter(Filter p,
        Filter subFilter)
    {
      Message message = ERR_MVFILTER_BAD_FILTER_NOT.get(p.toString());
      return new LocalizedIllegalArgumentException(message);
    }



    @Override
    public LocalizedIllegalArgumentException visitOrFilter(Filter p,
        List<Filter> subFilters)
    {
      Message message = ERR_MVFILTER_BAD_FILTER_OR.get(p.toString());
      return new LocalizedIllegalArgumentException(message);
    }



    @Override
    public LocalizedIllegalArgumentException visitUnrecognizedFilter(
        Filter p, byte filterTag, ByteSequence filterBytes)
    {
      Message message = ERR_MVFILTER_BAD_FILTER_UNRECOGNIZED.get(p
          .toString(), filterTag);
      return new LocalizedIllegalArgumentException(message);
    }
  }



  /**
   * Decodes a matched values control from a byte string.
   */
  private final static class Decoder implements
      ControlDecoder<MatchedValuesControl>
  {
    /**
     * {@inheritDoc}
     */
    public MatchedValuesControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_MATCHEDVALUES_NO_CONTROL_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        if (!reader.hasNextElement())
        {
          Message message = ERR_MATCHEDVALUES_NO_FILTERS.get();
          throw DecodeException.error(message);
        }

        LinkedList<Filter> filters = new LinkedList<Filter>();
        do
        {
          Filter filter = LDAPUtils.decodeFilter(reader);

          try
          {
            validateFilter(filter);
          }
          catch (LocalizedIllegalArgumentException e)
          {
            throw DecodeException
                .error(e.getMessageObject());
          }

          filters.add(filter);
        } while (reader.hasNextElement());

        reader.readEndSequence();

        return new MatchedValuesControl(isCritical, Collections
            .unmodifiableList(filters));
      }
      catch (IOException e)
      {
        StaticUtils.DEBUG_LOG.throwing("MatchedValuesControl.Decoder",
            "decode", e);

        Message message = ERR_MATCHEDVALUES_CANNOT_DECODE_VALUE_AS_SEQUENCE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message);
      }
    }



    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_MATCHED_VALUES;
    }

  }



  /**
   * A control decoder which can be used to decode matched values
   * controls.
   */
  public static final ControlDecoder<MatchedValuesControl> DECODER = new Decoder();

  private static final FilterValidator FILTER_VALIDATOR = new FilterValidator();



  private static void validateFilter(final Filter filter)
      throws LocalizedIllegalArgumentException
  {
    LocalizedIllegalArgumentException e = filter.accept(
        FILTER_VALIDATOR, filter);
    if (e != null)
    {
      throw e;
    }
  }



  private List<Filter> filters;



  /**
   * Creates a new matched values control using the default OID and the
   * provided criticality and set of filters.
   * 
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical to the operation processing.
   * @param filters
   *          The list of matched value filters.
   * @throws LocalizedIllegalArgumentException
   *           If one of the filters is not permitted by the matched
   *           values control.
   */
  public MatchedValuesControl(boolean isCritical, Filter... filters)
      throws LocalizedIllegalArgumentException
  {
    super(OID_MATCHED_VALUES, isCritical);

    Validator.ensureNotNull((Object) filters);
    Validator.ensureTrue(filters.length > 0, "filters is empty");

    if (filters.length == 1)
    {
      validateFilter(filters[0]);
      this.filters = Collections.singletonList(filters[0]);
    }
    else
    {
      LinkedList<Filter> list = new LinkedList<Filter>();
      for (Filter filter : filters)
      {
        validateFilter(filter);
        list.add(filter);
      }
      this.filters = Collections.unmodifiableList(list);
    }
  }



  private MatchedValuesControl(boolean isCritical, List<Filter> filters)
  {
    super(OID_MATCHED_VALUES, isCritical);
    this.filters = filters;
  }



  /**
   * Returns an {@code Iterable} containing the list of filters
   * associated with this matched values control.
   * 
   * @return An {@code Iterable} containing the list of filters.
   */
  public Iterable<Filter> getFilters()
  {
    return filters;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString getValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);
    try
    {
      writer.writeStartSequence();
      for (Filter f : filters)
      {
        LDAPUtils.encodeFilter(writer, f);
      }
      writer.writeEndSequence();
      return buffer.toByteString();
    }
    catch (IOException ioe)
    {
      // This should never happen unless there is a bug somewhere.
      throw new RuntimeException(ioe);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasValue()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("MatchingValuesControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(")");
  }
}
