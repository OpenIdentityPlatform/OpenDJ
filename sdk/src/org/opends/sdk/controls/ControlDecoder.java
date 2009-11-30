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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.sdk.controls;



import org.opends.sdk.DecodeException;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.util.ByteString;



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
   * @param schema
   *          The schema which should be used when decoding the control,
   *          if required.
   * @return The decoded control.
   * @throws DecodeException
   *           If the control could not be decoded.
   */
  T decode(boolean isCritical, ByteString value, Schema schema)
      throws DecodeException;



  /**
   * Gets the OID of the control decoded by this decoded.
   *
   * @return The OID of the control decoded by this decoded.
   */
  String getOID();
}
