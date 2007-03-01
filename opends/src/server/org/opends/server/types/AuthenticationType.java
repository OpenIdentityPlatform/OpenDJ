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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



/**
 * This enumeration defines the set of possible authentication types
 * that may be used for a bind request.  This is based on the LDAP
 * specification defined in RFC 2251.
 */
public enum AuthenticationType
{
  /**
   * The authentication type that indicates that the user will be
   * performing simple authentication (i.e., just a password).
   */
  SIMPLE((byte) 0x80),



  /**
   * The authentication type that indicates that the user will be
   * performing SASL authentication using some extensible mechanism.
   */
  SASL((byte) 0xA3),



  /**
   * The authentication type that indicates that the associated
   * connection is an internal connection.
   */
  INTERNAL((byte) 0xFF);



  // The BER type tag that is associated with this authentication
  // type.
  private byte berType;



  /**
   * Creates a new authentication type with the provided BER type tag.
   *
   * @param  berType  The BER type tag that is associated with this
   *                  authentication type.
   */
  private AuthenticationType(byte berType)
  {
    this.berType = berType;
  }



  /**
   * Retrieves the BER type tag associated with this authentication
   * type.
   *
   * @return  The BER type tag associated with this authentication
   *          type.
   */
  public int getBERType()
  {
    return berType;
  }



  /**
   * Retrieves a string representation of this authentication type.
   *
   * @return  A string representation of this authentication type.
   */
  public String toString()
  {
    switch (berType)
    {
      case (byte) 0x80:
        return "Simple";
      case (byte) 0xA3:
        return "SASL";
      case (byte) 0xFF:
        return "Internal";
      default:
        return "Unknown";
    }
  }
}

