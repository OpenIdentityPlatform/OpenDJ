package org.opends.sdk.controls;



import static com.sun.opends.sdk.util.Messages.ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE;
import static com.sun.opends.sdk.util.Messages.ERR_LDAP_PAGED_RESULTS_DECODE_NULL;
import static com.sun.opends.sdk.util.Messages.ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE;
import static com.sun.opends.sdk.util.Messages.ERR_LDAP_PAGED_RESULTS_DECODE_SIZE;

import java.io.IOException;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.ByteStringBuilder;
import org.opends.sdk.util.StaticUtils;
import org.opends.sdk.util.Validator;



/**
 * This class represents a paged results control value as defined in RFC
 * 2696. The searchControlValue is an OCTET STRING wrapping the
 * BER-encoded version of the following SEQUENCE: realSearchControlValue
 * ::= SEQUENCE { size INTEGER (0..maxInt), -- requested page size from
 * client -- result set size estimate from server cookie OCTET STRING }
 */
public class PagedResultsControl extends Control
{
  /**
   * The OID for the paged results control defined in RFC 2696.
   */
  public static final String OID_PAGED_RESULTS_CONTROL = "1.2.840.113556.1.4.319";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<PagedResultsControl>
  {
    /**
     * {@inheritDoc}
     */
    public PagedResultsControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value == null)
      {
        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_NULL.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
      }
      catch (Exception e)
      {
        StaticUtils.DEBUG_LOG.throwing("PagedResultsControl.Decoder",
            "decode", e);

        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE
            .get(String.valueOf(e));
        throw DecodeException.error(message, e);
      }

      int size;
      try
      {
        size = (int) reader.readInteger();
      }
      catch (Exception e)
      {
        StaticUtils.DEBUG_LOG.throwing("PagedResultsControl.Decoder",
            "decode", e);

        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_SIZE.get(String
            .valueOf(e));
        throw DecodeException.error(message, e);
      }

      ByteString cookie;
      try
      {
        cookie = reader.readOctetString();
      }
      catch (Exception e)
      {
        StaticUtils.DEBUG_LOG.throwing("PagedResultsControl.Decoder",
            "decode", e);

        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_COOKIE
            .get(String.valueOf(e));
        throw DecodeException.error(message, e);
      }

      try
      {
        reader.readEndSequence();
      }
      catch (Exception e)
      {
        StaticUtils.DEBUG_LOG.throwing("PagedResultsControl.Decoder",
            "decode", e);

        Message message = ERR_LDAP_PAGED_RESULTS_DECODE_SEQUENCE
            .get(String.valueOf(e));
        throw DecodeException.error(message, e);
      }

      return new PagedResultsControl(isCritical, size, cookie);
    }



    public String getOID()
    {
      return OID_PAGED_RESULTS_CONTROL;
    }
  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<PagedResultsControl> DECODER = new Decoder();

  /**
   * The control value size element, which is either the requested page
   * size from the client, or the result set size estimate from the
   * server.
   */
  private final int size;

  /**
   * The control value cookie element.
   */
  private final ByteString cookie;



  /**
   * Creates a new paged results control with the specified information.
   * 
   * @param isCritical
   *          Indicates whether this control should be considered
   *          critical in processing the request.
   * @param size
   *          The size element.
   * @param cookie
   *          The cookie element.
   */
  public PagedResultsControl(boolean isCritical, int size,
      ByteString cookie)
  {
    super(OID_PAGED_RESULTS_CONTROL, isCritical);

    Validator.ensureNotNull(cookie);
    this.size = size;
    this.cookie = cookie;
  }



  /**
   * Creates a new paged results control with the specified information.
   * 
   * @param size
   *          The size element.
   * @param cookie
   *          The cookie element.
   */
  public PagedResultsControl(int size, ByteString cookie)
  {
    this(false, size, cookie);
  }



  /**
   * Get the control value cookie element.
   * 
   * @return The control value cookie element.
   */
  public ByteString getCookie()
  {
    return cookie;
  }



  /**
   * Get the control value size element, which is either the requested
   * page size from the client, or the result set size estimate from the
   * server.
   * 
   * @return The control value size element.
   */
  public int getSize()
  {
    return size;
  }



  @Override
  public ByteString getValue()
  {
    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);
    try
    {
      writer.writeStartSequence();
      writer.writeInteger(size);
      writer.writeOctetString(cookie);
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
    buffer.append("PagedResultsControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", size=");
    buffer.append(size);
    buffer.append(", cookie=");
    buffer.append(cookie);
    buffer.append(")");
  }
}
