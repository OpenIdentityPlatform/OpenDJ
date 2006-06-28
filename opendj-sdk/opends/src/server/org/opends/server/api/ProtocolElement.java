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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.api;



/**
 * This interface defines a set of methods that must be implemented by
 * any class that forms the basis for a protocol element (e.g., an
 * ASN.1 element, an LDAP message, etc.).
 */
public interface ProtocolElement
{
  /**
   * Retrieves the name of the protocol associated with this protocol
   * element.
   *
   * @return  The name of the protocol associated with this protocol
   *          element.
   */
  public String getProtocolElementName();



  /**
   * Retrieves a string representation of this protocol element.
   *
   * @return  A string representation of this protocol element.
   */
  public String toString();



  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   */
  public void toString(StringBuilder buffer);



  /**
   * Appends a string representation of this protocol element to the
   * provided buffer.
   *
   * @param  buffer  The buffer into which the string representation
   *                 should be written.
   * @param  indent  The number of spaces that should be used to
   *                 indent the resulting string representation.
   */
  public void toString(StringBuilder buffer, int indent);
}

