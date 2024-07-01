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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;

/** Handles the disk representation of LDAP data. */
public class DnKeyFormat
{

  // The following fields have been copied from the DN class in the SDK
  /** RDN separator for normalized byte string of a DN. */
  private static final byte NORMALIZED_RDN_SEPARATOR = 0x00;
  /** AVA separator for normalized byte string of a DN. */
  private static final byte NORMALIZED_AVA_SEPARATOR = 0x01;
  /** Escape byte for normalized byte string of a DN. */
  private static final byte NORMALIZED_ESC_BYTE = 0x02;

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
      if (positionIsRDNSeparator(dnKey, i))
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
    return dn.localName(dn.size() - prefixRDNs).toNormalizedByteString();
  }

  /**
   * Returns a best effort conversion from key to a human readable DN.
   * @param key the index key
   * @return a best effort conversion from key to a human readable DN.
   */
  static String keyToDNString(ByteString key)
  {
    return key.toByteString().toASCIIString();
  }

  private static boolean positionIsRDNSeparator(ByteSequence key, int index)
  {
    return index > 0
        && key.byteAt(index) == NORMALIZED_RDN_SEPARATOR && key.byteAt(index - 1) != NORMALIZED_ESC_BYTE;
  }

  static ByteStringBuilder beforeFirstChildOf(final ByteSequence key)
  {
    final ByteStringBuilder beforeKey = new ByteStringBuilder(key.length() + 1);
    beforeKey.appendBytes(key);
    beforeKey.appendByte(NORMALIZED_RDN_SEPARATOR);
    return beforeKey;
  }

  static ByteStringBuilder afterLastChildOf(final ByteSequence key)
  {
    final ByteStringBuilder afterKey = new ByteStringBuilder(key.length() + 1);
    afterKey.appendBytes(key);
    afterKey.appendByte(NORMALIZED_AVA_SEPARATOR);
    return afterKey;
  }

  /**
   * Check if two DN have a parent-child relationship.
   *
   * @param parent
   *          The potential parent
   * @param child
   *          The potential child of parent
   * @return true if child is a direct children of parent, false otherwise.
   */
  static boolean isChild(ByteSequence parent, ByteSequence child)
  {
    if (child.length() <= parent.length()
        || child.byteAt(parent.length()) != NORMALIZED_RDN_SEPARATOR
        || !child.startsWith(parent))
    {
      return false;
    }
    // Immediate children should only have one RDN separator past the parent length
    boolean childSeparatorDetected = false;
    for (int i = parent.length() ; i < child.length(); i++)
    {
      if (child.byteAt(i) == NORMALIZED_RDN_SEPARATOR)
      {
        if (childSeparatorDetected)
        {
          return false;
        }
        childSeparatorDetected = true;
      }
    }
    return childSeparatorDetected;
  }
}
