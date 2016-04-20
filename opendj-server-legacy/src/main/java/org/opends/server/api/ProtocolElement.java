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
package org.opends.server.api;

/**
 * This interface defines a set of methods that must be implemented by
 * any class that forms the basis for a protocol element (e.g., an
 * ASN.1 element, an LDAP message, etc.).
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayExtend=true,
     mayInvoke=true)
public interface ProtocolElement
{
  /**
   * Retrieves the name of the protocol associated with this protocol element.
   *
   * @return  The name of the protocol associated with this protocol element.
   */
  String getProtocolElementName();

  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   */
  void toString(StringBuilder buffer);

  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   * @param  indent  The number of spaces that should be used to
   *                 indent the resulting string representation.
   */
  void toString(StringBuilder buffer, int indent);
}
