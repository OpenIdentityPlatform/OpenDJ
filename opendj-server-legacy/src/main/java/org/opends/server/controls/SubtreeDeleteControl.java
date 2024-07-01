/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.controls;



import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.forgerock.opendj.ldap.ResultCode;



/**
 * This class implements the subtree delete control defined in
 * draft-armijo-ldap-treedelete. It makes it possible for clients to
 * delete subtrees of entries.
 */
public class SubtreeDeleteControl extends Control
{
  /** ControlDecoder implementation to decode this control from a ByteString. */
  private static final class Decoder implements
      ControlDecoder<SubtreeDeleteControl>
  {
    @Override
    public SubtreeDeleteControl decode(boolean isCritical,
        ByteString value) throws DirectoryException
    {
      if (value != null)
      {
        LocalizableMessage message =
            ERR_SUBTREE_DELETE_INVALID_CONTROL_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      return new SubtreeDeleteControl(isCritical);
    }



    @Override
    public String getOID()
    {
      return OID_SUBTREE_DELETE_CONTROL;
    }

  }



  /** The Control Decoder that can be used to decode this control. */
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



  @Override
  protected void writeValue(ASN1Writer writer) throws IOException
  {
    // Nothing to do.
  }



  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("SubtreeDeleteControl()");
  }

}
