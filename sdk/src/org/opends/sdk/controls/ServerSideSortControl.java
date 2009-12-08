package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Validator;



/**
 * This class implements the server-side sort control as defined in RFC
 * 2891.
 */
public class ServerSideSortControl
{
  /**
   * The OID for the server-side sort request control.
   */
  public static final String OID_SERVER_SIDE_SORT_REQUEST_CONTROL = "1.2.840.113556.1.4.473";

  /**
   * The OID for the server-side sort response control.
   */
  public static final String OID_SERVER_SIDE_SORT_RESPONSE_CONTROL = "1.2.840.113556.1.4.474";



  /**
   * This class implements the server-side sort request control as
   * defined in RFC 2891 section 1.1. The ASN.1 description for the
   * control value is: <BR>
   * <BR>
   * 
   * <PRE>
   * SortKeyList ::= SEQUENCE OF SEQUENCE {
   *            attributeType   AttributeDescription,
   *            orderingRule    [0] MatchingRuleId OPTIONAL,
   *            reverseOrder    [1] BOOLEAN DEFAULT FALSE }
   * </PRE>
   */
  public static class Request extends Control
  {
    private final List<SortKey> sortKeys = new ArrayList<SortKey>();



    public Request(boolean isCritical, SortKey... sortKeys)
    {
      super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, isCritical);
      addSortKey(sortKeys);
    }



    public Request(boolean isCritical, String sortOrderString)
        throws DecodeException
    {
      super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, isCritical);

