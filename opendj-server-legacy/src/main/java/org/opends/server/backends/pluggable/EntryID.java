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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import org.forgerock.opendj.ldap.ByteString;

/**
 * An integer identifier assigned to each entry in the backend.
 * An entry ID is implemented by this class as a long.
 * There are static methods to assign monotonically increasing entry IDs,
 * starting from 1.
 */
class EntryID implements Comparable<EntryID>
{
  /** The identifier integer value. */
  private final long id;
  /** The value in tree format, created when necessary. */
  private ByteString value;

  /**
   * Create a new entry ID object from a given long value.
   * @param id The long value of the ID.
   */
  EntryID(long id)
  {
    this.id = id;
  }

  /**
   * Create a new entry ID object from a value in tree format.
   * @param value The tree value of the ID.
   */
  EntryID(ByteString value)
  {
    this.value = value;
    id = value.toLong();
  }

  /**
   * Get the value of the entry ID as a long.
   * @return The entry ID.
   */
  long longValue()
  {
    return id;
  }

  /**
   * Get the value of the ID in tree format.
   * @return The value of the ID in tree format.
   */
  ByteString toByteString()
  {
    if (value == null)
    {
      value = ByteString.valueOfLong(id);
    }
    return value;
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
  @Override
  public int compareTo(EntryID that) throws ClassCastException
  {
    final long result = this.id - that.id;
    if (result < 0)
    {
      return -1;
    }
    else if (result > 0)
    {
      return 1;
    }
    return 0;
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
  @Override
  public boolean equals(Object that)
  {
    if (this == that)
    {
      return true;
    }
    if (!(that instanceof EntryID))
    {
      return false;
    }
    return this.id == ((EntryID) that).id;
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
  @Override
  public int hashCode()
  {
    return (int) id;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  @Override
  public String toString()
  {
    return Long.toString(id);
  }
}
