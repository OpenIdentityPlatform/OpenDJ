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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap;

import static com.forgerock.opendj.ldap.CoreMessages.ERR_RDN_TYPE_NOT_FOUND;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;

import com.forgerock.opendj.util.Iterators;
import com.forgerock.opendj.util.SubstringReader;
import org.forgerock.util.Reject;

/**
 * A relative distinguished name (RDN) as defined in RFC 4512 section 2.3 is the
 * name of an entry relative to its immediate superior. An RDN is composed of an
 * unordered set of one or more attribute value assertions (AVA) consisting of
 * an attribute description with zero options and an attribute value. These AVAs
 * are chosen to match attribute values (each a distinguished value) of the
 * entry.
 * <p>
 * An entry's relative distinguished name must be unique among all immediate
 * subordinates of the entry's immediate superior (i.e. all siblings).
 * <p>
 * The following are examples of string representations of RDNs:
 *
 * <pre>
 * uid=12345
 * ou=Engineering
 * cn=Kurt Zeilenga+L=Redwood Shores
 * </pre>
 *
 * The last is an example of a multi-valued RDN; that is, an RDN composed of
 * multiple AVAs.
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC 4512 -
 *      Lightweight Directory Access Protocol (LDAP): Directory Information
 *      Models </a>
 */
public final class RDN implements Iterable<AVA>, Comparable<RDN> {

    /** Separator for AVAs. */
    private static final char AVA_CHAR_SEPARATOR = '+';

    /**
     * A constant holding a special RDN having zero AVAs and which always
     * compares greater than any other RDN other than itself.
     */
    private static final RDN MAX_VALUE = new RDN(new AVA[0], "");

    /**
     * Returns a constant containing a special RDN which is greater than any
     * other RDN other than itself. This RDN may be used in order to perform
     * range queries on DN keyed collections such as {@code SortedSet}s and
     * {@code SortedMap}s. For example, the following code can be used to
     * construct a range whose contents is a sub-tree of entries:
     *
     * <pre>
     * SortedMap<DN, Entry> entries = ...;
     * DN baseDN = ...;
     *
     * // Returns a map containing the baseDN and all of its subordinates.
     * SortedMap<DN,Entry> subtree = entries.subMap(baseDN, baseDN.child(RDN.maxValue));
     * </pre>
     *
     * @return A constant containing a special RDN which is greater than any
     *         other RDN other than itself.
     */
    public static RDN maxValue() {
        return MAX_VALUE;
    }

    /**
     * Parses the provided LDAP string representation of an RDN using the
     * default schema.
     *
     * @param rdn
     *            The LDAP string representation of a RDN.
     * @return The parsed RDN.
     * @throws LocalizedIllegalArgumentException
     *             If {@code rdn} is not a valid LDAP string representation of a
     *             RDN.
     * @throws NullPointerException
     *             If {@code rdn} was {@code null}.
     */
    public static RDN valueOf(final String rdn) {
        return valueOf(rdn, Schema.getDefaultSchema());
    }

