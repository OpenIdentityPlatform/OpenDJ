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
package org.opends.server.types;



import org.opends.server.api.Backend;

import static org.opends.server.loggers.Debug.*;



/**
 * This class defines a Directory Server cache entry, which is simply
 * used to store an entry with its associated backend and entry ID.
 */
public class CacheEntry
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.CacheEntry";



  // The backend with which this cache entry is associated.
  private Backend backend;

  // The entry itself.
  private Entry entry;

  // The entry ID for the entry within the backend.
  private long entryID;



  /**
   * Creates a new cache entry with the provided information.
   *
   * @param  entry    The entry for this cache entry.
   * @param  backend  The backend for this cache entry.
   * @param  entryID  The entry ID for this cache entry.
   */
  public CacheEntry(Entry entry, Backend backend, long entryID)
  {
    assert debugConstructor(CLASS_NAME, String.valueOf(entry),
                            String.valueOf(backend),
                            String.valueOf(entryID));

    this.entry   = entry;
    this.backend = backend;
    this.entryID = entryID;
  }



  /**
   * Retrieves the entry for this cache entry.
   *
   * @return  The entry for this cache entry.
   */
  public Entry getEntry()
  {
    assert debugEnter(CLASS_NAME, "getEntry");

    return entry;
  }



  /**
   * Specifies the entry for this cache entry.
   *
   * @param  entry  The entry for this cache entry.
   */
  public void setEntry(Entry entry)
  {
    assert debugEnter(CLASS_NAME, "setEntry", String.valueOf(entry));

    this.entry = entry;
  }



  /**
   * Retrieves the backend for this cache entry.
   *
   * @return  The backend for this cache entry.
   */
  public Backend getBackend()
  {
    assert debugEnter(CLASS_NAME, "getBackend");

    return backend;
  }



  /**
   * Specifies the backend for this cache entry.
   *
   * @param  backend  The backend for this cache entry.
   */
  public void setBackend(Backend backend)
  {
    assert debugEnter(CLASS_NAME, "setBackend",
                      String.valueOf(backend));

    this.backend = backend;
  }



  /**
   * Retrieves the entry ID for this cache entry.
   *
   * @return  The entry ID for this cache entry.
   */
  public long getEntryID()
  {
    assert debugEnter(CLASS_NAME, "getEntryID");

    return entryID;
  }



  /**
   * Specifies the entry ID for this cache entry.
   *
   * @param  entryID  The entryID for this cache entry.
   */
  public void setEntryID(long entryID)
  {
    assert debugEnter(CLASS_NAME, "setEntryID");

    this.entryID = entryID;
  }



  /**
   * Retrieves the DN for this cache entry.
   *
   * @return  The DN for this cache entry.
   */
  public DN getDN()
  {
    assert debugEnter(CLASS_NAME, "getDN");

    return entry.getDN();
  }



  /**
   * Retrieves the hash code for this cache entry.  It will be the
   * integer representation of the entry ID.
   *
   * @return  The hash code for this cache entry.
   */
  public int hashCode()
  {
    assert debugEnter(CLASS_NAME, "hashCode");

    return (int) entryID;
  }



  /**
   * Indicates whether this cache entry is equal to the provided \
   * object.  They will be considered equal if the provided object is
   * a cache entry with the same entry and entry ID.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this cache entry, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals", String.valueOf(o));

    if (o == null)
    {
      return false;
    }

    if (o == this)
    {
      return true;
    }

    if (! (o instanceof CacheEntry))
    {
      return false;
    }

    CacheEntry e = (CacheEntry) o;
    return ((e.entryID == entryID) && (e.entry.equals(entry)));
  }
}

