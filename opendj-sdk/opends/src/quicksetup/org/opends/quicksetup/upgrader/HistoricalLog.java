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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.quicksetup.util.Utils;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.*;

/**
 * Log of past upgrade/reversion events that is backed by the file
 * [install root]/history/log.
 */
public class HistoricalLog {

  private File file;

  /**
   * Creates a historical log backed by <code>file</code>.  If file
   * does not exist an attempt will be made to create it.
   * @param file File containing the historical record
   * @throws IOException if something goes wrong attempting to create
   * a new historical record file
   */
  public HistoricalLog(File file) throws IOException {
    this.file = file;
    if (!file.exists()) {
      Utils.createFile(file);
    }
  }

  /**
   * Gets a list of the historical records in the file.
   * @return List of HistoricalRecord
   * @throws IOException if there was an error reading the records file
   */
  public List<HistoricalRecord> getRecords() throws IOException {
    List<HistoricalRecord> records = new ArrayList<HistoricalRecord>();
    BufferedReader br = new BufferedReader(new FileReader(file));
    String s;
    while (null != (s = br.readLine())) {
      records.add(HistoricalRecord.fromString(s));
    }
    return Collections.unmodifiableList(records);
  }

  /**
   * Creates a new historical log record and appends a new log record to the
   * log.  A new operation ID is generated and returned so that future calls
   * can use the same ID.
   * @param from current version
   * @param to version to upgrade to
   * @param status of the upgrade
   * @param note optional string with additional information
   * @return Long operation ID that can be used in writing future logs
   * @throws IOException if there is a problem appending the log to the file
   */
  public Long append(Integer from, Integer to,
                     HistoricalRecord.Status status, String note)
          throws IOException
  {
    HistoricalRecord record = new HistoricalRecord(from, to, status, note);
    Long id = record.getOperationId();
    append(record);
    return id;
  }

  /**
   * Creates a new historical log record and appends a new log record to the
   * log.
   * @param id Long ID obtained from a call to
            {@link org.opends.quicksetup.upgrader.HistoricalLog#
            append(Integer, Integer,
            org.opends.quicksetup.upgrader.HistoricalRecord.Status)}
   * @param from current version
   * @param to version to upgrade to
   * @param status of the upgrade
   * @param note optional string with additional information
   * @throws IOException if there is a problem appending the log to the file
   */
  public void append(Long id, Integer from, Integer to,
                     HistoricalRecord.Status status, String note)
          throws IOException
  {
    HistoricalRecord record = new HistoricalRecord(id, from, to, status, note);
    append(record);
  }

  /**
   * Appends a historical record to the log.
   * @param record to append to the log file
   * @throws IOException if there is a problem appending the record to the file
   */
  private void append(HistoricalRecord record) throws IOException {
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(file, true));
      bw.write(record.toString());
      bw.newLine();
      bw.flush();
    } finally {
      if (bw != null) {
        try {
          bw.close();
        } catch (IOException ioe2) {
        // do nothing;
        }
      }
    }
  }

}
