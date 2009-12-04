package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.io.IOException;

import org.opends.sdk.ByteString;
import org.opends.sdk.ByteStringBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.asn1.ASN1;
import org.opends.sdk.asn1.ASN1Reader;
import org.opends.sdk.asn1.ASN1Writer;
import org.opends.sdk.schema.Schema;

import com.sun.opends.sdk.util.Message;
import com.sun.opends.sdk.util.Validator;



/**
 * This class implements the virtual list view controls as defined in
 * draft-ietf-ldapext-ldapv3-vlv.
 */
public class VLVControl
{
  /**
   * The OID for the virtual list view request control.
   */
  public static final String OID_VLV_REQUEST_CONTROL = "2.16.840.1.113730.3.4.9";

  /**
   * The OID for the virtual list view request control.
   */
  public static final String OID_VLV_RESPONSE_CONTROL = "2.16.840.1.113730.3.4.10";



  public static class Request extends Control
  {
    private int beforeCount;

    private int afterCount;

    private VLVTarget target;

    private ByteString contextID;



    /**
     * Creates a new VLV request control that will identify the target
     * entry by an assertion value.
     * 
     * @param isCritical
     *          Indicates whether the control should be considered
     *          critical.
     * @param beforeCount
     *          The number of entries before the target assertion value.
     * @param afterCount
     *          The number of entries after the target assertion value.
     * @param assertionValue
     *          The greaterThanOrEqual target assertion value that
     *          indicates where to start the page of results.
     * @param contextID
     *          The context ID provided by the server in the last VLV
     *          response for the same set of criteria, or {@code null}
     *          if there was no previous VLV response or the server did
     *          not include a context ID in the last response.
     */
    public Request(boolean isCritical, int beforeCount, int afterCount,
        ByteString assertionValue, ByteString contextID)
    {
      this(isCritical, beforeCount, afterCount, VLVTarget
          .greaterThanOrEqual(assertionValue), contextID);
    }



    /**
     * Creates a new VLV request control that will identify the target
     * entry by offset.
     * 
     * @param isCritical
     *          Indicates whether or not the control is critical.
     * @param beforeCount
     *          The number of entries before the target offset to
     *          retrieve in the results page.
     * @param afterCount
     *          The number of entries after the target offset to
     *          retrieve in the results page.
     * @param offset
     *          The offset in the result set to target for the beginning
     *          of the page of results.
     * @param contentCount
     *          The content count returned by the server in the last
     *          phase of the VLV request, or zero for a new VLV request
     *          session.
     * @param contextID
     *          The context ID provided by the server in the last VLV
     *          response for the same set of criteria, or {@code null}
     *          if there was no previous VLV response or the server did
     *          not include a context ID in the last response.
     */
    public Request(boolean isCritical, int beforeCount, int afterCount,
        int offset, int contentCount, ByteString contextID)
    {
      this(isCritical, beforeCount, afterCount, VLVTarget.byOffset(
          offset, contentCount), contextID);
    }



    /**
     * Creates a new VLV request control that will identify the target
     * entry by an assertion value.
     * 
     * @param beforeCount
     *          The number of entries before the target offset to
     *          retrieve in the results page.
     * @param afterCount
     *          The number of entries after the target offset to
     *          retrieve in the results page.
     * @param greaterThanOrEqual
     *          The greaterThanOrEqual target assertion value that
     *          indicates where to start the page of results.
     */
    public Request(int beforeCount, int afterCount,
        ByteString greaterThanOrEqual)
    {
      this(false, beforeCount, afterCount, greaterThanOrEqual, null);
    }



    /**
     * Creates a new VLV request control that will identify the target
     * entry by offset.
     * 
     * @param beforeCount
     *          The number of entries before the target offset to
     *          retrieve in the results page.
     * @param afterCount
     *          The number of entries after the target offset to
     *          retrieve in the results page.
     * @param offset
     *          The offset in the result set to target for the beginning
     *          of the page of results.
     * @param contentCount
     *          The content count returned by the server in the last
     *          phase of the VLV request, or zero for a new VLV request
     *          session.
     */
    public Request(int beforeCount, int afterCount, int offset,
        int contentCount)
    {
      this(false, beforeCount, afterCount, offset, contentCount, null);
    }



