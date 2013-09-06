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
 *      Portions Copyright 2010-2013 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.je;

import java.io.UnsupportedEncodingException;

import org.opends.messages.Message;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.CNIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;

import com.sleepycat.je.DatabaseEntry;

import static org.opends.server.util.StaticUtils.*;

/**
 * SuperClass of DatabaseEntry used for data stored in the DraftCNDB.
 */
public class DraftCNData extends DatabaseEntry
{
  private static final String FIELD_SEPARATOR = "!";

  private static final long serialVersionUID = 1L;

  private long changeNumber;
  private CNIndexRecord record;

  /**
   * Creates a record to be stored in the DraftCNDB.
   *
   * @param changeNumber
   *          the change number
   * @param previousCookie
   *          The previous cookie
   * @param baseDN
   *          The baseDN (domain DN)
   * @param csn
   *          The replication CSN
   */
  public DraftCNData(long changeNumber, String previousCookie, String baseDN,
      CSN csn)
  {
    this.changeNumber = changeNumber;
    String record =
        previousCookie + FIELD_SEPARATOR + baseDN + FIELD_SEPARATOR + csn;
    setData(getBytes(record));
  }

  /**
   * Creates a record to be stored in the DraftCNDB from the provided byte[].
   *
   * @param changeNumber
   *          the change number
   * @param data
   *          the provided byte[]
   * @throws ChangelogException
   *           if a database problem occurred
   */
  public DraftCNData(long changeNumber, byte[] data) throws ChangelogException
  {
    this.changeNumber = changeNumber;
    this.record = decodeData(changeNumber, data);
  }

  /**
   * Decode and returns a {@link CNIndexRecord}.
   *
   * @param changeNumber
   * @param data
   *          the provided byte array.
   * @return the decoded {@link CNIndexRecord}
   * @throws ChangelogException
   *           when a problem occurs.
   */
  private CNIndexRecord decodeData(long changeNumber, byte[] data)
      throws ChangelogException
  {
    try
    {
      String stringData = new String(data, "UTF-8");
      String[] str = stringData.split(FIELD_SEPARATOR, 3);
      return new CNIndexRecord(changeNumber, str[0], str[1], new CSN(str[2]));
    }
    catch (UnsupportedEncodingException e)
    {
      // should never happens
      // TODO: i18n
      throw new ChangelogException(Message.raw("need UTF-8 support"));
    }
  }

  /**
   * Getter for the decoded record.
   *
   * @return the {@link CNIndexRecord} record.
   * @throws ChangelogException
   *           when a problem occurs.
   */
  public CNIndexRecord getRecord() throws ChangelogException
  {
    if (record == null)
      record = decodeData(changeNumber, getData());
    return record;
  }

  /**
   * Provide a string representation of these data.
   * @return the string representation of these data.
   */
  @Override
  public String toString()
  {
    return "DraftCNData : [" + record + "]";
  }

}
