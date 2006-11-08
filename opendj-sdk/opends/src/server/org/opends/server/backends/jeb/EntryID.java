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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import com.sleepycat.je.DatabaseEntry;

/**
 * An integer identifier assigned to each entry in the JE backend.
 * An entry ID is implemented by this class as a long.
 * There are static methods to assign monotonically increasing entry IDs,
 * starting from 1.
 */
public class EntryID implements Comparable<EntryID>
{
  /**
   * The identifier integer value.
   */
  private final Long id;

  /**
   * The value in database format, created when necessary.
   */
  private DatabaseEntry data = null;

  /**
   * Create a new entry ID object from a given long value.
   * @param id The long value of the ID.
   */
  public EntryID(long id)
  {
    this.id = id;
  }

  /**
   * Create a new entry ID object from a given Long value.
   * @param id the Long value of the ID.
   */
  public EntryID(Long id)
  {
    this.id = id;
  }

  /**
   * Create a new entry ID object from a value in database format.
   * @param databaseEntry The database value of the ID.
   */
  public EntryID(DatabaseEntry databaseEntry)
  {
    data = databaseEntry;
    id = JebFormat.entryIDFromDatabase(data.getData());
  }

  /**
   * Get the value of the entry ID as a long.
   * @return The entry ID.
   */
  public long longValue()
  {
    return id;
  }

  /**
   * Get the value of the ID in database format.
   * @return The value of the ID in database format.
   */
  public DatabaseEntry getDatabaseEntry()
  {
    if (data == null)
    {
      data = new DatabaseEntry();
      data.setData(JebFormat.entryIDToDatabase(id));
    }
    return data;
  }

  /**
   * Compares this object with the specified object for order.  Returns a
   * negative integer, zero, or a positive integer as this object is less
   * than, equal to, or greater than the specified object.<p>
   * <p/>
   *
   * @param that the Object to be compared.
   * @return a negative integer, zero, or a positive integer as this object
   *         is less than, equal to, or greater than the specified object.
   * @throws ClassCastException if the specified object's type prevents it
   *                            from being compared to this Object.
   */
  public int compareTo(EntryID that) throws ClassCastException
  {
    return this.id.compareTo(that.id);
  }

  /**
   * Indicates whether some other object is "equal to" this one.
   *
   * @param   that   the reference object with which to compare.
   * @return  <code>true</code> if this object is the same as the obj
   *          argument; <code>false</code> otherwise.
   * @see     #hashCode()
   * @see     java.util.Hashtable
   */
  @Override public boolean equals(Object that)
  {
    if (that == null)
    {
      return false;
    }
    if (this == that)
    {
      return true;
    }
    if (!(that instanceof EntryID))
    {
      return false;
    }
    return this.id.equals(((EntryID)that).id);
  }

  /**
   * Returns a hash code value for the object. This method is
   * supported for the benefit of hashtables such as those provided by
   * <code>java.util.Hashtable</code>.
   *
   * @return  a hash code value for this object.
   * @see     java.lang.Object#equals(java.lang.Object)
   * @see     java.util.Hashtable
   */
  @Override public int hashCode()
  {
    return (int)id.longValue();
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  public String toString()
  {
    return id.toString();
  }
}
