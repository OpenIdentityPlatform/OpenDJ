package org.opends.sdk.controls;



import static com.sun.opends.sdk.messages.Messages.*;

import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;
import org.opends.sdk.schema.Schema;




/**
 * This class implements the subtree delete control defined in
 * draft-armijo-ldap-treedelete. It makes it possible for clients to
 * delete subtrees of entries.
 */
public class SubtreeDeleteControl extends Control
{
  /**
   * The OID for the subtree delete control.
   */
  public static final String OID_SUBTREE_DELETE_CONTROL = "1.2.840.113556.1.4.805";



  /**
   * ControlDecoder implementation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<SubtreeDeleteControl>
  {
    /**
     * {@inheritDoc}
     */
    public SubtreeDeleteControl decode(boolean isCritical,
        ByteString value, Schema schema) throws DecodeException
    {
      if (value != null)
      {
        LocalizableMessage message = ERR_SUBTREE_DELETE_INVALID_CONTROL_VALUE
            .get();
        throw DecodeException.error(message);
      }

      return new SubtreeDeleteControl(isCritical);
    }



    public String getOID()
    {
      return OID_SUBTREE_DELETE_CONTROL;
    }

  }



  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<SubtreeDeleteControl> DECODER = new Decoder();



  /**
   * Creates a new subtree delete control.
   * 
   * @param isCritical
   *          Indicates whether the control should be considered
   *          critical for the operation processing.
   */
  public SubtreeDeleteControl(boolean isCritical)
  {
    super(OID_SUBTREE_DELETE_CONTROL, isCritical);
  }



  @Override
  public ByteString getValue()
  {
    return null;
  }



  @Override
  public boolean hasValue()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SubtreeDeleteControl(oid=");
    buffer.append(getOID());
    buffer.append(", criticality=");
    buffer.append(isCritical());
    buffer.append(")");
  }

}
