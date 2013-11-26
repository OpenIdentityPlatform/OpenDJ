/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.sync.filters;



import java.util.Date;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;



/**
 * The context associated with a change record to be filtered. The context
 * provides access to meta data associated with changes as they are processed,
 * including but not limited to:
 * <ul>
 * <li>The change number
 * <li>The CSN, if available
 * <li>The entry's unique ID, if available
 * <li>The name of the user who performed the change, if available
 * </ul>
 */
public final class ChangeRecordContext
{
  private final Entry entry;



  /**
   * Creates a new change record context using the provided change log entry.
   *
   * @param entry
   *          The change log entry.
   */
  ChangeRecordContext(final Entry entry)
  {
    this.entry = Entries.unmodifiableEntry(entry);
  }



  /**
   * Returns the change cookie contained in the change log entry, or
   * {@code null} if it is not present.
   *
   * @return The change cookie contained in the change log entry, or
   *         {@code null} if it is not present.
   */
  public String getChangeCookie()
  {
    // TODO
    return null;
  }



  /**
   * Returns an unmodifiable view of the underlying change log entry.
   *
   * @return An unmodifiable view of the underlying change log entry.
   */
  public Entry getChangeEntry()
  {
    return entry;
  }



  /**
   * Returns the change initiators name contained in the change log entry, or
   * {@code null} if it is not present.
   *
   * @return The change initiators name contained in the change log entry, or
   *         {@code null} if it is not present.
   */
  public DN getChangeInitiatorsName()
  {
    // TODO
    return null;
  }



  /**
   * Returns the change number contained in the change log entry, or {@code -1}
   * if it is not present.
   *
   * @return The change number contained in the change log entry, or {@code -1}
   *         if it is not present.
   */
  public long getChangeNumber()
  {
    // TODO
    return 0L;
  }



  /**
   * Returns the change time contained in the change log entry, or {@code null}
   * if it is not present.
   *
   * @return The change time contained in the change log entry, or {@code null}
   *         if it is not present.
   */
  public Date getChangeTime()
  {
    // TODO
    return null;
  }
}