    private Request(boolean isCritical, int beforeCount,
        int afterCount, VLVTarget target, ByteString contextID)
    {
      super(OID_VLV_REQUEST_CONTROL, isCritical);

      this.beforeCount = beforeCount;
      this.afterCount = afterCount;
      this.target = target;
      this.contextID = contextID;
    }



    /**
     * Retrieves the number of entries after the target offset or
     * assertion value to include in the results page.
     * 
     * @return The number of entries after the target offset to include
     *         in the results page.
     */
    public int getAfterCount()
    {
      return afterCount;
    }



    /**
     * Retrieves the number of entries before the target offset or
     * assertion value to include in the results page.
     * 
     * @return The number of entries before the target offset to include
     *         in the results page.
     */
    public int getBeforeCount()
    {
      return beforeCount;
    }



    /**
     * Retrieves a context ID value that should be used to resume a
     * previous VLV results session.
     * 
     * @return A context ID value that should be used to resume a
     *         previous VLV results session, or {@code null} if none is
     *         available.
     */
    public ByteString getContextID()
    {
      return contextID;
    }



    public VLVTarget getTarget()
    {
      return target;
    }



    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writer.writeStartSequence();
        writer.writeInteger(beforeCount);
        writer.writeInteger(afterCount);
        target.encode(writer);
        if (contextID != null)
        {
          writer.writeOctetString(contextID);
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



    public Request setAfterCount(int afterCount)
    {
      this.afterCount = afterCount;
      return this;
    }



    public Request setBeforeCount(int beforeCount)
    {
      this.beforeCount = beforeCount;
      return this;
    }



    public Request setContextID(ByteString contextID)
    {
      this.contextID = contextID;
      return this;
    }



    public Request setTarget(ByteString assertionValue)
    {
      Validator.ensureNotNull(assertionValue);
      target = VLVTarget.greaterThanOrEqual(assertionValue);
      return this;
    }



    public Request setTarget(int offset, int contentCount)
    {
      target = VLVTarget.byOffset(offset, contentCount);
      return this;
    }



    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("VLVRequestControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", beforeCount=");
      buffer.append(beforeCount);
      buffer.append(", afterCount=");
      buffer.append(afterCount);
      buffer.append(", target=");
      target.toString(buffer);
      buffer.append(", contextID=");
      buffer.append(contextID);
      buffer.append(")");
    }
  }



  /**
   * This class implements the virtual list view response controls as
   * defined in draft-ietf-ldapext-ldapv3-vlv. The ASN.1 description for
   * the control value is: <BR>
   * <BR>
   * 
   * <PRE>
   * VirtualListViewResponse ::= SEQUENCE {
   *       targetPosition    INTEGER (0 .. maxInt),
   *       contentCount     INTEGER (0 .. maxInt),
   *       virtualListViewResult ENUMERATED {
   *            success (0),
   *            operationsError (1),
   *            protocolError (3),
   *            unwillingToPerform (53),
   *            insufficientAccessRights (50),
   *            timeLimitExceeded (3),
   *            adminLimitExceeded (11),
   *            innapropriateMatching (18),
   *            sortControlMissing (60),
   *            offsetRangeError (61),
   *            other(80),
   *            ... },
   *       contextID     OCTET STRING OPTIONAL }
   * </PRE>
   */
  public static class Response extends Control
  {
    private final int targetPosition;

    private final int contentCount;

    private final VLVResult vlvResult;

    private final ByteString contextID;



    /**
     * Creates a new VLV response control with the provided information.
     * 
     * @param isCritical
     *          Indicates whether the control should be considered
     *          critical.
     * @param targetPosition
     *          The position of the target entry in the result set.
     * @param contentCount
     *          The content count estimating the total number of entries
     *          in the result set.
     * @param vlvResult
     *          The result code for the VLV operation.
     * @param contextID
     *          The context ID for this VLV response control.
     */
    public Response(boolean isCritical, int targetPosition,
        int contentCount, VLVResult vlvResult, ByteString contextID)
    {
      super(OID_VLV_RESPONSE_CONTROL, isCritical);

      this.targetPosition = targetPosition;
      this.contentCount = contentCount;
      this.vlvResult = vlvResult;
      this.contextID = contextID;
    }



