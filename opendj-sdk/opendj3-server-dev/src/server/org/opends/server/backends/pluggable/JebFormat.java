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

import java.util.Iterator;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RDN;
import org.opends.server.util.StaticUtils;

/**
 * Handles the disk representation of LDAP data.
 */
public class JebFormat
{

  /** The format version used by this class to encode and decode a ByteString. */
  public static final byte FORMAT_VERSION = 0x01;
  /** The ASN1 tag for the ByteString type. */
  public static final byte TAG_DATABASE_ENTRY = 0x60;
  /** The ASN1 tag for the DirectoryServerEntry type. */
  public static final byte TAG_DIRECTORY_SERVER_ENTRY = 0x61;

  /**
   * Decode a DN value from its database key representation.
   *
   * @param dnKey The database key value of the DN.
   * @param prefix The DN to prefix the decoded DN value.
   * @return The decoded DN value.
   * @throws DirectoryException if an error occurs while decoding the DN value.
   * @see #dnToDNKey(DN, int)
   */
  public static DN dnFromDNKey(ByteSequence dnKey, DN prefix) throws DirectoryException
  {
    DN dn = prefix;
    boolean escaped = false;
    ByteStringBuilder buffer = new ByteStringBuilder();
    for(int i = 0; i < dnKey.length(); i++)
    {
      if(dnKey.byteAt(i) == 0x5C)
      {
        escaped = true;
        continue;
      }
      else if(!escaped && dnKey.byteAt(i) == 0x01)
      {
        buffer.append(0x01);
        escaped = false;
        continue;
      }
      else if(!escaped && dnKey.byteAt(i) == 0x00)
      {
        if(buffer.length() > 0)
        {
          dn = dn.child(RDN.decode(buffer.toString()));
          buffer.clear();
        }
      }
      else
      {
        if(escaped)
        {
          buffer.append(0x5C);
          escaped = false;
        }
        buffer.append(dnKey.byteAt(i));
      }
    }

    if(buffer.length() > 0)
    {
      dn = dn.child(RDN.decode(buffer.toString()));
    }

    return dn;
  }

  public static int findDNKeyParent(ByteSequence dnKey)
  {
    if (dnKey.length() == 0)
    {
      // This is the root or base DN
      return -1;
    }

    // We will walk backwards through the buffer and find the first unescaped comma
    for (int i = dnKey.length() - 1; i >= 0; i--)
    {
      if (dnKey.byteAt(i) == 0x00 && i - 1 >= 0 && dnKey.byteAt(i - 1) != 0x5C)
      {
        return i;
      }
    }
    return 0;
  }

  /**
   * Create a DN database key from an entry DN.
   * @param dn The entry DN.
   * @param prefixRDNs The number of prefix RDNs to remove from the encoded
   *                   representation.
   * @return A ByteString containing the key.
   */
  public static ByteString dnToDNKey(DN dn, int prefixRDNs)
  {
    StringBuilder buffer = new StringBuilder();
    for (int i = dn.size() - prefixRDNs - 1; i >= 0; i--)
    {
      buffer.append('\u0000');
      formatRDNKey(dn.getRDN(i), buffer);
    }

    return ByteString.wrap(StaticUtils.getBytes(buffer.toString()));
  }

  private static void formatRDNKey(RDN rdn, StringBuilder buffer)
  {
    if (!rdn.isMultiValued())
    {
      rdn.toString(buffer);
    }
    else
    {
      TreeSet<String> rdnElementStrings = new TreeSet<String>();

      for (int i=0; i < rdn.getNumValues(); i++)
      {
        StringBuilder b2 = new StringBuilder();
        rdnElementStrings.add(b2.toString());
      }

      Iterator<String> iterator = rdnElementStrings.iterator();
      buffer.append(iterator.next().replace("\u0001", "\\\u0001"));
      while (iterator.hasNext())
      {
        buffer.append('\u0001');
        buffer.append(iterator.next().replace("\u0001", "\\\u0001"));
      }
    }
  }
}
