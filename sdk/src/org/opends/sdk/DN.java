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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static org.opends.messages.SchemaMessages.*;

import java.util.*;

import org.opends.messages.Message;
import org.opends.sdk.schema.Schema;
import org.opends.sdk.schema.UnknownSchemaElementException;
import org.opends.sdk.util.LocalizedIllegalArgumentException;
import org.opends.sdk.util.SubstringReader;
import org.opends.sdk.util.Validator;



/**
 * A distinguished name (DN) as defined in RFC 4512 section 2.3 is the
 * concatenation of its relative distinguished name (RDN) and its
 * immediate superior's DN. A DN unambiguously refers to an entry in the
 * Directory.
 * <p>
 * The following are examples of string representations of DNs:
 * 
 * <pre>
 * UID=nobody@example.com,DC=example,DC=com CN=John
 * Smith,OU=Sales,O=ACME Limited,L=Moab,ST=Utah,C=US
 * </pre>
 * 
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC
 *      4512 - Lightweight Directory Access Protocol (LDAP): Directory
 *      Information Models </a>
 */
public final class DN implements Iterable<RDN>, Comparable<DN>
{
  private static final DN ROOT_DN = new DN(null, null, "");

  // This is the size of the per-thread per-schema DN cache. We should
  // be conservative here in case there are many threads. We will only
  // cache parent DNs, so there's no need for it to be big.
  private static final int DN_CACHE_SIZE = 32;

  private static final ThreadLocal<WeakHashMap<Schema, Map<String, DN>>> CACHE = new ThreadLocal<WeakHashMap<Schema, Map<String, DN>>>()
  {

    /**
     * {@inheritDoc}
     */
    @Override
    protected WeakHashMap<Schema, Map<String, DN>> initialValue()
    {
      return new WeakHashMap<Schema, Map<String, DN>>();
    }

  };



  /**
   * Returns the Root DN. The Root DN does not contain and RDN
   * components and is superior to all other DNs.
   * 
   * @return The Root DN.
   */
  public static DN rootDN()
  {
    return ROOT_DN;
  }



  /**
   * Parses the provided LDAP string representation of a DN using the
   * default schema.
   * 
   * @param dn
   *          The LDAP string representation of a DN.
   * @return The parsed DN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public static DN valueOf(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return valueOf(dn, Schema.getDefaultSchema());
  }



  /**
   * Parses the provided LDAP string representation of a DN using the
   * provided schema.
   * 
   * @param dn
   *          The LDAP string representation of a DN.
   * @param schema
   *          The schema to use when parsing the DN.
   * @return The parsed DN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} or {@code schema} was {@code null}.
   */
  public static DN valueOf(String dn, Schema schema)
      throws LocalizedIllegalArgumentException
  {
    Validator.ensureNotNull(schema);
    if (dn.length() == 0)
    {
      return ROOT_DN;
    }

    // First check if DN is already cached.
    final Map<String, DN> cache = getCache(schema);
    final DN cachedDN = cache.get(dn);
    if (cachedDN != null)
    {
      return cachedDN;
    }

    // Not in cache so decode.
    final SubstringReader reader = new SubstringReader(dn);
    return decode(dn, reader, schema, cache);
  }



  // Decodes a DN using the provided reader and schema.
  private static DN decode(String dnString, SubstringReader reader,
      Schema schema, Map<String, DN> cache)
      throws LocalizedIllegalArgumentException
  {
    reader.skipWhitespaces();
    if (reader.remaining() == 0)
    {
      return ROOT_DN;
    }

    RDN rdn;
    try
    {
      rdn = RDN.decode(null, reader, schema);
    }
    catch (final UnknownSchemaElementException e)
    {
      final Message message = ERR_DN_TYPE_NOT_FOUND.get(reader
          .getString(), e.getMessageObject());
      throw new LocalizedIllegalArgumentException(message);
    }

    DN parent;
    if (reader.remaining() > 0 && reader.read() == ',')
    {
      reader.mark();
      final String parentString = reader.read(reader.remaining());

      parent = cache.get(parentString);
      if (parent == null)
      {
        reader.reset();
        parent = decode(parentString, reader, schema, cache);

        // Only cache parent DNs since leaf DNs are likely to make the
        // cache to volatile.
        cache.put(parentString, parent);
      }
    }
    else
    {
      parent = ROOT_DN;
    }

    return new DN(rdn, parent, dnString);
  }



