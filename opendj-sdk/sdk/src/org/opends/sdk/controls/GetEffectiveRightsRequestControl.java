package org.opends.sdk.controls;



import static com.sun.opends.sdk.util.Messages.INFO_GETEFFECTIVERIGHTS_DECODE_ERROR;
import static com.sun.opends.sdk.util.Messages.INFO_GETEFFECTIVERIGHTS_INVALID_AUTHZID;

import java.io.IOException;
import java.util.*;

import com.sun.opends.sdk.util.Message;
import org.opends.sdk.DN;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.AttributeType;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;
import org.opends.sdk.util.ByteStringBuilder;
import org.opends.sdk.util.Validator;



/**
 * This class partially implements the geteffectiverights control as
 * defined
 * in draft-ietf-ldapext-acl-model-08.txt. The main differences are:
 * - The response control is not supported. Instead the dseecompat
 * geteffectiverights control implementation creates attributes
 * containing
 * right information strings and adds those attributes to the
 * entry being returned. The attribute type names are dynamically
 * created;
 * see the dseecompat's AciGetEffectiveRights class for details.
 * - The dseecompat implementation allows additional attribute types
 * in the request control for which rights information can be returned.
 * These are known as the specified attribute types.
 * The dseecompat request control value is the following: <BR>
 * 
 * <PRE>
 *  GetRightsControl ::= SEQUENCE {
 *    authzId    authzId
 *    attributes  SEQUENCE OF AttributeType
 *  }
 *   -- Only the "dn:DN form is supported.
 * 
 * </PRE>
 **/
public class GetEffectiveRightsRequestControl extends Control
{
  /**
   * The OID for the get effective rights control.
   */
  public static final String OID_GET_EFFECTIVE_RIGHTS = "1.3.6.1.4.1.42.2.27.9.5.2";



  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private static final class Decoder implements
      ControlDecoder<GetEffectiveRightsRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public GetEffectiveRightsRequestControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      // If the value is null create a GetEffectiveRightsRequestControl
      // class with null authzDN and attribute list, else try to
      // decode the value.
      if (value == null)
        return new GetEffectiveRightsRequestControl(isCritical,
            (String) null);
      else
      {
        ASN1Reader reader = ASN1.getReader(value);
        String authzDN;
        List<String> attrs = Collections.emptyList();
        try
        {
          reader.readStartSequence();
          String authzIDString = reader.readOctetStringAsString();
          String lowerAuthzIDString = authzIDString.toLowerCase();
          // Make sure authzId starts with "dn:" and is a valid DN.
          if (lowerAuthzIDString.startsWith("dn:"))
            authzDN = authzIDString.substring(3);
          else
          {
            Message message = INFO_GETEFFECTIVERIGHTS_INVALID_AUTHZID
                .get(lowerAuthzIDString);
            throw DecodeException.error(message);
          }
          // There is an sequence containing an attribute list, try to
          // decode it.
          if (reader.hasNextElement())
          {
            attrs = new LinkedList<String>();
            reader.readStartSequence();
            while (reader.hasNextElement())
            {
              // Decode as an octet string.
              attrs.add(reader.readOctetStringAsString());
            }
            reader.readEndSequence();
          }
          reader.readEndSequence();
        }
        catch (IOException e)
        {
          Message message = INFO_GETEFFECTIVERIGHTS_DECODE_ERROR.get(e
              .getMessage());
          throw DecodeException.error(message);
        }

        return new GetEffectiveRightsRequestControl(isCritical,
            authzDN, attrs);
      }
    }



    public String getOID()
    {
      return OID_GET_EFFECTIVE_RIGHTS;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<GetEffectiveRightsRequestControl> DECODER = new Decoder();

  // The raw DN representing the authzId
  private String authorizationDN = null;

  // The raw DN representing the authzId
  private List<String> attributes = null;



  public GetEffectiveRightsRequestControl(boolean isCritical,
      String authorizationDN, String... attributes)
  {
    super(OID_GET_EFFECTIVE_RIGHTS, isCritical);

    this.authorizationDN = authorizationDN;
    if (attributes != null)
    {
      this.attributes = new ArrayList<String>(attributes.length);
      this.attributes.addAll(Arrays.asList(attributes));
    }
    else
    {
      this.attributes = Collections.emptyList();
    }
  }



  public GetEffectiveRightsRequestControl(boolean isCritical,
      DN authorizationDN, AttributeType... attributes)
  {
    super(OID_GET_EFFECTIVE_RIGHTS, isCritical);

    Validator.ensureNotNull(authorizationDN, attributes);

    this.authorizationDN = authorizationDN.toString();

    if (attributes != null)
    {
      for (AttributeType attr : attributes)
      {
        this.attributes = new ArrayList<String>(attributes.length);
        this.attributes.add(attr.getNameOrOID());
      }
    }
    else
    {
      this.attributes = Collections.emptyList();
    }
  }



  private GetEffectiveRightsRequestControl(boolean isCritical,
      String authorizationDN, List<String> attributes)
  {
    super(OID_GET_EFFECTIVE_RIGHTS, isCritical);

    Validator.ensureNotNull(authorizationDN, attributes);

    this.authorizationDN = authorizationDN;
    this.attributes = attributes;
  }



  public ByteString getValue()
  {
    if (authorizationDN == null && attributes.isEmpty())
    {
      return ByteString.empty();
    }

    ByteStringBuilder buffer = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(buffer);
    try
    {
      writer.writeStartSequence();
      if (authorizationDN != null)
      {
        writer.writeOctetString("dn:" + authorizationDN);
      }

      if (!attributes.isEmpty())
      {
        writer.writeStartSequence();
        for (String attr : attributes)
        {
          writer.writeOctetString(attr);
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



  public boolean hasValue()
  {
    return authorizationDN != null || !attributes.isEmpty();
  }



  /**
   * Appends a string representation of this proxied auth v2 control to
   * the provided buffer.
   * 
   * @param buffer
   *          The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("GetEffectiveRightsRequestControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(", authorizationDN=\"");
    buffer.append(authorizationDN);
    buffer.append("\"");
    buffer.append(", attributes=(");
    buffer.append(attributes);
    buffer.append("))");
  }
}