    /**
     * Creates a new VLV response control with the provided information.
     * 
     * @param targetPosition
     *          The position of the target entry in the result set.
     * @param contentCount
     *          The content count estimating the total number of entries
     *          in the result set.
     * @param vlvResult
     *          The result code for the VLV operation.
     */
    public Response(int targetPosition, int contentCount,
        VLVResult vlvResult)
    {
      this(false, targetPosition, contentCount, vlvResult, null);
    }



    /**
     * Retrieves the estimated total number of entries in the result
     * set.
     * 
     * @return The estimated total number of entries in the result set.
     */
    public int getContentCount()
    {
      return contentCount;
    }



    /**
     * Retrieves a context ID value that should be included in the next
     * request to retrieve a page of the same result set.
     * 
     * @return A context ID value that should be included in the next
     *         request to retrieve a page of the same result set, or
     *         {@code null} if there is no context ID.
     */
    public ByteString getContextID()
    {
      return contextID;
    }



    /**
     * Retrieves the position of the target entry in the result set.
     * 
     * @return The position of the target entry in the result set.
     */
    public int getTargetPosition()
    {
      return targetPosition;
    }



    @Override
    public ByteString getValue()
    {
      ByteStringBuilder buffer = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(buffer);
      try
      {
        writer.writeStartSequence();
        writer.writeInteger(targetPosition);
        writer.writeInteger(contentCount);
        writer.writeEnumerated(vlvResult.intValue());
        if (contextID != null)
        {
          writer.writeOctetString(contextID);
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
     * Retrieves the result code for the VLV operation.
     * 
     * @return The result code for the VLV operation.
     */
    public VLVResult getVLVResult()
    {
      return vlvResult;
    }



    @Override
    public boolean hasValue()
    {
      return true;
    }



    /**
     * Appends a string representation of this VLV request control to
     * the provided buffer.
     * 
     * @param buffer
     *          The buffer to which the information should be appended.
     */
    @Override
    public void toString(StringBuilder buffer)
    {
      buffer.append("VLVResponseControl(oid=");
      buffer.append(getOID());
      buffer.append(", criticality=");
      buffer.append(isCritical());
      buffer.append(", targetPosition=");
      buffer.append(targetPosition);
      buffer.append(", contentCount=");
      buffer.append(contentCount);
      buffer.append(", vlvResult=");
      buffer.append(vlvResult);

      if (contextID != null)
      {
        buffer.append(", contextID=");
        buffer.append(contextID);
      }

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
        Message message = INFO_VLVREQ_CONTROL_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();

        int beforeCount = (int) reader.readInteger();
        int afterCount = (int) reader.readInteger();
        VLVTarget target = VLVTarget.decode(reader);
        ByteString contextID = null;
        if (reader.hasNextElement())
        {
          contextID = reader.readOctetString();
        }

        return new Request(isCritical, beforeCount, afterCount, target,
            contextID);
      }
      catch (IOException e)
      {
        Message message = INFO_VLVREQ_CONTROL_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }



    public String getOID()
    {
      return OID_VLV_REQUEST_CONTROL;
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
        Message message = INFO_VLVRES_CONTROL_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();

        int targetPosition = (int) reader.readInteger();
        int contentCount = (int) reader.readInteger();
        VLVResult vlvResult = VLVResult
            .valueOf(reader.readEnumerated());
        ByteString contextID = null;
        if (reader.hasNextElement())
        {
          contextID = reader.readOctetString();
        }

        return new Response(isCritical, targetPosition, contentCount,
            vlvResult, contextID);
      }
      catch (IOException e)
      {
        Message message = INFO_VLVRES_CONTROL_CANNOT_DECODE_VALUE
            .get(getExceptionMessage(e));
        throw DecodeException.error(message, e);
      }
    }



    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return OID_VLV_RESPONSE_CONTROL;
    }
  }



  /**
   * The Control Decoder that can be used to decode the request control.
   */
  public static final ControlDecoder<Request> REQUEST_DECODER = new RequestDecoder();

  /**
   * The Control Decoder that can be used to decode the response
   * control.
   */
  public static final ControlDecoder<Response> RESPONSE_DECODER = new ResponseDecoder();
}
