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
package org.opends.server.replication.server;

import static org.opends.server.util.StaticUtils.*;

import java.io.UnsupportedEncodingException;

import org.opends.messages.Message;
import org.opends.server.replication.common.ChangeNumber;

import com.sleepycat.je.DatabaseEntry;

/**
 * SuperClass of DatabaseEntry used for data stored in the DraftCNDB.
 */
public class DraftCNData extends DatabaseEntry
{
  private static final String FIELD_SEPARATOR = "!";

  private static final long serialVersionUID = 1L;

  private String value;
  private String baseDN;
  private ChangeNumber changeNumber;

  /**
   * Creates a record to be stored in the DraftCNDB.
   * @param value The value (cookie).
   * @param baseDN The baseDN (domain DN).
   * @param changeNumber The replication change number.
   */
  public DraftCNData(String value, String baseDN, ChangeNumber changeNumber)
  {
    String record = value
                   + FIELD_SEPARATOR + baseDN
                   + FIELD_SEPARATOR + changeNumber;
    setData(getBytes(record));
  }

  /**
   * Creates a record to be stored in the DraftCNDB from the provided byte[].
   * @param data the provided byte[].
   * @throws Exception a.
   */
  public DraftCNData(byte[] data) throws Exception
  {
    decodeData(data);
  }

  /**
   * Decode a record into fields.
   * @param data the provided byte array.
   * @throws Exception when a problem occurs.
   */
  public void decodeData(byte[] data)
  throws Exception
  {
    try
    {
      String stringData = new String(data, "UTF-8");

      String[] str = stringData.split(FIELD_SEPARATOR, 3);
      value = str[0];
      baseDN = str[1];
      changeNumber = new ChangeNumber(str[2]);
    }
    catch (UnsupportedEncodingException e)
    {
      // should never happens
      // TODO: i18n
      throw new ReplicationDBException(Message.raw("need UTF-8 support"));
    }
  }

  /**
   * Getter for the value.
   * @return the value.
   * @throws Exception when a problem occurs.
   */
  public String getValue()
  throws Exception
  {
    if (value == null)
      this.decodeData(this.getData());
    return this.value;
  }

  /**
   * Getter for the service ID.
   * @return The baseDN
   * @throws Exception when a problem occurs.
   */
  public String getBaseDN()
  throws Exception
  {
    if (value == null)
      this.decodeData(this.getData());
    return this.baseDN;
  }

  /**
   * Getter for the replication change number.
   * @return the replication change number.
   * @throws Exception when a problem occurs.
   */
  public ChangeNumber getChangeNumber()
  throws Exception
  {
    if (value == null)
      this.decodeData(this.getData());
    return this.changeNumber;
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
    buffer.append("] [changeNumber=").append(changeNumber).append("]");
  }
}
