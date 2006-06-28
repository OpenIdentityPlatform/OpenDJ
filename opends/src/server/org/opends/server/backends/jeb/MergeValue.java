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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * This is a class used by the index merge thread. It merges the data
 * for one index key from multiple intermediate files.
 */
public class MergeValue
{
  /**
   * The value of the index key.
   */
  byte[] key;

  /**
   * The entry IDs to be added, where each set comes from a different file.
   */
  List<Longs> addData;

  /**
   * The entry IDs to be deleted, where each set comes from a different file.
   */
  ArrayList<Longs> delData;

  /**
   * A bit set indicating which files have contributed data for this key.
   * Each file reader is identified by an array index. If bit n is set,
   * it means that the reader with index n contributed data.
   */
  BitSet readers;

  /**
   * The index entry limit.
   */
  int entryLimit;

  /**
   * Create a new merge value.
   * @param numReaders The total number of file readers that could be
   * contributing to this value.  Reader identifiers are in the range
   * 0 .. numReaders-1.
   * @param entryLimit The configured index entry limit.
   */
  public MergeValue(int numReaders, int entryLimit)
  {
    this.key = null;
    addData = new ArrayList<Longs>(numReaders);
    delData = new ArrayList<Longs>(numReaders);
    readers = new BitSet(numReaders);
    this.entryLimit = entryLimit;
  }


  /**
   * Get the value of the key.
   * @return The key value.
   */
  public byte[] getKey()
  {
    return key;
  }


  /**
   * Set the value of the key.
   * @param key The key value .
   */
  public void setKey(byte[] key)
  {
    this.key = key;
  }



  /**
   * Provide data for the key from one of the file readers.
   * @param reader The reader providing the data.
   * @param addData A set of entry IDs to be added.
   * @param delData A set of entry IDs to be deleted.
   */
  public void mergeData(int reader, Longs addData, Longs delData)
  {
    this.addData.add(addData);
    if (delData.size() > 0)
    {
      this.delData.add(delData);
    }
    readers.set(reader);
  }


  /**
   * Get the readers that provided data to be merged.
   * @return An array of identifiers of readers that provided data.
   */
  public int[] getReaders()
  {
    int[] ret = new int[readers.cardinality()];

    for (int i = readers.nextSetBit(0), j = 0; i != -1;
         i = readers.nextSetBit(i+1))
    {
      ret[j++] = i;
    }
    return ret;
  }


  /**
   * Get the list of arrays of IDs to be added.
   * @return The list of arrays of IDs to be added.
   */
  public List<Longs> getAddValues()
  {
    return addData;
  }


  /**
   * Get the list of arrays of IDs to be deleted.
   * @return The list of arrays of IDs to be deleted.
   */
  public List<Longs> getDelValues()
  {
    return delData;
  }

}
