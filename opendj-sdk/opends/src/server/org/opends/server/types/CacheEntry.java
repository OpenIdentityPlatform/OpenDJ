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
package org.opends.server.types;



import org.opends.server.api.Backend;



/**
 * This class defines a Directory Server cache entry, which is simply
 * used to store an entry with its associated backend and entry ID.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true,
     notes="This should only be used within a backend")
public final class CacheEntry
{
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
    return entry;
  }



  /**
   * Specifies the entry for this cache entry.
   *
   * @param  entry  The entry for this cache entry.
   */
  public void setEntry(Entry entry)
  {
    this.entry = entry;
  }



  /**
   * Retrieves the backend for this cache entry.
   *
   * @return  The backend for this cache entry.
   */
  public Backend getBackend()
  {
    return backend;
  }



  /**
   * Specifies the backend for this cache entry.
   *
   * @param  backend  The backend for this cache entry.
   */
  public void setBackend(Backend backend)
  {
    this.backend = backend;
  }



  /**
   * Retrieves the entry ID for this cache entry.
   *
   * @return  The entry ID for this cache entry.
   */
  public long getEntryID()
  {
    return entryID;
  }



  /**
   * Specifies the entry ID for this cache entry.
   *
   * @param  entryID  The entryID for this cache entry.
   */
  public void setEntryID(long entryID)
  {
    this.entryID = entryID;
  }



  /**
   * Retrieves the DN for this cache entry.
   *
   * @return  The DN for this cache entry.
   */
  public DN getDN()
  {
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

