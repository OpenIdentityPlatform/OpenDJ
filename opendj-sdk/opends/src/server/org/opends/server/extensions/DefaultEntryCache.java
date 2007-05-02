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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.EntryCacheCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.EntryCache;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockType;
import org.opends.server.types.ResultCode;




/**
 * This class defines the default entry cache that will be used in the server if
 * none is configured.  It does not actually store any entries, so all calls to
 * <CODE>getEntry</CODE> will return <CODE>null</CODE>, and all calls to
 * <CODE>putEntry</CODE> will return immediately without doing anything.
 */
public class DefaultEntryCache
       extends EntryCache<EntryCacheCfg>
       implements ConfigurationChangeListener<EntryCacheCfg>
{



  /**
   * Creates a new instance of this default entry cache.
   */
  public DefaultEntryCache()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
  public void initializeEntryCache(EntryCacheCfg configEntry)
         throws ConfigException, InitializationException
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public void finalizeEntryCache()
  {
    // No implementation required.
  }



  /**
   * {@inheritDoc}
   */
  public boolean containsEntry(DN entryDN)
  {
    // This implementation does not store any entries.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public Entry getEntry(DN entryDN)
  {
    // This implementation does not store any entries.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public long getEntryID(DN entryDN)
  {
    // This implementation does not store any entries.
    return -1;
  }



  /**
   * {@inheritDoc}
   */
  public Entry getEntry(DN entryDN, LockType lockType, List<Lock> lockList)
  {
    // This implementation does not store entries.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public Entry getEntry(Backend backend, long entryID, LockType lockType,
                        List<Lock> lockList)
  {
    // This implementation does not store entries.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public void putEntry(Entry entry, Backend backend, long entryID)
  {
    // This implementation does not store entries.
  }



  /**
   * {@inheritDoc}
   */
  public boolean putEntryIfAbsent(Entry entry, Backend backend, long entryID)
  {
    // This implementation does not store entries, so we will never have a
    // conflict.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public void removeEntry(DN entryDN)
  {
    // This implementation does not store entries.
  }



  /**
   * {@inheritDoc}
   */
  public void clear()
  {
    // This implementation does not store entries.
  }



  /**
   * {@inheritDoc}
   */
  public void clearBackend(Backend backend)
  {
    // This implementation does not store entries.
  }



  /**
   * {@inheritDoc}
   */
  public void clearSubtree(DN baseDN)
  {
    // This implementation does not store entries.
  }



  /**
   * {@inheritDoc}
   */
  public void handleLowMemory()
  {
    // This implementation does not store entries, so there are no resources
    // that it can free.
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      EntryCacheCfg configuration,
      List<String>  unacceptableReasons
      )
  {
    // No implementation required.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      EntryCacheCfg configuration
      )
  {
    // No implementation required.

    ConfigChangeResult changeResult = new ConfigChangeResult(
        ResultCode.SUCCESS, false, new ArrayList<String>()
        );

    return changeResult;
    }
}

