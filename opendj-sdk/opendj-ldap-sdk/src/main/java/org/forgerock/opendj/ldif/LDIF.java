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
 *      Copyright 2011-2012 ForgeRock AS
 */

package org.forgerock.opendj.ldif;

import static org.forgerock.opendj.ldap.CoreMessages.REJECTED_CHANGE_FAIL_DELETE;
import static org.forgerock.opendj.ldap.CoreMessages.REJECTED_CHANGE_FAIL_MODIFY;
import static org.forgerock.opendj.ldap.CoreMessages.REJECTED_CHANGE_FAIL_MODIFYDN;
import static com.forgerock.opendj.util.StaticUtils.getBytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.forgerock.opendj.asn1.ASN1;
import org.forgerock.opendj.asn1.ASN1Reader;
import org.forgerock.opendj.asn1.ASN1Writer;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.Matcher;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.SubtreeDeleteRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Responses;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.AttributeUsage;
import org.forgerock.opendj.ldap.schema.Schema;

import com.forgerock.opendj.ldap.LDAPUtils;

/**
 * This class contains common utility methods for creating and manipulating
 * readers and writers.
 */
public final class LDIF {
    // @formatter:off
    private static final class EntryIteratorReader implements EntryReader {
        private final Iterator<Entry> iterator;
        private EntryIteratorReader(final Iterator<Entry> iterator) { this.iterator = iterator; }
        public void close()      { }
        public boolean hasNext() { return iterator.hasNext(); }
        public Entry readEntry() { return iterator.next(); }
    }
    // @formatter:on

    /**
     * Comparator ordering the DN ASC.
     */
    private static final Comparator<byte[][]> DN_ORDER2 = new Comparator<byte[][]>() {
        public int compare(byte[][] b1, byte[][] b2) {
            return DN_ORDER.compare(b1[0], b2[0]);
        }
    };

    /**
     * Comparator ordering the DN ASC.
     */
    private static final Comparator<byte[]> DN_ORDER = new Comparator<byte[]>() {
        public int compare(byte[] b1, byte[] b2) {
            final ByteString bs = ByteString.valueOf(b1);
            final ByteString bs2 = ByteString.valueOf(b2);
            return bs.compareTo(bs2);
        }
    };

    /**
     * Copies the content of {@code input} to {@code output}. This method does
     * not close {@code input} or {@code output}.
     *
     * @param input
     *            The input change record reader.
     * @param output
     *            The output change record reader.
     * @return The output change record reader.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public static ChangeRecordWriter copyTo(final ChangeRecordReader input,
            final ChangeRecordWriter output) throws IOException {
        while (input.hasNext()) {
            output.writeChangeRecord(input.readChangeRecord());
        }
        return output;
    }

    /**
     * Copies the content of {@code input} to {@code output}. This method does
     * not close {@code input} or {@code output}.
     *
     * @param input
     *            The input entry reader.
     * @param output
     *            The output entry reader.
     * @return The output entry reader.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public static EntryWriter copyTo(final EntryReader input, final EntryWriter output)
            throws IOException {
        while (input.hasNext()) {
            output.writeEntry(input.readEntry());
        }
        return output;
    }

    /**
     * Compares the content of {@code source} to the content of {@code target}
     * and returns the differences in a change record reader. Closing the
     * returned reader will cause {@code source} and {@code target} to be closed
     * as well.
     * <p>
     * <b>NOTE:</b> this method reads the content of {@code source} and
     * {@code target} into memory before calculating the differences, and is
     * therefore not suited for use in cases where a very large number of
     * entries are to be compared.
     *
     * @param source
     *            The entry reader containing the source entries to be compared.
     * @param target
     *            The entry reader containing the target entries to be compared.
     * @return A change record reader containing the differences.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public static ChangeRecordReader diff(final EntryReader source, final EntryReader target)
            throws IOException {

        final List<byte[][]> source2 = readEntriesAsList(source);
        final List<byte[][]> target2 = readEntriesAsList(target);
        final Iterator<byte[][]> sourceIterator = source2.iterator();
        final Iterator<byte[][]> targetIterator = target2.iterator();

        return new ChangeRecordReader() {
            private Entry sourceEntry = nextEntry(sourceIterator);
            private Entry targetEntry = nextEntry(targetIterator);

            @Override
            public void close() throws IOException {
                try {
                    source.close();
                } finally {
                    target.close();
                }
            }

            @Override
            public boolean hasNext() {
                return (sourceEntry != null || targetEntry != null);
            }

            @Override
            public ChangeRecord readChangeRecord() throws IOException {
                if (sourceEntry != null && targetEntry != null) {
                    final DN sourceDN = sourceEntry.getName();
                    final DN targetDN = targetEntry.getName();
                    final int cmp = sourceDN.compareTo(targetDN);

                    if (cmp == 0) {
                        // Modify record: entry in both source and target.
                        final ModifyRequest request =
                                Requests.newModifyRequest(sourceEntry, targetEntry);
                        sourceEntry = nextEntry(sourceIterator);
                        targetEntry = nextEntry(targetIterator);
                        return request;
                    } else if (cmp < 0) {
                        // Delete record: entry in source but not in target.
                        final DeleteRequest request =
                                Requests.newDeleteRequest(sourceEntry.getName());
                        sourceEntry = nextEntry(sourceIterator);
                        return request;
                    } else {
                        // Add record: entry in target but not in source.
                        final AddRequest request = Requests.newAddRequest(targetEntry);
                        targetEntry = nextEntry(targetIterator);
                        return request;
                    }
                } else if (sourceEntry != null) {
                    // Delete remaining source records.
                    final DeleteRequest request = Requests.newDeleteRequest(sourceEntry.getName());
                    sourceEntry = nextEntry(sourceIterator);
                    return request;
                } else if (targetEntry != null) {
                    // Add remaining target records.
                    final AddRequest request = Requests.newAddRequest(targetEntry);
                    targetEntry = nextEntry(targetIterator);
                    return request;
                } else {
                    throw new NoSuchElementException();
                }
            }

            private Entry nextEntry(final Iterator<byte[][]> i) throws IOException {
                if (i.hasNext()) {
                    return decodeEntry(i.next()[1]);
                }
                return null;
            }
        };
    }

    /**
     * Returns an entry reader over the provided entry collection.
     *
     * @param entries
     *            The entry collection.
     * @return An entry reader over the provided entry collection.
     */
    public static EntryReader newEntryCollectionReader(final Collection<Entry> entries) {
        return new EntryIteratorReader(entries.iterator());
    }

