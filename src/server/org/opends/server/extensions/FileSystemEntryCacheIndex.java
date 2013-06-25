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

package org.opends.server.extensions;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class represents serializable entry cache index structures
 * and supporting data types used for the entry cache persistence.
 * Structurally it should be an inner class of FileSystemEntryCache
 * however due to serialization constraints it has been separated.
 */
class FileSystemEntryCacheIndex implements Serializable {

  static final long serialVersionUID = 4537634108673038611L;

  /**
   * The file system entry cache instance this index belongs to.
   */
  transient private FileSystemEntryCache fsEntryCacheInstance;

  /**
   * Backend to checksum/id map for offline state.
   */
  public Map<String, Long> offlineState;
  /**
   * The mapping between backends and ID to DN maps.
   */
  public Map<String, Map<Long, String>> backendMap;
  /**
   * The mapping between DNs and IDs.
   */
  public Map<String, Long> dnMap;

  /**
   * Index constructor.
   * @param fsEntryCacheInstance The File System Entry Cache instance
   *                             this index is associated with.
   * @param accessOrder          The ordering mode for the index map
   *                             {@code true} for access-order,
   *                             {@code false} for insertion-order.
   */
  protected FileSystemEntryCacheIndex(
    FileSystemEntryCache fsEntryCacheInstance, boolean accessOrder) {

    this.fsEntryCacheInstance = fsEntryCacheInstance;

    offlineState =
      new ConcurrentHashMap<String, Long>();
    backendMap =
      new HashMap<String, Map<Long, String>>();
    dnMap =
      new LinkedHashMapRotator<String,Long>(
      16, (float) 0.75, accessOrder);
  }

  /**
   * This inner class exist solely to override <CODE>removeEldestEntry()</CODE>
   * method of the LinkedHashMap.
   *
   * @see  java.util.LinkedHashMap
   */
  private class LinkedHashMapRotator<K,V> extends LinkedHashMap<K,V> {

    static final long serialVersionUID = 5271482121415968435L;

    /**
     * Linked Hash Map Rotator constructor.
     * @param initialCapacity The initial capacity.
     * @param loadFactor      The load factor.
     * @param accessOrder     The ordering mode - {@code true} for
     *                        access-order, {@code false} for
     *                        insertion-order.
     */
    public LinkedHashMapRotator(int initialCapacity,
                                float loadFactor,
                                boolean accessOrder) {
      super(initialCapacity, loadFactor, accessOrder);
    }

    /**
     * This method will get called each time we add a new key/value
     * pair to the map. The eldest entry will be selected by the
     * underlying LinkedHashMap implementation based on the access
     * order configured and will follow either FIFO implementation
     * by default or LRU implementation if configured so explicitly.
     * @param  eldest  The least recently inserted entry in the map,
     *                 or if this is an access-ordered map, the least
     *                 recently accessed entry.
     * @return boolean {@code true} if the eldest entry should be
     *                 removed from the map; {@code false} if it
     *                 should be retained.
     */
    @Override protected boolean removeEldestEntry(Map.Entry eldest) {
      return fsEntryCacheInstance.removeEldestEntry(eldest);
    }
  }
}
