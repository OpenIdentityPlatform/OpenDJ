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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2010-2015 ForgeRock AS.
 */
package org.opends.server.replication.server.changelog.je;

import static org.opends.server.util.StaticUtils.*;

import java.io.UnsupportedEncodingException;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import com.sleepycat.je.DatabaseEntry;

/**
 * Subclass of DatabaseEntry used for data stored in the DraftCNDB.
 */
public class DraftCNData extends DatabaseEntry
{
  private static final String FIELD_SEPARATOR = "!";
  private static final String EMPTY_STRING_PREVIOUS_COOKIE = "";
  private static final long serialVersionUID = 1L;

  private long changeNumber;
  private ChangeNumberIndexRecord record;

  /**
   * Creates a record to be stored in the DraftCNDB.
   *
   * @param changeNumber
   *          the change number
   * @param baseDN
   *          The baseDN (domain DN)
   * @param csn
   *          The replication CSN
   */
  public DraftCNData(long changeNumber, DN baseDN, CSN csn)
  {
    this.changeNumber = changeNumber;
    // Although the previous cookie is not used any more, we need
    // to keep it in database for compatibility with previous versions
    String record = EMPTY_STRING_PREVIOUS_COOKIE + FIELD_SEPARATOR + baseDN + FIELD_SEPARATOR + csn;
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
   * Decode and returns a {@link ChangeNumberIndexRecord}.
   *
   * @param changeNumber
   * @param data
   *          the provided byte array.
   * @return the decoded {@link ChangeNumberIndexRecord}
   * @throws ChangelogException
   *           when a problem occurs.
   */
  private ChangeNumberIndexRecord decodeData(long changeNumber, byte[] data)
      throws ChangelogException
  {
    try
    {
      // Although the previous cookie is not used any more, we need
      // to keep it in database for compatibility with previous versions
      String stringData = new String(data, "UTF-8");
      String[] str = stringData.split(FIELD_SEPARATOR, 3);
      // str[0] contains previous cookie and is ignored
      final DN baseDN = DN.valueOf(str[1]);
      final CSN csn = new CSN(str[2]);
      return new ChangeNumberIndexRecord(changeNumber, baseDN, csn);
    }
    catch (UnsupportedEncodingException e)
    {
      // should never happens
      // TODO: i18n
      throw new ChangelogException(LocalizableMessage.raw("need UTF-8 support"));
    }
    catch (DirectoryException e)
    {
      throw new ChangelogException(e);
    }
  }

  /**
   * Getter for the decoded record.
   *
   * @return the {@link ChangeNumberIndexRecord} record.
   * @throws ChangelogException
   *           when a problem occurs.
   */
  public ChangeNumberIndexRecord getRecord() throws ChangelogException
  {
    if (record == null)
    {
      record = decodeData(changeNumber, getData());
    }
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