    /**
     * Returns an entry reader over the provided entry iterator.
     *
     * @param entries
     *            The entry iterator.
     * @return An entry reader over the provided entry iterator.
     */
    public static EntryReader newEntryIteratorReader(final Iterator<Entry> entries) {
        return new EntryIteratorReader(entries);
    }

    /**
     * Applies the set of changes contained in {@code patch} to the content of
     * {@code input} and returns the result in an entry reader. This method
     * ignores missing entries, and overwrites existing entries. Closing the
     * returned reader will cause {@code input} and {@code patch} to be closed
     * as well.
     * <p>
     * <b>NOTE:</b> this method reads the content of {@code input} into memory
     * before applying the changes, and is therefore not suited for use in cases
     * where a very large number of entries are to be patched.
     * <p>
     * <b>NOTE:</b> this method will not perform modifications required in order
     * to maintain referential integrity. In particular, if an entry references
     * another entry using a DN valued attribute and the referenced entry is
     * deleted, then the DN reference will not be removed. The same applies to
     * renamed entries and their references.
     *
     * @param input
     *            The entry reader containing the set of entries to be patched.
     * @param patch
     *            The change record reader containing the set of changes to be
     *            applied.
     * @return An entry reader containing the patched entries.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public static EntryReader patch(final EntryReader input, final ChangeRecordReader patch)
            throws IOException {
        return patch(input, patch, RejectedChangeRecordListener.OVERWRITE);
    }

    /**
     * Applies the set of changes contained in {@code patch} to the content of
     * {@code input} and returns the result in an entry reader. Closing the
     * returned reader will cause {@code input} and {@code patch} to be closed
     * as well.
     * <p>
     * <b>NOTE:</b> this method reads the content of {@code input} into memory
     * before applying the changes, and is therefore not suited for use in cases
     * where a very large number of entries are to be patched.
     * <p>
     * <b>NOTE:</b> this method will not perform modifications required in order
     * to maintain referential integrity. In particular, if an entry references
     * another entry using a DN valued attribute and the referenced entry is
     * deleted, then the DN reference will not be removed. The same applies to
     * renamed entries and their references.
     *
     * @param input
     *            The entry reader containing the set of entries to be patched.
     * @param patch
     *            The change record reader containing the set of changes to be
     *            applied.
     * @param listener
     *            The rejected change listener.
     * @return An entry reader containing the patched entries.
     * @throws IOException
     *             If an unexpected IO error occurred.
     */
    public static EntryReader patch(final EntryReader input, final ChangeRecordReader patch,
            final RejectedChangeRecordListener listener) throws IOException {
        final SortedMap<byte[], byte[]> entries = readEntriesAsMap(input);

        while (patch.hasNext()) {
            final ChangeRecord change = patch.readChangeRecord();
            final DN changeDN = change.getName();
            final byte[] changeNormDN = getBytes(change.getName().toNormalizedString());

            final DecodeException de =
                    change.accept(new ChangeRecordVisitor<DecodeException, Void>() {

                        @Override
                        public DecodeException visitChangeRecord(final Void p,
                                final AddRequest change) {

                            if (entries.get(changeNormDN) != null) {
                                final Entry existingEntry = decodeEntry(entries.get(changeNormDN));
                                try {
                                    final Entry entry =
                                            listener.handleDuplicateEntry(change, existingEntry);
                                    entries.put(getBytes(entry.getName().toNormalizedString()),
                                            encodeEntry(entry)[1]);
                                } catch (final DecodeException e) {
                                    return e;
                                }
                            } else {
                                entries.put(changeNormDN, encodeEntry(change)[1]);
                            }
                            return null;
                        }

                        @Override
                        public DecodeException visitChangeRecord(final Void p,
                                final DeleteRequest change) {
                            if (entries.get(changeNormDN) == null) {
                                try {
                                    listener.handleRejectedChangeRecord(change,
                                            REJECTED_CHANGE_FAIL_DELETE.get(change.getName()
                                                    .toString()));
                                } catch (final DecodeException e) {
                                    return e;
                                }
                            } else {
                                try {
                                    if (change.getControl(SubtreeDeleteRequestControl.DECODER,
                                            new DecodeOptions()) != null) {
                                        entries.subMap(
                                                getBytes(change.getName().toNormalizedString()),
                                                getBytes(change.getName().child(RDN.maxValue())
                                                        .toNormalizedString())).clear();
                                    } else {
                                        entries.remove(changeNormDN);
                                    }
                                } catch (final DecodeException e) {
                                    return e;
                                }

                            }
                            return null;
                        }

                        @Override
                        public DecodeException visitChangeRecord(final Void p,
                                final ModifyDNRequest change) {
                            if (entries.get(changeNormDN) == null) {
                                try {
                                    listener.handleRejectedChangeRecord(change,
                                            REJECTED_CHANGE_FAIL_MODIFYDN.get(change.getName()
                                                    .toString()));
                                } catch (final DecodeException e) {
                                    return e;
                                }
                            } else {
                                // Calculate the old and new DN.
                                final DN oldDN = changeDN;

                                DN newSuperior = change.getNewSuperior();
                                if (newSuperior == null) {
                                    newSuperior = change.getName().parent();
                                    if (newSuperior == null) {
                                        newSuperior = DN.rootDN();
                                    }
                                }
                                final DN newDN = newSuperior.child(change.getNewRDN());

                                // Move the renamed entries into a separate map
                                // in order to avoid cases where the renamed subtree overlaps.
                                final SortedMap<byte[], byte[]> renamedEntries =
                                        new TreeMap<byte[], byte[]>(DN_ORDER);

                                // @formatter:off
                                final Iterator<Map.Entry<byte[], byte[]>> i =
                                    entries.subMap(changeNormDN,
                                        getBytes((changeDN.child(RDN.maxValue())).
                                                toNormalizedString())).entrySet().iterator();
                                // @formatter:on

                                while (i.hasNext()) {
                                    final Map.Entry<byte[], byte[]> e = i.next();
                                    final Entry entry = decodeEntry(e.getValue());
                                    final DN renamedDN = entry.getName().rename(oldDN, newDN);
                                    entry.setName(renamedDN);
                                    renamedEntries.put(getBytes(renamedDN.toNormalizedString()),
                                            encodeEntry(entry)[1]);
                                    i.remove();
                                }

                                // Modify target entry
                                final Entry targetEntry =
                                        decodeEntry(renamedEntries.values().iterator().next());

                                if (change.isDeleteOldRDN()) {
                                    for (final AVA ava : oldDN.rdn()) {
                                        targetEntry.removeAttribute(ava.toAttribute(), null);
                                    }
                                }
                                for (final AVA ava : newDN.rdn()) {
                                    targetEntry.addAttribute(ava.toAttribute());
                                }

                                renamedEntries.remove(getBytes(targetEntry.getName()
                                        .toNormalizedString()));
                                renamedEntries.put(getBytes(targetEntry.getName()
                                        .toNormalizedString()), encodeEntry(targetEntry)[1]);

                                // Add the renamed entries.
                                final Iterator<byte[]> j = renamedEntries.values().iterator();
                                while (j.hasNext()) {
                                    final Entry renamedEntry = decodeEntry(j.next());
                                    final byte[] existingEntryDn =
                                            entries.get(getBytes(renamedEntry.getName()
                                                    .toNormalizedString()));

                                    if (existingEntryDn != null) {
                                        final Entry existingEntry = decodeEntry(existingEntryDn);
                                        try {
                                            final Entry tmp =
                                                    listener.handleDuplicateEntry(change,
                                                            existingEntry, renamedEntry);
                                            entries.put(
                                                    getBytes(tmp.getName().toNormalizedString()),
                                                    encodeEntry(tmp)[1]);
                                        } catch (final DecodeException e) {
                                            return e;
                                        }
                                    } else {
                                        entries.put(getBytes(renamedEntry.getName()
                                                .toNormalizedString()),
                                                encodeEntry(renamedEntry)[1]);
                                    }
                                }
                                renamedEntries.clear();
                            }
                            return null;
                        }

                        @Override
                        public DecodeException visitChangeRecord(final Void p,
                                final ModifyRequest change) {
                            if (entries.get(changeNormDN) == null) {
                                try {
                                    listener.handleRejectedChangeRecord(change,
                                            REJECTED_CHANGE_FAIL_MODIFY.get(change.getName()
                                                    .toString()));
                                } catch (final DecodeException e) {
                                    return e;
                                }
                            } else {
                                final Entry entry = decodeEntry(entries.get(changeNormDN));
                                for (final Modification modification : change.getModifications()) {
                                    final ModificationType modType =
                                            modification.getModificationType();
                                    if (modType.equals(ModificationType.ADD)) {
                                        entry.addAttribute(modification.getAttribute(), null);
                                    } else if (modType.equals(ModificationType.DELETE)) {
                                        entry.removeAttribute(modification.getAttribute(), null);
                                    } else if (modType.equals(ModificationType.REPLACE)) {
                                        entry.replaceAttribute(modification.getAttribute());
                                    } else {
                                        System.err.println("Unable to apply \"" + modType
                                                + "\" modification to entry \"" + change.getName()
                                                + "\": modification type not supported");
                                    }
                                }
                                entries.put(changeNormDN, encodeEntry(entry)[1]);
                            }
                            return null;
                        }

                    }, null);

            if (de != null) {
                throw de;
            }
        }

        return new EntryReader() {
            private final Iterator<byte[]> iterator = entries.values().iterator();

            @Override
            public void close() throws IOException {
                try {
                    input.close();
                } finally {
                    patch.close();
                }
            }

            @Override
            public boolean hasNext() throws IOException {
                return iterator.hasNext();
            }

            @Override
            public Entry readEntry() throws IOException {
                return decodeEntry(iterator.next());
            }
        };
    }

