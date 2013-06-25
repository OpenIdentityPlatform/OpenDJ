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
package org.opends.server.controls;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;

import org.opends.messages.Message;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;



/**
 * This class implements the subtree delete control defined in
 * draft-armijo-ldap-treedelete. It makes it possible for clients to
 * delete subtrees of entries.
 */
public class SubtreeDeleteControl extends Control
{
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
        ByteString value) throws DirectoryException
    {
      if (value != null)
      {
        Message message =
            ERR_SUBTREE_DELETE_INVALID_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
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
  public static final ControlDecoder<SubtreeDeleteControl> DECODER =
      new Decoder();



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



  /**
   * {@inheritDoc}
   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SubtreeDeleteControl()");
  }

}
