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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.file;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.replication.server.changelog.api.ChangelogException;

/**
 * Parser of a log record.
 * <p>
 * The parser allows to convert from a object to its binary representation
 * and to convert back from the binary representation to the object.
 *
 * @param <K>
 *          Type of the key of the record.
 * @param <V>
 *          Type of the value of the record.
 */
interface RecordParser<K, V>
{
  /**
   * Decode a record from the provided byte array.
   * <p>
   * The record is expected to have been encoded using the {@code writeRecord()}
   * method.
   *
   * @param data
   *          The raw data to read the record from.
   * @return the decoded record, or {@code null} if there is no more record to
   *         read, or only an incomplete record
   * @throws DecodingException
   *           If an error occurs while decoding the record.
   */
  Record<K, V> decodeRecord(ByteString data) throws DecodingException;

  /**
   * Encode the provided record to a byte array.
   * <p>
   * The returned array is intended to be stored as provided in the log file.
   *
   * @param record
   *          The record to encode.
   * @return the bytes array representing the (key,value) record
   */
  ByteString encodeRecord(Record<K, V> record);

  /**
   * Read the key from the provided string.
   *
   * @param key
   *          The string representation of key, suitable for use in a filename,
   *          as written by the {@code encodeKeyToString()} method.
   * @return the key
   * @throws ChangelogException
   *           If key can't be read from the string.
   */
  K decodeKeyFromString(String key) throws ChangelogException;

  /**
   * Returns the provided key as a string that is suitable to be used in a
   * filename.
   *
   * @param key
   *          The key of a record.
   * @return a string encoding the key, unambiguously decodable to the original
   *         key, and suitable for use in a filename. The string should contain
   *         only ASCII characters and no space.
   */
  String encodeKeyToString(K key);

  /**
   * Returns a key that is guaranted to be always higher than any other key.
   *
   * @return the highest possible key
   */
  K getMaxKey();

}