    /**
     * Returns a filtered view of {@code input} containing only those entries
     * which match the search base DN, scope, and filtered defined in
     * {@code search}. In addition, returned entries will be filtered according
     * to any attribute filtering criteria defined in the search request.
     * <p>
     * The filter and attribute descriptions will be decoded using the default
     * schema.
     *
     * @param input
     *            The entry reader containing the set of entries to be filtered.
     * @param search
     *            The search request defining the filtering criteria.
     * @return A filtered view of {@code input} containing only those entries
     *         which match the provided search request.
     */
    public static EntryReader search(final EntryReader input, final SearchRequest search) {
        return search(input, search, Schema.getDefaultSchema());
    }

    /**
     * Returns a filtered view of {@code input} containing only those entries
     * which match the search base DN, scope, and filtered defined in
     * {@code search}. In addition, returned entries will be filtered according
     * to any attribute filtering criteria defined in the search request.
     * <p>
     * The filter and attribute descriptions will be decoded using the provided
     * schema.
     *
     * @param input
     *            The entry reader containing the set of entries to be filtered.
     * @param search
     *            The search request defining the filtering criteria.
     * @param schema
     *            The schema which should be used to decode the search filter
     *            and attribute descriptions.
     * @return A filtered view of {@code input} containing only those entries
     *         which match the provided search request.
     */
    public static EntryReader search(final EntryReader input, final SearchRequest search,
            final Schema schema) {
        final Matcher matcher = search.getFilter().matcher(schema);

        return new EntryReader() {
            private Entry nextEntry = null;
            private int entryCount = 0;

            public void close() throws IOException {
                input.close();
            }

            public boolean hasNext() throws IOException {
                if (nextEntry == null) {
                    final int sizeLimit = search.getSizeLimit();
                    if (sizeLimit == 0 || entryCount < sizeLimit) {
                        final DN baseDN = search.getName();
                        final SearchScope scope = search.getScope();
                        while (input.hasNext()) {
                            final Entry entry = input.readEntry();
                            if (entry.getName().isInScopeOf(baseDN, scope)
                                    && matcher.matches(entry).toBoolean()) {
                                nextEntry = filterEntry(entry);
                                break;
                            }
                        }
                    }
                }
                return nextEntry != null;
            }

            public Entry readEntry() throws IOException {
                if (hasNext()) {
                    final Entry entry = nextEntry;
                    nextEntry = null;
                    entryCount++;
                    return entry;
                } else {
                    throw new NoSuchElementException();
                }
            }

            private Entry filterEntry(final Entry entry) {
                // TODO: rename attributes; move functionality to Entries.
                if (search.getAttributes().isEmpty()) {
                    if (search.isTypesOnly()) {
                        final Entry filteredEntry = new LinkedHashMapEntry(entry.getName());
                        for (final Attribute attribute : entry.getAllAttributes()) {
                            filteredEntry.addAttribute(Attributes.emptyAttribute(attribute
                                    .getAttributeDescription()));
                        }
                        return filteredEntry;
                    } else {
                        return entry;
                    }
                } else {
                    final Entry filteredEntry = new LinkedHashMapEntry(entry.getName());
                    for (final String atd : search.getAttributes()) {
                        if (atd.equals("*")) {
                            for (final Attribute attribute : entry.getAllAttributes()) {
                                if (attribute.getAttributeDescription().getAttributeType()
                                        .getUsage() == AttributeUsage.USER_APPLICATIONS) {
                                    if (search.isTypesOnly()) {
                                        filteredEntry
                                                .addAttribute(Attributes.emptyAttribute(attribute
                                                        .getAttributeDescription()));
                                    } else {
                                        filteredEntry.addAttribute(attribute);
                                    }
                                }
                            }
                        } else if (atd.equals("+")) {
                            for (final Attribute attribute : entry.getAllAttributes()) {
                                if (attribute.getAttributeDescription().getAttributeType()
                                        .getUsage() != AttributeUsage.USER_APPLICATIONS) {
                                    if (search.isTypesOnly()) {
                                        filteredEntry
                                                .addAttribute(Attributes.emptyAttribute(attribute
                                                        .getAttributeDescription()));
                                    } else {
                                        filteredEntry.addAttribute(attribute);
                                    }
                                }
                            }
                        } else {
                            final AttributeDescription ad =
                                    AttributeDescription.valueOf(atd, schema);
                            for (final Attribute attribute : entry.getAllAttributes(ad)) {
                                if (search.isTypesOnly()) {
                                    filteredEntry.addAttribute(Attributes.emptyAttribute(attribute
                                            .getAttributeDescription()));
                                } else {
                                    filteredEntry.addAttribute(attribute);
                                }
                            }
                        }
                    }
                    return filteredEntry;
                }
            }

        };
    }

