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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import java.util.StringTokenizer;
import java.util.EnumSet;
import java.util.Set;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.text.SimpleDateFormat;

/**
 * A record in the historical log stored in [install root]/history/log.
 */
public class HistoricalRecord {

  static private final Logger LOG =
          Logger.getLogger(HistoricalRecord.class.getName());

  //--------------------------------------------------//
  // Since these are internationalized, logs that are //
  // moved from one locale to another may not be      //
  // readable programmatically.                       //
  //--------------------------------------------------//

  static private Message OPERATION = INFO_UPGRADE_LOG_FIELD_OP.get();

  static private Message TIME = INFO_UPGRADE_LOG_FIELD_TIME.get();

  static private Message FROM = INFO_UPGRADE_LOG_FIELD_FROM.get();

  static private Message TO = INFO_UPGRADE_LOG_FIELD_TO.get();

  static private Message STATUS = INFO_UPGRADE_LOG_FIELD_STATUS.get();

  static private Message NOTE = INFO_UPGRADE_LOG_FIELD_NOTE.get();

  static private String SEPARATOR = " ";

  static private String DATE_FORMAT = "yyyyMMddHHmmss";

  /**
   * State of an upgrade.
   */
  public enum Status {

    /** Operation has started. */
    STARTED(INFO_UPGRADE_LOG_STATUS_STARTED.get()),

    /** Operation completed successfully. */
    SUCCESS(INFO_UPGRADE_LOG_STATUS_SUCCESS.get()),

    /** Operation failed. */
    FAILURE(INFO_UPGRADE_LOG_STATUS_FAILURE.get()),

    /** Operation was canceled. */
    CANCEL(INFO_UPGRADE_LOG_STATUS_CANCEL.get());

    private Message representation;

    /**
     * Creates a State from a String.
     * @param s string representation of a state
     * @return Status created from <code>s</code>
     */
    static public Status fromString(String s) {
      Status retOc = null;
      Set<Status> all = EnumSet.allOf(Status.class);
      for (Status oc : all) {
        if (oc.toString().equals(s)) {
          retOc = oc;
        }
      }
      return retOc;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
      return String.valueOf(representation);
    }

    private Status(Message representation) {
      this.representation = representation;
    }

  }

  /**
   * Creates a historical log record from its string representation.
   * @param s string representation
   * @return HistoricalRecord created from the string
   * @throws IllegalArgumentException if the string is misformatted
   */
  static public HistoricalRecord fromString(String s)
          throws IllegalArgumentException {
    Long operationid = null;
    BuildInformation from = null;
    BuildInformation to = null;
    Status outcome = null;
    Date date = null;
    String note = null;
    Exception creationError = null;
    try {
      StringTokenizer st = new StringTokenizer(s, SEPARATOR);

      String token = st.nextToken();
      String operationIdString = token.substring(OPERATION.length());
      operationid = Long.parseLong(operationIdString);

      token = st.nextToken();
      String timeString = token.substring(TIME.length());
      date = new SimpleDateFormat(DATE_FORMAT).parse(timeString);

      token = st.nextToken();
      String fromString = token.substring(FROM.length());
      from = BuildInformation.fromBuildString(fromString);

      token = st.nextToken();
      String toString = token.substring(TO.length());
      to = BuildInformation.fromBuildString(toString);

      token = st.nextToken();
      String outcomeString = token.substring(STATUS.length());
      outcome = Status.fromString(outcomeString);

      if (st.hasMoreTokens()) {
        token = st.nextToken("");
        if (token != null) {
          note = token.substring(NOTE.length());
        }
      }
    } catch (Exception e) {
      // There was a problem creating the record.  Log the error and
      // create the record with what we have already accumulated.
      LOG.log(Level.INFO, "error creating historical log record", e);
      creationError = e;
    }
    return new HistoricalRecord(operationid, date, from,
            to, outcome, note, creationError);
  }

  private Long operationId;

  private BuildInformation from;

  private BuildInformation to;

  private Status status;

  private Date date;

  private String note;

  /** true indicates there were not errors creating this record. */
  private Exception creationError;

  /**
   * Creates a new historical record using the current time and generating.
   * a new operation id
   * @param from current version
   * @param to version to upgrade to
   * @param status of the upgrade
   * @param note containing details of status; can be null
   */
  public HistoricalRecord(BuildInformation from,
                          BuildInformation to,
                          Status status, String note) {
    this.from = from;
    this.to = to;
    this.status = status;
    this.date = new Date();
    this.operationId = date.getTime();
    this.note = note;
  }

  /**
   * Creates a new historical record using the current time.
   * @param operationId obtained from a previously created HistoricalRecord
   * @param from current version
   * @param to version to upgrade to
   * @param status of the upgrade
   * @param note containing details of status; can be null
   */
  public HistoricalRecord(Long operationId,
                          BuildInformation from,
                          BuildInformation to,
                          Status status, String note) {
    this.from = from;
    this.to = to;
    this.status = status;
    this.date = new Date();
    this.operationId = operationId;
    this.note = note;
  }

  /**
   * Creates a new historical record using the current time.
   * @param operationId obtained from a previously created HistoricalRecord
   * @param from current version
   * @param to version to upgrade to
   * @param status of the upgrade
   * @param note containing details of status; can be null
   * @param creationError Exception that occurred while this record was
   * being created
   * @param date of this operation
   */
  private HistoricalRecord(Long operationId, Date date, BuildInformation from,
                          BuildInformation to, Status status, String note,
                          Exception creationError) {
    this.operationId = operationId;
    this.from = from;
    this.to = to;
    this.status = status;
    this.date = date;
    this.note = note;
    this.creationError = creationError;
  }

  /**
   * Gets the operation ID associated with this record.
   * @return Long ID of this operation
   */
  public Long getOperationId() {
    return operationId;
  }

  /**
   * Gets the Date the record was created.
   * @return Date of the record
   */
  public Date getDate() {
    return this.date;
  }

  /**
   * Gets the Integer representing the SVN rev ID of the current installation.
   * @return Integer version ID
   */
  public BuildInformation getFromVersion() {
    return this.from;
  }

  /**
   * Gets the Integer representing the SVN rev ID of the installation being
   * upgraded to.
   * @return Integer version ID
   */
  public BuildInformation getToVersion() {
    return this.to;
  }

  /**
   * {@inheritDoc}
   */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(OPERATION);
    sb.append(operationId != null ? operationId : INFO_GENERAL_UNSET.get());
    sb.append(SEPARATOR);
    sb.append(TIME);
    sb.append(new SimpleDateFormat(DATE_FORMAT).format(date));
    sb.append(SEPARATOR);
    sb.append(FROM);
    sb.append(from != null ? from.getBuildString() : INFO_GENERAL_UNSET.get());
    sb.append(SEPARATOR);
    sb.append(TO);
    sb.append(to != null ? to.getBuildString() : INFO_GENERAL_UNSET.get());
    sb.append(SEPARATOR);
    sb.append(STATUS);
    sb.append(status);
    if (note != null) {
      sb.append(SEPARATOR)
      .append(NOTE)
      .append(note);
    }
    return sb.toString();
  }

}