  @SuppressWarnings("serial")
  private static Map<String, DN> getCache(Schema schema)
  {
    final WeakHashMap<Schema, Map<String, DN>> threadLocalMap = CACHE
        .get();
    Map<String, DN> schemaLocalMap = threadLocalMap.get(schema);

    if (schemaLocalMap == null)
    {
      schemaLocalMap = new LinkedHashMap<String, DN>(DN_CACHE_SIZE,
          0.75f, true)
      {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, DN> e)
        {
          return size() > DN_CACHE_SIZE;
        }
      };
      threadLocalMap.put(schema, schemaLocalMap);
    }
    return schemaLocalMap;
  }



  private final RDN rdn;

  private final DN parent;

  private final int size;

  // We need to store the original string value if provided in order to
  // preserve the original whitespace.
  private String stringValue;

  private String normalizedStringValue = null;



  // Private constructor.
  private DN(RDN rdn, DN parent, String stringValue)
  {
    this.rdn = rdn;
    this.parent = parent;
    this.stringValue = stringValue;
    this.size = parent != null ? parent.size + 1 : 0;
  }



  /**
   * Returns a DN which is subordinate to this DN and having the
   * additional RDN components contained in the provided DN.
   * 
   * @param dn
   *          The DN containing the RDN components to be added to this
   *          DN.
   * @return The subordinate DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public DN child(DN dn) throws NullPointerException
  {
    Validator.ensureNotNull(dn);

    if (dn.isRootDN())
    {
      return this;
    }
    else if (isRootDN())
    {
      return dn;
    }
    else
    {
      final RDN[] rdns = new RDN[dn.size()];
      int i = rdns.length;
      for (DN next = dn; next.rdn != null; next = next.parent)
      {
        rdns[--i] = next.rdn;
      }
      DN newDN = this;
      for (i = 0; i < rdns.length; i++)
      {
        newDN = new DN(rdns[i], newDN, null);
      }
      return newDN;
    }
  }



  /**
   * Returns a DN which is an immediate child of this DN and having the
   * specified RDN.
   * 
   * @param rdn
   *          The RDN for the child DN.
   * @return The child DN.
   * @throws NullPointerException
   *           If {@code rdn} was {@code null}.
   */
  public DN child(RDN rdn) throws NullPointerException
  {
    Validator.ensureNotNull(rdn);
    return new DN(rdn, this, null);
  }



  /**
   * Returns a DN which is subordinate to this DN and having the
   * additional RDN components contained in the provided DN decoded
   * using the default schema.
   * 
   * @param dn
   *          The DN containing the RDN components to be added to this
   *          DN.
   * @return The subordinate DN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public DN child(String dn) throws LocalizedIllegalArgumentException,
      NullPointerException
  {
    Validator.ensureNotNull(dn);
    return child(valueOf(dn));
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(DN dn)
  {
    final String s1 = toNormalizedString();
    final String s2 = dn.toNormalizedString();
    return s1.compareTo(s2);
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof DN)
    {
      final String s1 = toNormalizedString();
      final String s2 = ((DN) obj).toNormalizedString();
      return s1.equals(s2);
    }
    else
    {
      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
    final String s = toNormalizedString();
    return s.hashCode();
  }



  /**
   * Returns {@code true} if this DN is an immediate child of the
   * provided DN.
   * 
   * @param dn
   *          The potential parent DN.
   * @return {@code true} if this DN is the immediate child of the
   *         provided DN, otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isChildOf(DN dn) throws NullPointerException
  {
    // If this is the Root DN then parent will be null but this is ok.
    return dn.equals(parent);
  }



  /**
   * Returns {@code true} if this DN is an immediate child of the
   * provided DN decoded using the default schema.
   * 
   * @param dn
   *          The potential parent DN.
   * @return {@code true} if this DN is the immediate child of the
   *         provided DN, otherwise {@code false}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isChildOf(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // If this is the Root DN then parent will be null but this is ok.
    return isChildOf(valueOf(dn));
  }



  /**
   * Returns {@code true} if this DN is the immediate parent of the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is the immediate parent of the
   *         provided DN, otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isParentOf(DN dn) throws NullPointerException
  {
    // If dn is the Root DN then parent will be null but this is ok.
    return equals(dn.parent);
  }



  /**
   * Returns {@code true} if this DN is the immediate parent of the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is the immediate parent of the
   *         provided DN, otherwise {@code false}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isParentOf(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    // If dn is the Root DN then parent will be null but this is ok.
    return isParentOf(valueOf(dn));
  }



  /**
   * Returns {@code true} if this DN is the Root DN.
   * 
   * @return {@code true} if this DN is the Root DN, otherwise {@code
   *         false}.
   */
  public boolean isRootDN()
  {
    return size == 0;
  }



  /**
   * Returns {@code true} if this DN is subordinate to or equal to the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is subordinate to or equal to the
   *         provided DN, otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isSubordinateOrEqualTo(DN dn)
      throws NullPointerException
  {
    if (size < dn.size)
    {
      return false;
    }
    else if (size == dn.size)
    {
      return equals(dn);
    }
    else
    {
      // dn is a potential superior of this.
      return parent(dn.size - size).equals(dn);
    }
  }



  /**
   * Returns {@code true} if this DN is subordinate to or equal to the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is subordinate to or equal to the
   *         provided DN, otherwise {@code false}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isSubordinateOrEqualTo(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return isSubordinateOrEqualTo(valueOf(dn));
  }



  /**
   * Returns {@code true} if this DN is superior to or equal to the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is superior to or equal to the
   *         provided DN, otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isSuperiorOrEqualTo(DN dn) throws NullPointerException
  {
    if (size > dn.size)
    {
      return false;
    }
    else if (size == dn.size)
    {
      return equals(dn);
    }
    else
    {
      // dn is a potential subordinate of this.
      return dn.parent(dn.size - size).equals(this);
    }
  }



  /**
   * Returns {@code true} if this DN is superior to or equal to the
   * provided DN.
   * 
   * @param dn
   *          The potential child DN.
   * @return {@code true} if this DN is superior to or equal to the
   *         provided DN, otherwise {@code false}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} is not a valid LDAP string representation
   *           of a DN.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  public boolean isSuperiorOrEqualTo(String dn)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return isSuperiorOrEqualTo(valueOf(dn));
  }



  /**
   * Returns an iterator of the RDNs contained in this DN. The RDNs will
   * be returned in the order starting with this DN's RDN, followed by
   * the RDN of the parent DN, and so on.
   * <p>
   * Attempts to remove RDNs using an iterator's {@code remove()} method
   * are not permitted and will result in an {@code
   * UnsupportedOperationException} being thrown.
   * 
   * @return An iterator of the RDNs contained in this DN.
   */
  public Iterator<RDN> iterator()
  {
    return new Iterator<RDN>()
    {
      private DN dn = DN.this;



      public boolean hasNext()
      {
        return dn.rdn != null;
      }



      public RDN next()
      {
        if (dn.rdn == null)
        {
          throw new NoSuchElementException();
        }

        final RDN rdn = dn.rdn;
        dn = dn.parent;
        return rdn;
      }



      public void remove()
      {
        throw new UnsupportedOperationException();
      }
    };
  }



  /**
   * Returns the DN which is the immediate parent of this DN, or {@code
   * null} if this DN is the Root DN.
   * <p>
   * This method is equivalent to:
   * 
   * <pre>
   * parent(1);
   * </pre>
   * 
   * @return The DN which is the immediate parent of this DN, or {@code
   *         null} if this DN is the Root DN.
   */
  public DN parent()
  {
    return parent;
  }



  /**
   * Returns the DN which is equal to this DN with the specified number
   * of RDNs removed. Note that if {@code index} is zero then this DN
   * will be returned (identity).
   * 
   * @param index
   *          The number of RDNs to be removed.
   * @return The DN which is equal to this DN with the specified number
   *         of RDNs removed, or {@code null} if the parent of the Root
   *         DN is reached.
   * @throws IllegalArgumentException
   *           If {@code index} is less than zero.
   */
  public DN parent(int index) throws IllegalArgumentException
  {
    // We allow size + 1 so that we can return null as the parent of the
    // Root DN.
    Validator.ensureTrue(index >= 0, "index less than zero");

    DN parentDN = this;
    for (int i = 0; parentDN != null && i < index; i++)
    {
      parentDN = parentDN.parent;
    }
    return parentDN;
  }



  /**
   * Returns the RDN of this DN, or {@code null} if this DN is the Root
   * DN.
   * 
   * @return The RDN of this DN, or {@code null} if this DN is the Root
   *         DN.
   */
  public RDN rdn()
  {
    return rdn;
  }



  /**
   * Returns the number of RDN components in this DN.
   * 
   * @return The number of RDN components in this DN.
   */
  public int size()
  {
    return size();
  }



  /**
   * Returns the normalized string representation of this DN.
   * 
   * @return The normalized string representation of this DN.
   */
  public String toNormalizedString()
  {
    if (normalizedStringValue == null)
    {
      final StringBuilder builder = new StringBuilder();
      if (!parent.isRootDN())
      {
        builder.append(parent.toNormalizedString());
        builder.append(',');
      }
      rdn.toNormalizedString(builder);
      normalizedStringValue = builder.toString();
    }
    return normalizedStringValue;
  }



  /**
   * Returns the RFC 4514 string representation of this DN.
   * 
   * @return The RFC 4514 string representation of this DN.
   * @see <a href="http://tools.ietf.org/html/rfc4514">RFC 4514 -
   *      Lightweight Directory Access Protocol (LDAP): String
   *      Representation of Distinguished Names </a>
   */
  public String toString()
  {
    // We don't care about potential race conditions here.
    if (stringValue == null)
    {
      final StringBuilder builder = new StringBuilder();
      rdn.toString(builder);
      if (!parent.isRootDN())
      {
        builder.append(',');
        builder.append(parent.toString());
      }
      stringValue = builder.toString();
    }
    return stringValue;
  }
}