    private static List<byte[][]> readEntriesAsList(final EntryReader reader) throws IOException {
        final List<byte[][]> entries = new ArrayList<byte[][]>();

        while (reader.hasNext()) {
            final Entry entry = reader.readEntry();
            entries.add(encodeEntry(entry));
        }
        // Sorting the list by DN
        Collections.sort(entries, DN_ORDER2);

        return entries;
    }

    private static TreeMap<byte[], byte[]> readEntriesAsMap(final EntryReader reader)
            throws IOException {
        final TreeMap<byte[], byte[]> entries = new TreeMap<byte[], byte[]>(DN_ORDER);

        while (reader.hasNext()) {
            final Entry entry = reader.readEntry();
            final byte[][] bEntry = encodeEntry(entry);
            entries.put(bEntry[0], bEntry[1]);
        }

        return entries;
    }

    private static SearchResultEntry decodeEntry(final byte[] asn1EntryFormat) {
        final ASN1Reader readerASN1 = ASN1.getReader(asn1EntryFormat);
        try {
            final SearchResultEntry sr =
                    LDAPUtils.decodeSearchResultEntry(readerASN1, new DecodeOptions());
            readerASN1.close();
            return sr;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[][] encodeEntry(final Entry entry) {
        final byte[][] bEntry = new byte[2][];

        final ByteStringBuilder bsb = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(bsb);
        // Store normalized DN
        bEntry[0] = getBytes(entry.getName().toNormalizedString());
        try {
            // Store ASN1 representation of the entry.
            LDAPUtils.encodeSearchResultEntry(writer, Responses.newSearchResultEntry(entry));
            bEntry[1] = bsb.toByteArray();
            return bEntry;
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    // Prevent instantiation.
    private LDIF() {
        // Do nothing.
    }
}
