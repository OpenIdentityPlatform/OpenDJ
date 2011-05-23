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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.sdk.controls;



import static org.opends.sdk.CoreMessages.ERR_SUBENTRIES_CONTROL_BAD_OID;
import static org.opends.sdk.CoreMessages.ERR_SUBENTRIES_INVALID_CONTROL_VALUE;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.sdk.ByteString;
import org.opends.sdk.DecodeException;
import org.opends.sdk.DecodeOptions;

import com.forgerock.opendj.util.Validator;



/**
 * The sub-entries request control as defined in draft-ietf-ldup-subentry. This
 * control may be included in a search request to indicate that sub-entries
 * should be included in the search results.
 * <p>
 * In the absence of the sub-entries request control, sub-entries are not
 * visible to search operations unless the target/base of the operation is a
 * sub-entry. In the presence of the sub-entry request control, only sub-entries
 * are visible.
 *
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-ldup-subentry">draft-ietf-ldup-subentry
 *      - LDAP Subentry Schema </a>
 */
public final class SubentriesRequestControl implements Control
{
  /**
   * The OID for the sub-entries request control.
   */
  public static final String OID = "1.3.6.1.4.1.7628.5.101.1";

  private static final SubentriesRequestControl CRITICAL_INSTANCE = new SubentriesRequestControl(
      true);
  private static final SubentriesRequestControl NONCRITICAL_INSTANCE = new SubentriesRequestControl(
      false);

  /**
   * A decoder which can be used for decoding the sub-entries request control.
   */
  public static final ControlDecoder<SubentriesRequestControl> DECODER =
    new ControlDecoder<SubentriesRequestControl>()
  {

    public SubentriesRequestControl decodeControl(final Control control,
        final DecodeOptions options) throws DecodeException
    {
      Validator.ensureNotNull(control);

      if (control instanceof SubentriesRequestControl)
      {
        return (SubentriesRequestControl) control;
      }

      if (!control.getOID().equals(OID))
      {
        final LocalizableMessage message = ERR_SUBENTRIES_CONTROL_BAD_OID.get(
            control.getOID(), OID);
        throw DecodeException.error(message);
      }

      if (control.hasValue())
      {
        final LocalizableMessage message = ERR_SUBENTRIES_INVALID_CONTROL_VALUE
            .get();
        throw DecodeException.error(message);
      }

      return control.isCritical() ? CRITICAL_INSTANCE : NONCRITICAL_INSTANCE;
    }



    public String getOID()
    {
      return OID;
    }
  };



  /**
   * Creates a new sub-entries request control having the provided criticality.
   *
   * @param isCritical
   *          {@code true} if it is unacceptable to perform the operation
   *          without applying the semantics of this control, or {@code false}
   *          if it can be ignored.
   * @return The new control.
   */
  public static SubentriesRequestControl newControl(final boolean isCritical)
  {
    return isCritical ? CRITICAL_INSTANCE : NONCRITICAL_INSTANCE;
  }



  private final boolean isCritical;



  private SubentriesRequestControl(final boolean isCritical)
  {
    this.isCritical = isCritical;
  }



  /**
   * {@inheritDoc}
   */
  public String getOID()
  {
    return OID;
  }



  /**
   * {@inheritDoc}
   */
  public ByteString getValue()
  {
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public boolean hasValue()
  {
    return false;
  }



  /**
   * {@inheritDoc}
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
    final StringBuilder builder = new StringBuilder();
    builder.append("SubentriesRequestControl(oid=");
    builder.append(getOID());
    builder.append(", criticality=");
    builder.append(isCritical());
    builder.append(")");
    return builder.toString();
  }

}
