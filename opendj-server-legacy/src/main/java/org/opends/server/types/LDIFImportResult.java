/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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
  /** The total number of entries read during the import. */
  private final long entriesRead;
  /** The total number of entries rejected during the import. */
  private final long entriesRejected;
  /** The total number of entries skipped during the import. */
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
  @Override
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
  private void toString(StringBuilder buffer)
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
