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
package org.opends.server.types;



/**
 * This class defines a data structure for providing information about
 * the state of a completed LDIF import, including the total number of
 * entries read, skipped, and rejected.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class LDIFImportResult
{
  // The total number of entries read during the import.
  private final long entriesRead;

  // The total number of entries rejected during the import.
  private final long entriesRejected;

  // The total number of entries skipped during the import.
  private final long entriesSkipped;



  /**
   * Creates a new LDIF import result object with the provided
   * information.
   *
   * @param  entriesRead      The total number of entries read
   *                          during the import, including those that
   *                          were later rejected or skipped.
   * @param  entriesRejected  The total number of entries rejected
   *                          during the import.
   * @param  entriesSkipped   The total number of entries skipped
   *                          during the import.
   */
  public LDIFImportResult(long entriesRead, long entriesRejected,
                          long entriesSkipped)
  {
    this.entriesRead     = entriesRead;
    this.entriesRejected = entriesRejected;
    this.entriesSkipped  = entriesSkipped;
  }



  /**
   * Retrieves the total number of entries read during the import,
   * including those that were later rejected or skipped.
   *
   * @return  The total number of entries read during the import,
   *          including those that were later rejected or skipped.
   */
  public long getEntriesRead()
  {
    return entriesRead;
  }



  /**
   * Retrieves the total number of entries that were successfully
   * imported.
   *
   * @return  The total number of entries that were successfully
   *          imported.
   */
  public long getEntriesImported()
  {
    return entriesRead - entriesRejected - entriesSkipped;
  }



  /**
   * Retrieves the total number of entries rejected during the import.
   *
   * @return  The total number of entries rejected during the import.
   */
  public long getEntriesRejected()
  {
    return entriesRejected;
  }



  /**
   * Retrieves the total number of entries skipped during the import.
   *
   * @return  The total number of entries skipped during the import.
   */
  public long getEntriesSkipped()
  {
    return entriesSkipped;
  }



  /**
   * Retrieves a string representation of this LDIF import result
   * object.
   *
   * @return  A string representation of this LDIF import result
   *          object.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this LDIF import result object
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("LDIFImportResult(entriesRead=");
    buffer.append(entriesRead);
    buffer.append(", entriesRejected=");
    buffer.append(entriesRejected);
    buffer.append(", entriesSkipped=");
    buffer.append(entriesSkipped);
    buffer.append(")");
  }
}

