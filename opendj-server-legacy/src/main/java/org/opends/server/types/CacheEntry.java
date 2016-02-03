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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.types;

import org.forgerock.opendj.ldap.DN;

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
  /** ID of the backend with which this cache entry is associated. */
  private final String backendID;

  /** The entry itself. */
  private final Entry entry;

  /** The entry ID for the entry within the backend. */
  private final long entryID;

  /**
   * Creates a new cache entry with the provided information.
   *
   * @param  entry    The entry for this cache entry.
   * @param  backendID  ID of the backend for this cache entry.
   * @param  entryID  The entry ID for this cache entry.
   */
  public CacheEntry(Entry entry, String backendID, long entryID)
  {
    this.entry   = entry;
    this.backendID = backendID;
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
   * Retrieves the backend ID for this cache entry.
   *
   * @return  ID of the backend for this cache entry.
   */
  public String getBackendID()
  {
    return backendID;
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
   * Retrieves the DN for this cache entry.
   *
   * @return  The DN for this cache entry.
   */
  public DN getDN()
  {
    return entry.getName();
  }

  /**
   * Retrieves the hash code for this cache entry.  It will be the
   * integer representation of the entry ID.
   *
   * @return  The hash code for this cache entry.
   */
  @Override
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
  @Override
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
    return e.entryID == entryID && e.entry.equals(entry);
  }
}