    /**
     * Parses the provided LDAP string representation of a RDN using the
     * provided schema.
     *
     * @param rdn
     *            The LDAP string representation of a RDN.
     * @param schema
     *            The schema to use when parsing the RDN.
     * @return The parsed RDN.
     * @throws LocalizedIllegalArgumentException
     *             If {@code rdn} is not a valid LDAP string representation of a
     *             RDN.
     * @throws NullPointerException
     *             If {@code rdn} or {@code schema} was {@code null}.
     */
    public static RDN valueOf(final String rdn, final Schema schema) {
        final SubstringReader reader = new SubstringReader(rdn);
        try {
            return decode(rdn, reader, schema);
        } catch (final UnknownSchemaElementException e) {
            final LocalizableMessage message =
                    ERR_RDN_TYPE_NOT_FOUND.get(rdn, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(message);
        }
    }

    // FIXME: ensure that the decoded RDN does not contain multiple AVAs
    // with the same type.
    static RDN decode(final String rdnString, final SubstringReader reader, final Schema schema) {
        final AVA firstAVA = AVA.decode(reader, schema);

        // Skip over any spaces that might be after the attribute value.
        reader.skipWhitespaces();

        reader.mark();
        if (reader.remaining() > 0 && reader.read() == '+') {
            final List<AVA> avas = new ArrayList<>();
            avas.add(firstAVA);

            do {
                avas.add(AVA.decode(reader, schema));

                // Skip over any spaces that might be after the attribute value.
                reader.skipWhitespaces();

                reader.mark();
            } while (reader.remaining() > 0 && reader.read() == '+');

            reader.reset();
            return new RDN(avas.toArray(new AVA[avas.size()]), null);
        } else {
            reader.reset();
            return new RDN(new AVA[] { firstAVA }, null);
        }
    }

    /** In original order. */
    private final AVA[] avas;

    /**
     * We need to store the original string value if provided in order to
     * preserve the original whitespace.
     */
    private String stringValue;

    /**
     * Creates a new RDN using the provided attribute type and value.
     * <p>
     * If {@code attributeValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeType
     *            The attribute type.
     * @param attributeValue
     *            The attribute value.
     * @throws NullPointerException
     *             If {@code attributeType} or {@code attributeValue} was
     *             {@code null}.
     */
    public RDN(final AttributeType attributeType, final Object attributeValue) {
        this.avas = new AVA[] { new AVA(attributeType, attributeValue) };
    }

    /**
     * Creates a new RDN using the provided attribute type and value decoded
     * using the default schema.
     * <p>
     * If {@code attributeValue} is not an instance of {@code ByteString} then
     * it will be converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param attributeType
     *            The attribute type.
     * @param attributeValue
     *            The attribute value.
     * @throws UnknownSchemaElementException
     *             If {@code attributeType} was not found in the default schema.
     * @throws NullPointerException
     *             If {@code attributeType} or {@code attributeValue} was
     *             {@code null}.
     */
    public RDN(final String attributeType, final Object attributeValue) {
        this.avas = new AVA[] { new AVA(attributeType, attributeValue) };
    }

    /**
     * Creates a new RDN using the provided AVAs.
     *
     * @param avas
     *            The attribute-value assertions used to build this RDN.
     * @throws NullPointerException
     *             If {@code avas} is {@code null} or contains a null ava.
     */
    public RDN(final AVA... avas) {
        this(avas, null);
    }

    /**
     * Creates a new RDN using the provided AVAs.
     *
     * @param avas
     *            The attribute-value assertions used to build this RDN.
     * @throws NullPointerException
     *             If {@code ava} is {@code null} or contains null ava.
     */
    public RDN(Collection<AVA> avas) {
        this(avas.toArray(new AVA[avas.size()]), null);
    }

    private RDN(final AVA[] avas, final String stringValue) {
        Reject.ifNull(avas);
        this.avas = avas;
        this.stringValue = stringValue;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final RDN rdn) {
        // Identity.
        if (this == rdn) {
            return 0;
        }

        // MAX_VALUE is always greater than any other RDN other than itself.
        if (this == MAX_VALUE) {
            return 1;
        }

        if (rdn == MAX_VALUE) {
            return -1;
        }

        // Compare number of AVAs first as this is quick and easy.
        final int sz1 = avas.length;
        final int sz2 = rdn.avas.length;
        if (sz1 != sz2) {
            return sz1 - sz2 > 0 ? 1 : -1;
        }

        // Fast path for common case.
        if (sz1 == 1) {
            return avas[0].compareTo(rdn.avas[0]);
        }

        // Need to sort the AVAs before comparing.
        final AVA[] a1 = new AVA[sz1];
        System.arraycopy(avas, 0, a1, 0, sz1);
        Arrays.sort(a1);

        final AVA[] a2 = new AVA[sz1];
        System.arraycopy(rdn.avas, 0, a2, 0, sz1);
        Arrays.sort(a2);

        for (int i = 0; i < sz1; i++) {
            final int result = a1[i].compareTo(a2[i]);
            if (result != 0) {
                return result;
            }
        }

        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof RDN) {
            return compareTo((RDN) obj) == 0;
        } else {
            return false;
        }
    }

    /**
     * Returns the attribute value contained in this RDN which is associated
     * with the provided attribute type, or {@code null} if this RDN does not
     * include such an attribute value.
     *
     * @param attributeType
     *            The attribute type.
     * @return The attribute value.
     */
    public ByteString getAttributeValue(final AttributeType attributeType) {
        for (final AVA ava : avas) {
            if (ava.getAttributeType().equals(attributeType)) {
                return ava.getAttributeValue();
            }
        }
        return null;
    }

