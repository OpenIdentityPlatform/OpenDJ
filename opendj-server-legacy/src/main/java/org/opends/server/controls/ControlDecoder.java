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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.server.controls;



import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;



/**
 * An interface for decoding controls.
 *
 * @param <T>
 *          The type of control decoded by this decoder.
 */
public interface ControlDecoder<T extends Control>
{

  /**
   * Decodes the provided control.
   *
   * @param isCritical
   *          Indicates whether the control should be considered
   *          critical.
   * @param value
   *          The value for the control.
   * @return The decoded control.
   * @throws DirectoryException
   *           If the control could not be decoded.
   */
  T decode(boolean isCritical, ByteString value) throws DirectoryException;



  /**
   * Gets the OID of the control decoded by this decoded.
   *
   * @return The OID of the control decoded by this decoded.
   */
  String getOID();
}
