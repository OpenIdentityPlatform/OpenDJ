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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.backends.jeb.importLDIF;

import org.opends.server.backends.jeb.EntryID;

/**
 * Interface defining and import ID set.
 */
public interface ImportIDSet {

  /**
   * Add an entry ID to the set.
   *
   * @param entryID The entry ID to add.
   * @param entryLimit The entry limit.
   * @param maintainCount Maintain count of IDs if in undefined mode.
   */
  public void
  addEntryID(EntryID entryID, int entryLimit, boolean maintainCount);

  /**
   * Return if a  set is defined or not.
   *
   * @return <CODE>True</CODE> if a set is defined.
   */
  public boolean isDefined();

  /**
   * Return the memory size of a set.
   *
   * @return The sets current memory size.
   */
  public int getMemorySize();

  /**
   * Convert a set to a byte array suitable for saving to DB.
   *
   * @return A byte array representing the set.
   */
  public byte[] toDatabase();

  /**
   * Return the size of the set.
   *
   * @return The size of the ID set.
   */
  public int size();

  /**
   * Merge a byte array read from DB with a ID set.
   *
   * @param dbBytes The byte array read from DB.
   * @param bufImportIDSet The import ID set to merge.
   * @param entryLimit The entry limit.
   * @param maintainCount Maintain count of iDs if in undefined mode.
   * @return <CODE>True</CODE> if the merged set is undefined.
   */
  public boolean merge(byte[] dbBytes, ImportIDSet bufImportIDSet,
                       int entryLimit, boolean maintainCount);

  /**
   * Set the import ID set to the undefined state.
   */
  public void setUndefined();


  /**
   * Return the undefined size.
   *
   * @return The undefined count.
   */
  public long getUndefinedSize();
}
