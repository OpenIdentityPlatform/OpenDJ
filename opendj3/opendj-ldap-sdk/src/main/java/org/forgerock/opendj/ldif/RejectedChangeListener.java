/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldif;



import static org.forgerock.opendj.ldap.CoreMessages.*;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;



/**
 * A listener interface which is notified whenever a change record cannot be
 * applied to an entry. This may occur when an attempt is made to update a
 * non-existent entry, or add an entry which already exists.
 * <p>
 * By default the {@link #FAIL_FAST} listener is used.
 */
public interface RejectedChangeListener
{
  /**
   * A handler which terminates processing by throwing a {@code DecodeException}
   * as soon as a change is rejected.
   */
  public final static RejectedChangeListener FAIL_FAST = new RejectedChangeListener()
  {

    @Override
    public Entry handleDuplicateEntry(final AddRequest change,
        final Entry existingEntry) throws DecodeException
    {
      throw DecodeException.error(REJECTED_CHANGE_FAIL_ADD_DUPE.get(change
          .getName().toString()));
    }



    @Override
    public Entry handleDuplicateEntry(final ModifyDNRequest change,
        final Entry existingEntry, final Entry renamedEntry)
        throws DecodeException
    {
      throw DecodeException.error(REJECTED_CHANGE_FAIL_MODIFYDN_DUPE
          .get(renamedEntry.getName().toString()));
    }



    @Override
    public void handleMissingEntry(final DeleteRequest change)
        throws DecodeException
    {
      throw DecodeException.error(REJECTED_CHANGE_FAIL_DELETE.get(change
          .getName().toString()));
    }



    @Override
    public void handleMissingEntry(final ModifyDNRequest change)
        throws DecodeException
    {
      throw DecodeException.error(REJECTED_CHANGE_FAIL_MODIFYDN.get(change
          .getName().toString()));
    }



    @Override
    public void handleMissingEntry(final ModifyRequest change)
        throws DecodeException
    {
      throw DecodeException.error(REJECTED_CHANGE_FAIL_MODIFY.get(change
          .getName().toString()));
    }
  };

  /**
   * The default handler which ignores changes applied to missing entries and
   * tolerates duplicate entries by overwriting the existing entry with the new
   * entry.
   */
  public final static RejectedChangeListener OVERWRITE = new RejectedChangeListener()
  {

    @Override
    public Entry handleDuplicateEntry(final AddRequest change,
        final Entry existingEntry) throws DecodeException
    {
      // Overwrite existing entries.
      return change;
    }



    @Override
    public Entry handleDuplicateEntry(final ModifyDNRequest change,
        final Entry existingEntry, final Entry renamedEntry)
        throws DecodeException
    {
      // Overwrite existing entries.
      return renamedEntry;
    }



    @Override
    public void handleMissingEntry(final DeleteRequest change)
        throws DecodeException
    {
      // Ignore changes applied to missing entries.
    }



    @Override
    public void handleMissingEntry(final ModifyDNRequest change)
        throws DecodeException
    {
      // Ignore changes applied to missing entries.
    }



    @Override
    public void handleMissingEntry(final ModifyRequest change)
        throws DecodeException
    {
      // Ignore changes applied to missing entries.
    }
  };



  /**
   * Invoked when an attempt was made to add an entry which already exists.
   *
   * @param change
   *          The conflicting add request.
   * @param existingEntry
   *          The pre-existing entry.
   * @return The entry which should be kept.
   * @throws DecodeException
   *           If processing should terminate.
   */
  Entry handleDuplicateEntry(AddRequest change, Entry existingEntry)
      throws DecodeException;



  /**
   * Invoked when an attempt was made to rename an entry which already exists.
   *
   * @param change
   *          The conflicting add request.
   * @param existingEntry
   *          The pre-existing entry.
   * @param renamedEntry
   *          The renamed entry.
   * @return The entry which should be kept.
   * @throws DecodeException
   *           If processing should terminate.
   */
  Entry handleDuplicateEntry(ModifyDNRequest change, Entry existingEntry,
      Entry renamedEntry) throws DecodeException;



  /**
   * Invoked when an attempt was made to delete an entry which does not exist.
   *
   * @param change
   *          The conflicting delete request.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleMissingEntry(DeleteRequest change) throws DecodeException;



  /**
   * Invoked when an attempt was made to rename an entry which does not exist.
   *
   * @param change
   *          The conflicting rename request.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleMissingEntry(ModifyDNRequest change) throws DecodeException;



  /**
   * Invoked when an attempt was made to modify an entry which does not exist.
   *
   * @param change
   *          The conflicting modify request.
   * @throws DecodeException
   *           If processing should terminate.
   */
  void handleMissingEntry(ModifyRequest change) throws DecodeException;

}
