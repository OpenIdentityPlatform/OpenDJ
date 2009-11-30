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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.ldif;



import java.io.Closeable;
import java.io.IOException;

import org.opends.sdk.DecodeException;
import org.opends.sdk.Entry;



/**
 * An interface for reading entries from a data source, typically an
 * LDIF file.
 * <p>
 * Implementations must specify the following:
 * <ul>
 * <li>Whether or not it is possible for the implementation to encounter
 * malformed change records and, if it is possible, how they are
 * handled.
 * <li>Any synchronization limitations.
 * </ul>
 * <p>
 * TODO: LDIFInputStreamReader
 * <p>
 * TODO: SearchResultEntryReader
 */
public interface EntryReader extends Closeable
{

  /**
   * Closes this entry reader if it is not already closed. Note that
   * this method does not need to be called if a previous call of
   * {@link #readEntry()} has returned {@code null}.
   *
   * @throws IOException
   *           If an unexpected IO error occurred while closing.
   */
  void close() throws IOException;



  /**
   * Reads the next entry, blocking if necessary until an entry is
   * available.
   *
   * @return The next entry or {@code null} if there are no more entries
   *         to be read.
   * @throws DecodeException
   *           If the entry could not be decoded because it was
   *           malformed.
   * @throws IOException
   *           If an unexpected IO error occurred while reading the
   *           entry.
   */
  Entry readEntry() throws DecodeException, IOException;
}
