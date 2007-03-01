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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */



/**
 * Contains the code for the Directory Server backend that uses the Berkeley DB
 * Java Edition as the repository for storing entry and index information.
 * <BR><BR>
 *
 * <H3>On-disk Representation</H3>
 * <P>
 * First it is important to understand JE (Java Edition) terminology.  A JE
 * database environment has similarities to a database in the relational
 * database world. Each environment can have multiple databases, which are
 * similar to tables in a relational database.  A JE environment is identified
 * by the file system directory in which it is stored.  A JE database is
 * identified by a unique name within its environment.  Multiple databases in
 * the same environment may be updated within the same transaction, but
 * transactions cannot span environments.
 * <P>
 * In this description, database means a JE database.
 * <P>
 * Each instance of this backend creates a single JE environment to store its
 * data.  Unlike previous versions of Directory Server, environments are not
 * shared by backend instances.  The backend does support multiple base DNs,
 * so it is still possible for data under multiple suffixes to share the same
 * database environment, by declaring those suffixes as base DNs of a single
 * JE backend instance.
 * <P>
 * The data for a base DN is kept in a set of databases, so that a database
 * contains data for only one base DN.  Each database name is prefixed by
 * the base DN it belongs to, where the DN is simplified by preserving only
 * letters and digits.
 * <P>
 * For example, if you were to use the DbDump utility to list the databases
 * in the environment corresponding to a backend instance containing the base
 * DN dc=example,dc=com, you might see the following:
 * <pre>
 * dc_example_dc_com_cn.equality
 * dc_example_dc_com_cn.presence
 * dc_example_dc_com_cn.substring
 * dc_example_dc_com_dn2id
 * dc_example_dc_com_givenName.equality
 * dc_example_dc_com_givenName.presence
 * dc_example_dc_com_givenName.substring
 * dc_example_dc_com_id2children
 * dc_example_dc_com_id2entry
 * dc_example_dc_com_id2subtree
 * dc_example_dc_com_mail.equality
 * dc_example_dc_com_mail.presence
 * dc_example_dc_com_mail.substring
 * dc_example_dc_com_member.equality
 * dc_example_dc_com_sn.equality
 * dc_example_dc_com_sn.presence
 * dc_example_dc_com_sn.substring
 * dc_example_dc_com_telephoneNumber.equality
 * dc_example_dc_com_telephoneNumber.presence
 * dc_example_dc_com_telephoneNumber.substring
 * dc_example_dc_com_uid.equality
 * </pre>
 * <H4>Database Relocation</H4>
 * <P>
 * The data is stored in a format which is independent of system architecture,
 * and is also independent of file system location because it contains no
 * pathnames.  The backend, and its backups, can be copied, moved and restored
 * to a different location, within the same system or a different system.
 * <P>
 * <H4>The Entry ID</H4>
 * <P>
 * Each entry to be stored in the backend is assigned a 64-bit integer
 * identifier called the entry ID.  The first entry to be created is entry ID 1,
 * the second is entry ID 2, etc.  This ensures that the ID for any given entry
 * is always greater than its superiors.  The backend takes care to preserve
 * this invariant, in particular during Modify DN operations where an entry
 * can be given a new superior.  Clients have come to expect child entries to
 * be returned after their parent in search results, and the backend can ensure
 * this by returning entries in ID order.
 * <P>
 * On disk, an entry ID is stored in eight bytes in big-endian format (from
 * most significant byte to least significant byte).  This enables binary
 * copy of the backend from one system to another, regardless of the system
 * architecture.
 * <P>
 * Currently, IDs of deleted entries are not reused.  The use of a 64-bit
 * integer means it is implausible that the entry ID space will be exhausted.
 * <P>
 * <P>
 * <H4>The entry database (id2entry)</H4>
 * <P>
 * Entries are stored in the id2entry database.  The key to the database is
 * the entry ID, and the value is an ASN.1 encoding of the entry contents.
 * The default JE btree key comparator is used for the entry database,
 * such that cursoring through the database will return entries in order of
 * entry ID.  When the backend starts it is able to determine the last
 * assigned entry ID by reading the last key value in the entry database.
 * <P>
 * The format of the entry on disk is described by the following ASN.1.
 * <P>
 * <pre>
 * DatabaseEntry ::= [APPLICATION 0] IMPLICIT SEQUENCE {
 *  uncompressedSize        INTEGER,      -- A zero value means not compressed.
 *  dataBytes               OCTET STRING  -- Optionally compressed encoding of
 *                                           the data bytes.
 * }
 *
 * ID2EntryValue ::= DatabaseEntry
 *  -- Where dataBytes contains an encoding of DirectoryServerEntry.
 *
 * DirectoryServerEntry ::= [APPLICATION 1] IMPLICIT SEQUENCE {
 *  dn                      LDAPDN,
 *  objectClasses           SET OF LDAPString,
 *  userAttributes          AttributeList,
 *  operationalAttributes   AttributeList
 * }
 * </pre>
 * <P>
 * Entry compression is optional and can be switched on or off at any time.
 * Switching on entry compression only affects future writes, therefore the
 * database can contain a mixture of compressed and not-compressed records.
 * Either record type can be read regardless of the configuration setting.
 * The compression algorithm is the default ZLIB implementation provided by the
 * Java platform.
 * <P>
 * The ASN1 types have application tags to allow for future extensions.
 * The types may be extended with additional fields where this makes sense,
 * or additional types may be defined.
 * <P>
 * <H5>The entry count record</H5>
 * <P>
 * Previous versions of Directory Server provide the current number of entries
 * stored in the backend.  JE does not maintain database record counts,
 * requiring a full key traversal to count the number of records in a database,
 * which is too time consuming for large numbers of entries.
 * <P>
 * For this reason the backend maintains its own count of the number of
 * entries in the entry database, storing this count in the special record
 * whose key is entry ID zero.
 * <P>
 * <P>
 * <H4>The DN database (dn2id)</H4>
 * <P>
 * Although each entry's DN is stored in the entry database, we need to be
 * able to retrieve entries by DN.  The dn2id database key is the normalized
 * DN and the value is the entry ID corresponding to the DN.  A normalized DN
 * is one which may be compared for equality with another using a standard
 * string comparison function.  A given DN can have numerous string
 * representations, due to insignificant whitespace, or insignificant case of
 * attribute names, etc., but it has only one normalized form.  Use of the
 * normalized form enables efficient key comparison.
 * <P>
 * A custom btree key comparator is applied to the DN database, which orders
 * the keys such that a given entry DN comes after the DNs of its superiors,
 * and ensures that the DNs below a given base DN are contiguous.  This
 * ordering is used to return entries for a non-indexed subtree or
 * single level search.  The comparator is just like the default lexicographic
 * comparator except that it compares in reverse byte order.
 * <P>
 * For example, a cursor iteration through a range of the DN database might
 * look like this:
 * <pre>
 * dc=example,dc=com
 * ou=people,dc=example,dc=com
 * uid=user.1000,ou=people,dc=example,dc=com
 * uid=user.2000,ou=people,dc=example,dc=com
 * uid=user.3000,ou=people,dc=example,dc=com
 * uid=user.4000,ou=people,dc=example,dc=com
 * uid=user.100,ou=people,dc=example,dc=com
 * uid=user.1100,ou=people,dc=example,dc=com
 * uid=user.2100,ou=people,dc=example,dc=com
 * </pre>
 * <P>
 * At first, it may seem strange that user.1100 comes after user.1000 but it
 * becomes clear when considering the values in reverse byte order, since
 * 0011.resu is indeed greater than 0001.resu.
 * <P>
 * <H4>Index Databases</H4>
 * <P>
 * Index databases are used to efficiently process search requests.  The system
 * indexes, id2children and id2subtree, are dedicated to processing one-level
 * and subtree search scope respectively.  Then there are configurable
 * attribute indexes to process components of a search filter.  Each index
 * record maps a key to an Entry ID List.
 * <P>
 * <P>
 * <H5>Entry ID List</H5>
 * <P>
 * An entry ID list is a set of entry IDs, arranged in order of ID.  On disk,
 * the list is a concatenation of the 8-byte entry ID values, where the first
 * ID is the lowest.  The number of IDs in the list can be obtained by dividing
 * the total number of bytes by eight.
 * <P>
 * <P>
 * <H5>Index Entry Limit</H5>
 * <P>
 * In some cases, the number of entries indexed by a given key is so large
 * that the cost of maintaining the list during entry updates outweighs the
 * benefit of the list during search processing.  Each index therefore has
 * a configurable entry limit.  Whenever a list reaches the entry limit, it is
 * replaced with a zero length value to indicate that the list is no longer
 * maintained.
 * <P>
 * <P>
 * <H5>Children Index (id2children)</H5>
 * <P>
 * The children index is a system index which maps the ID of any non-leaf entry
 * to entry IDs of the immediate children of the entry. This index is used to
 * get the set of entries within the scope of a one-level search.
 * <P>
 * <P>
 * <H5>Subtree Index (id2subtree)</H5>
 * <P>
 * The subtree index is a system index which maps the ID of any non-leaf entry
 * to entry IDs of all descendants of the entry. This index is used to get the
 * set of entries within the scope of a subtree search.
 * <P>
 * <P>
 * <H5>Attribute Equality Index</H5>
 * <P>
 * An attribute equality index maps the value of an attribute to entry IDs of
 * all entries containing that attribute value. The database key is the
 * attribute value after it has been normalized by the equality matching rule
 * for that attribute.  This index is used to get the set of entries matching
 * an equality filter.
 * <P>
 * <P>
 * <H5>Attribute Presence Index</H5>
 * <P>
 * An attribute presence index contains a single record which has entry IDs
 * of all entries containing a value of the attribute. This index is used to get
 * the set of entries matching an attribute presence filter.
 * <P>
 * <P>
 * <H5>Attribute Substring Index</H5>
 * <P>
 * An attribute substring index maps a substring of an attribute value to entry
 * IDs of all entries containing that substring in one or more of its values of
 * the attribute.  This index is used to get a set of entries that are
 * candidates for matching a subtring filter.
 * <P>
 * The length of substrings in the index is configurable.  For example, let's
 * say the configured substring length is three, and there is an entry
 * containing the attribute value ABCDE.  The ID for this entry would be
 * indexed by the keys ABC BCD CDE DE E.  To find entries containing a short
 * substring such as DE, iterate through all keys with prefix DE.  To find
 * entries containing a longer substring such as BCDE, read keys BCD and CDE.
 * <P>
 * <P>
 * <H5>Attribute Ordering Index</H5>
 * <P>
 * An attribute ordering index is similar to an equality index in that it maps
 * the value of an attribute to entry IDs of all entries containing that
 * attribute value.  However, the values are normalized by the ordering matching
 * rule for the attribute rather than the equality matching rule, and the
 * btree key comparator is set to the ordering matching rule comparator.  This
 * index is used to get the set of entries matching inequality filters
 * (less-than-or-equal, greater-than-or-equal).
 *
 *
 */
package org.opends.server.backends.jeb;