      decodeSortOrderString(sortOrderString, sortKeys);
    }



    public Request(SortKey... sortKeys)
    {
      this(false, sortKeys);
    }



    public Request(String sortOrderString) throws DecodeException
    {
      this(false, sortOrderString);
    }



    public Request addSortKey(SortKey... sortKeys)
    {
      if (sortKeys != null)
      {
        for (SortKey sortKey : sortKeys)
        {
          Validator.ensureNotNull(sortKey);
          this.sortKeys.add(sortKey);
        }
      }
      return this;
    }



    public Request addSortKey(String sortOrderString)
        throws DecodeException
    {
      decodeSortOrderString(sortOrderString, sortKeys);
      return this;
    }



    public Iterable<SortKey> getSortKeys()
    {
      return sortKeys;
    }



    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writer.writeStartSequence();
        for (SortKey sortKey : sortKeys)
        {
          writer.writeStartSequence();
          writer.writeOctetString(sortKey.getAttributeDescription());

          if (sortKey.getOrderingRule() != null)
          {
            writer.writeOctetString(TYPE_ORDERING_RULE_ID, sortKey
                .getOrderingRule());
          }

          if (!sortKey.isReverseOrder())
          {
            writer.writeBoolean(TYPE_REVERSE_ORDER, true);
          }

          writer.writeEndSequence();
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



    @Override
    public boolean hasValue()
    {
      return true;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("ServerSideSortRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", sortKeys=");
      buffer.append(sortKeys);
      buffer.append(")");
    }
  }



  /**
   * This class implements the server-side sort response control as
   * defined in RFC 2891 section 1.2. The ASN.1 description for the
   * control value is: <BR>
   * <BR>
   * 
   * <PRE>
   * SortResult ::= SEQUENCE {
   *    sortResult  ENUMERATED {
   *        success                   (0), -- results are sorted
   *        operationsError           (1), -- server internal failure
   *        timeLimitExceeded         (3), -- timelimit reached before
   *                                       -- sorting was completed
   *        strongAuthRequired        (8), -- refused to return sorted
   *                                       -- results via insecure
   *                                       -- protocol
   *        adminLimitExceeded       (11), -- too many matching entries
   *                                       -- for the server to sort
   *        noSuchAttribute          (16), -- unrecognized attribute
   *                                       -- type in sort key
   *        inappropriateMatching    (18), -- unrecognized or
   *                                       -- inappropriate matching
   *                                       -- rule in sort key
   *        insufficientAccessRights (50), -- refused to return sorted
   *                                       -- results to this client
   *        busy                     (51), -- too busy to process
   *        unwillingToPerform       (53), -- unable to sort
   *        other                    (80)
   *        },
   *  attributeType [0] AttributeDescription OPTIONAL }
   * </PRE>
   */
  public static class Response extends Control
  {
    private SortResult sortResult;

    private String attributeDescription;



    public Response(boolean isCritical, SortResult sortResult)
    {
      this(isCritical, sortResult, null);
    }



    public Response(boolean isCritical, SortResult sortResult,
        String attributeDescription)
    {
      super(OID_SERVER_SIDE_SORT_RESPONSE_CONTROL, isCritical);
      Validator.ensureNotNull(sortResult);
      this.sortResult = sortResult;
      this.attributeDescription = attributeDescription;
    }



    public Response(SortResult sortResult)
    {
      this(false, sortResult, null);
    }



    public Response(SortResult sortResult, String attributeDescription)
    {
      this(false, sortResult, attributeDescription);
    }



    public String getAttributeDescription()
    {
      return attributeDescription;
    }



    public SortResult getSortResult()
    {
      return sortResult;
    }



    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writer.writeStartSequence();
        writer.writeEnumerated(sortResult.intValue());
        if (attributeDescription != null)
        {
          writer.writeOctetString(TYPE_ATTRIBUTE_TYPE,
              attributeDescription);
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



    @Override
    public boolean hasValue()
    {
      return true;
    }



    public Response setAttributeDescription(String attributeDescription)
    {
      this.attributeDescription = attributeDescription;
      return this;
    }



    public Response setSortResult(SortResult sortResult)
    {
      Validator.ensureNotNull(sortResult);
      this.sortResult = sortResult;
      return this;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("ServerSideSortResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", sortResult=");
      buffer.append(sortResult);
      buffer.append(", attributeDescription=");
      buffer.append(attributeDescription);
      buffer.append(")");
    }
  }



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class RequestDecoder implements
      ControlDecoder<Request>
  {
    /**
     * {@inheritDoc}
     */
    public Request decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value == null)
      {
        LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        if (!reader.hasNextElement())
        {
          LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_SORT_KEYS.get();
          throw DecodeException.error(message);
        }

        Request request = new Request();
        while (reader.hasNextElement())
        {
          reader.readStartSequence();
          String attrName = reader.readOctetStringAsString();

          String orderingRule = null;
          boolean reverseOrder = false;
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_ORDERING_RULE_ID))
          {
            orderingRule = reader.readOctetStringAsString();
          }
          if (reader.hasNextElement()
              && (reader.peekType() == TYPE_REVERSE_ORDER))
          {
            reverseOrder = reader.readBoolean();
          }
          reader.readEndSequence();

          request.addSortKey(new SortKey(attrName, orderingRule,
              reverseOrder));
        }
        reader.readEndSequence();

        return request;
      }
      catch (IOException e)
      {
        LocalizableMessage message = INFO_SORTREQ_CONTROL_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }



    public String getOID()
    {
      return OID_SERVER_SIDE_SORT_REQUEST_CONTROL;
    }

  }



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class ResponseDecoder implements
      ControlDecoder<Response>
  {
    /**
     * {@inheritDoc}
     */
    public Response decode(boolean isCritical, ByteString value, Schema schema)
        throws DecodeException
    {
      if (value == null)
      {
        LocalizableMessage message = INFO_SORTRES_CONTROL_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        SortResult sortResult = SortResult.valueOf(reader
            .readEnumerated());

        String attributeType = null;
        if (reader.hasNextElement())
        {
          attributeType = reader.readOctetStringAsString();
        }

        return new Response(isCritical, sortResult, attributeType);
      }
      catch (IOException e)
      {
        LocalizableMessage message = INFO_SORTRES_CONTROL_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }



    public String getOID()
    {
      return OID_SERVER_SIDE_SORT_RESPONSE_CONTROL;
    }

  }



  /**
   * The BER type to use when encoding the orderingRule element.
   */
  private static final byte TYPE_ORDERING_RULE_ID = (byte) 0x80;

  /**
   * The BER type to use when encoding the reverseOrder element.
   */
  private static final byte TYPE_REVERSE_ORDER = (byte) 0x81;

  /**
   * The BER type to use when encoding the attribute type element.
   */
  private static final byte TYPE_ATTRIBUTE_TYPE = (byte) 0x80;

  /**
   * The Control Decoder that can be used to decode the request control.
   */
  public static final ControlDecoder<Request> REQUEST_DECODER = new RequestDecoder();

  /**
   * The Control Decoder that can be used to decode the response
   * control.
   */
  public static final ControlDecoder<Response> RESPONSE_DECODER = new ResponseDecoder();



  private static void decodeSortOrderString(String sortOrderString,
      List<SortKey> sortKeys) throws DecodeException
  {
    StringTokenizer tokenizer = new StringTokenizer(sortOrderString,
        ",");

    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken().trim();
      boolean reverseOrder = false;
      if (token.startsWith("-"))
      {
        reverseOrder = true;
        token = token.substring(1);
      }
      else if (token.startsWith("+"))
      {
        token = token.substring(1);
      }

      int colonPos = token.indexOf(':');
      if (colonPos < 0)
      {
        if (token.length() == 0)
        {
          LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_ATTR_NAME
              .get(sortOrderString);
          throw DecodeException.error(message);
        }

        sortKeys.add(new SortKey(token, null, reverseOrder));
      }
      else if (colonPos == 0)
      {
        LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_ATTR_NAME
            .get(sortOrderString);
        throw DecodeException.error(message);
      }
      else if (colonPos == (token.length() - 1))
      {
        LocalizableMessage message = INFO_SORTREQ_CONTROL_NO_MATCHING_RULE
            .get(sortOrderString);
        throw DecodeException.error(message);
      }
      else
      {
        String attrName = token.substring(0, colonPos);
        String ruleID = token.substring(colonPos + 1);

        sortKeys.add(new SortKey(attrName, ruleID, reverseOrder));
      }
    }
  }
}
