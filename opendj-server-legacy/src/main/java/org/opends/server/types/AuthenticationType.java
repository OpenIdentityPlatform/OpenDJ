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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.types;

/**
 * This enumeration defines the set of possible authentication types
 * that may be used for a bind request.  This is based on the LDAP
 * specification defined in RFC 2251.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
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

  /** The BER type tag that is associated with this authentication type. */
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
   * Retrieves the BER type tag associated with this authentication type.
   *
   * @return  The BER type tag associated with this authentication type.
   */
  public int getBERType()
  {
    return berType;
  }

  @Override
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
