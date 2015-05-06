/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.DN;

/**
 * Handles the disk representation of LDAP data.
 */
public class DnKeyFormat
{

  /** The format version used by this class to encode and decode a ByteString. */
  static final byte FORMAT_VERSION = 0x01;

  /**
   * Find the length of bytes that represents the superior DN of the given DN
   * key. The superior DN is represented by the initial bytes of the DN key.
   *
   * @param dnKey
   *          The key value of the DN.
   * @return The length of the superior DN or -1 if the given dn is the root DN
   *         or 0 if the superior DN is removed.
   */
  static int findDNKeyParent(ByteSequence dnKey)
  {
    if (dnKey.length() == 0)
    {
      // This is the root or base DN
      return -1;
    }

    // We will walk backwards through the buffer
    // and find the first unescaped NORMALIZED_RDN_SEPARATOR
    for (int i = dnKey.length() - 1; i >= 0; i--)
    {
      if (dnKey.byteAt(i) == DN.NORMALIZED_RDN_SEPARATOR && i - 1 >= 0 && dnKey.byteAt(i - 1) != DN.NORMALIZED_ESC_BYTE)
      {
        return i;
      }
    }
    return 0;
  }

  /**
   * Create a DN key from an entry DN.
   *
   * @param dn The entry DN.
   * @param prefixRDNs The number of prefix RDNs to remove from the encoded
   *                   representation.
   * @return A ByteString containing the key.
   */
  static ByteString dnToDNKey(DN dn, int prefixRDNs)
  {
    final ByteStringBuilder builder = new ByteStringBuilder(128);
    final int startSize = dn.size() - prefixRDNs - 1;
    for (int i = startSize; i >= 0; i--)
    {
        builder.append(DN.NORMALIZED_RDN_SEPARATOR);
        dn.getRDN(i).toNormalizedByteString(builder);
    }
    return builder.toByteString();
  }
}
