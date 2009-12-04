package org.opends.sdk.controls;



import org.opends.sdk.ByteString;



/**
 * Created by IntelliJ IDEA. User: boli Date: Jun 29, 2009 Time:
 * 10:59:19 AM To change this template use File | Settings | File
 * Templates.
 */
public abstract class Control
{
  // The criticality for this control.
  protected final boolean isCritical;

  // The OID for this control.
  protected final String oid;



  public Control(String oid, boolean isCritical)
  {
    this.isCritical = isCritical;
    this.oid = oid;
  }



  /**
   * Retrieves the OID for this control.
   * 
   * @return The OID for this control.
   */
  public String getOID()
  {
    return oid;
  }



  public abstract ByteString getValue();



  public abstract boolean hasValue();



  /**
   * Indicates whether this control should be considered critical in
   * processing the request.
   * 
   * @return <CODE>true</CODE> if this code should be considered
   *         critical, or <CODE>false</CODE> if not.
   */
  public boolean isCritical()
  {
    return isCritical;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  public abstract void toString(StringBuilder buffer);
}
