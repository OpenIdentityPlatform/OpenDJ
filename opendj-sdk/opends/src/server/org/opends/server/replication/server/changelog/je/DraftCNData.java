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

  private String value;
  private String baseDN;
  private CSN csn;

  /**
   * Creates a record to be stored in the DraftCNDB.
   * @param previousCookie The previous cookie.
   * @param baseDN The baseDN (domain DN).
   * @param csn The replication CSN.
   */
  public DraftCNData(String previousCookie, String baseDN, CSN csn)
  {
    String record =
        previousCookie + FIELD_SEPARATOR + baseDN + FIELD_SEPARATOR + csn;
    setData(getBytes(record));
  }

  /**
   * Creates a record to be stored in the DraftCNDB from the provided byte[].
   * @param data the provided byte[].
   * @throws ChangelogException a.
   */
  public DraftCNData(byte[] data) throws ChangelogException
  {
    decodeData(data);
  }

  /**
   * Decode a record into fields.
   * @param data the provided byte array.
   * @throws ChangelogException when a problem occurs.
   */
  public void decodeData(byte[] data) throws ChangelogException
  {
    try
    {
      String stringData = new String(data, "UTF-8");

      String[] str = stringData.split(FIELD_SEPARATOR, 3);
      value = str[0];
      baseDN = str[1];
      csn = new CSN(str[2]);
    }
    catch (UnsupportedEncodingException e)
    {
      // should never happens
      // TODO: i18n
      throw new ChangelogException(Message.raw("need UTF-8 support"));
    }
  }

  /**
   * Getter for the value.
   *
   * @return the value.
   * @throws ChangelogException when a problem occurs.
   */
  public String getValue() throws ChangelogException
  {
    if (value == null)
      decodeData(getData());
    return this.value;
  }

  /**
   * Getter for the service ID.
   *
   * @return The baseDN
   * @throws ChangelogException when a problem occurs.
   */
  public String getBaseDN() throws ChangelogException
  {
    if (value == null)
      decodeData(getData());
    return this.baseDN;
  }

  /**
   * Getter for the replication CSN.
   *
   * @return the replication CSN.
   * @throws ChangelogException
   *           when a problem occurs.
   */
  public CSN getCSN() throws ChangelogException
  {
    if (value == null)
      decodeData(getData());
    return this.csn;
  }

  /**
   * Provide a string representation of these data.
   * @return the string representation of these data.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }

  /**
   * Dump a string representation of these data into the provided buffer.
   * @param buffer the provided buffer.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("DraftCNData : [value=").append(value);
    buffer.append("] [serviceID=").append(baseDN);
    buffer.append("] [csn=").append(csn).append("]");
  }
}
