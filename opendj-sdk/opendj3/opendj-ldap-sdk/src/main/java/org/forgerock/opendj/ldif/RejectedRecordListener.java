/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldif;



import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;



/**
 * A listener interface which is notified whenever records are skipped,
 * malformed, or fail schema validation.
 */
public interface RejectedRecordListener
{
  /**
   * The default handler which ignores skipped records but which terminates
   * processing by throwing a {@code DecodeException} as soon as a record is
   * found to be malformed or rejected due to a schema validation failure.
   */
  public static final RejectedRecordListener FAIL_FAST = new RejectedRecordListener()
  {

    @Override
    public void handleMalformedRecord(final long lineNumber,
        final List<String> ldifRecord, final LocalizableMessage reason)
        throws DecodeException
    {
      // Fail fast.
      throw DecodeException.error(reason);
    }



    @Override
    public void handleSchemaValidationFailure(final long lineNumber,
        final List<String> ldifRecord, final List<LocalizableMessage> reasons)
        throws DecodeException
    {
      // Fail fast - just use first message.
      throw DecodeException.error(reasons.get(0));
    }



    @Override
    public void handleSkippedRecord(final long lineNumber,
        final List<String> ldifRecord, final LocalizableMessage reason)
        throws DecodeException
    {
      // Ignore skipped records.
    }
  };



  /**
   * Invoked when a record was rejected because it was malformed in some way and
   * could not be decoded.
   *
   * @param lineNumber
   *          The line number within the source location in which the malformed
   *          record is located, if known, otherwise {@code -1}.
   * @param ldifRecord
   *          An LDIF representation of the malformed record.
   * @param reason
   *          The reason why the record is malformed.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleMalformedRecord(long lineNumber, List<String> ldifRecord,
      LocalizableMessage reason) throws DecodeException;



  /**
   * Invoked when a record was rejected because it does not conform to the
   * schema and schema validation is enabled.
   *
   * @param lineNumber
   *          The line number within the source location in which the rejected
   *          record is located, if known, otherwise {@code -1}.
   * @param ldifRecord
   *          An LDIF representation of the record which failed schema
   *          validation.
   * @param reasons
   *          The reasons why the record failed schema validation.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleSchemaValidationFailure(long lineNumber, List<String> ldifRecord,
      List<LocalizableMessage> reasons) throws DecodeException;



  /**
   * Invoked when a record was skipped because it did not match filter criteria
   * defined by the reader.
   *
   * @param lineNumber
   *          The line number within the source location in which the skipped
   *          record is located, if known, otherwise {@code -1}.
   * @param ldifRecord
   *          An LDIF representation of the skipped record.
   * @param reason
   *          The reason why the record was skipped.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleSkippedRecord(long lineNumber, List<String> ldifRecord,
      LocalizableMessage reason) throws DecodeException;

}
