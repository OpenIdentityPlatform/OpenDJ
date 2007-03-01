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
package org.opends.server.backends.jeb;

/**
 * A modification to an attribute index, to either insert an entry ID or
 * delete an entry ID, for a given key.
 */
public class IndexMod
{
  /**
   * The index key to be modified.
   */
  byte[] key;

  /**
   * The entry ID to be inserted or deleted.
   */
  EntryID value;

  /**
   * Indicates whether the entry ID should be inserted or deleted.
   */
  boolean isDelete;

  /**
   * Create a new index modification.
   * @param key The index key to be modified.
   * @param value The entry ID to be inserted or deleted.
   * @param isDelete Indicates whether the entry ID should be inserted or
   * deleted.
   */
  public IndexMod(byte[] key, EntryID value, boolean isDelete)
  {
    this.key = key;
    this.value = value;
    this.isDelete = isDelete;
  }

}