    /**
     * Returns the first AVA contained in this RDN.
     *
     * @return The first AVA contained in this RDN.
     */
    public AVA getFirstAVA() {
        return avas[0];
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        // Avoid an algorithm that requires the AVAs to be sorted.
        int hash = 0;
        for (final AVA ava : avas) {
            hash += ava.hashCode();
        }
        return hash;
    }

    /**
     * Returns {@code true} if this RDN contains more than one AVA.
     *
     * @return {@code true} if this RDN contains more than one AVA, otherwise
     *         {@code false}.
     */
    public boolean isMultiValued() {
        return avas.length > 1;
    }

    /**
     * Returns an iterator of the AVAs contained in this RDN. The AVAs will be
     * returned in the user provided order.
     * <p>
     * Attempts to remove AVAs using an iterator's {@code remove()} method are
     * not permitted and will result in an {@code UnsupportedOperationException}
     * being thrown.
     *
     * @return An iterator of the AVAs contained in this RDN.
     */
    @Override
    public Iterator<AVA> iterator() {
        return Iterators.arrayIterator(avas);
    }

    /**
     * Returns the number of AVAs in this RDN.
     *
     * @return The number of AVAs in this RDN.
     */
    public int size() {
        return avas.length;
    }

    /**
     * Returns the RFC 4514 string representation of this RDN.
     *
     * @return The RFC 4514 string representation of this RDN.
     * @see <a href="http://tools.ietf.org/html/rfc4514">RFC 4514 - Lightweight
     *      Directory Access Protocol (LDAP): String Representation of
     *      Distinguished Names </a>
     */
    @Override
    public String toString() {
        // We don't care about potential race conditions here.
        if (stringValue == null) {
            final StringBuilder builder = new StringBuilder();
            avas[0].toString(builder);
            for (int i = 1; i < avas.length; i++) {
                builder.append('+');
                avas[i].toString(builder);
            }
            stringValue = builder.toString();
        }
        return stringValue;
    }

    StringBuilder toString(final StringBuilder builder) {
        return builder.append(this);
    }

    /**
     * Returns the normalized byte string representation of this RDN.
     * <p>
     * The representation is not a valid RDN.
     *
     * @param builder
     *            The builder to use to construct the normalized byte string.
     * @return The normalized byte string representation.
     * @see DN#toNormalizedByteString()
     */
    ByteStringBuilder toNormalizedByteString(final ByteStringBuilder builder) {
        switch (size()) {
        case 0:
            // Handle RDN.maxValue().
            builder.appendByte(DN.NORMALIZED_AVA_SEPARATOR);
            break;
        case 1:
            getFirstAVA().toNormalizedByteString(builder);
            break;
        default:
            Iterator<AVA> it = getSortedAvas();
            it.next().toNormalizedByteString(builder);
            while (it.hasNext()) {
                builder.appendByte(DN.NORMALIZED_AVA_SEPARATOR);
                it.next().toNormalizedByteString(builder);
            }
            break;
        }
        return builder;
    }

    /**
     * Retrieves a normalized string representation of this RDN.
     * <p>
     * This representation is safe to use in an URL or in a file name.
     * However, it is not a valid RDN and can't be reverted to a valid RDN.
     *
     * @return The normalized string representation of this RDN.
     * @see DN#toNormalizedUrlSafeString()
     */
    StringBuilder toNormalizedUrlSafeString(final StringBuilder builder) {
        switch (size()) {
        case 0:
            // Handle RDN.maxValue().
            builder.append(RDN.AVA_CHAR_SEPARATOR);
            break;
        case 1:
            getFirstAVA().toNormalizedUrlSafe(builder);
            break;
        default:
            Iterator<AVA> it = getSortedAvas();
            it.next().toNormalizedUrlSafe(builder);
            while (it.hasNext()) {
                builder.append(RDN.AVA_CHAR_SEPARATOR);
                it.next().toNormalizedUrlSafe(builder);
            }
            break;
        }
        return builder;
    }

    private Iterator<AVA> getSortedAvas() {
        TreeSet<AVA> sortedAvas = new TreeSet<>();
        for (AVA ava : avas) {
            sortedAvas.add(ava);
        }
        return sortedAvas.iterator();
    }
}
