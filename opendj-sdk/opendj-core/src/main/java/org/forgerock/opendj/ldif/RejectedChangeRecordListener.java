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

package org.forgerock.opendj.ldif;

import static com.forgerock.opendj.ldap.CoreMessages.REJECTED_CHANGE_FAIL_ADD_DUPE;
import static com.forgerock.opendj.ldap.CoreMessages.REJECTED_CHANGE_FAIL_MODIFYDN_DUPE;

import org.forgerock.i18n.LocalizableMessage;
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
public interface RejectedChangeRecordListener {
    /**
     * A handler which terminates processing by throwing a
     * {@code DecodeException} as soon as a change is rejected.
     */
    RejectedChangeRecordListener FAIL_FAST = new RejectedChangeRecordListener() {

        public Entry handleDuplicateEntry(final AddRequest change, final Entry existingEntry) throws DecodeException {
            throw DecodeException.error(REJECTED_CHANGE_FAIL_ADD_DUPE.get(change.getName()));
        }

        public Entry handleDuplicateEntry(final ModifyDNRequest change, final Entry existingEntry,
                final Entry renamedEntry) throws DecodeException {
            throw DecodeException.error(REJECTED_CHANGE_FAIL_MODIFYDN_DUPE.get(renamedEntry.getName()));
        }

        public void handleRejectedChangeRecord(final AddRequest change, final LocalizableMessage reason)
                throws DecodeException {
            throw DecodeException.error(reason);
        }

        public void handleRejectedChangeRecord(final DeleteRequest change, final LocalizableMessage reason)
                throws DecodeException {
            throw DecodeException.error(reason);
        }

        public void handleRejectedChangeRecord(final ModifyRequest change, final LocalizableMessage reason)
                throws DecodeException {
            throw DecodeException.error(reason);
        }

        public void handleRejectedChangeRecord(final ModifyDNRequest change, final LocalizableMessage reason)
                throws DecodeException {
            throw DecodeException.error(reason);
        }

    };

    /**
     * The default handler which ignores changes applied to missing entries and
     * tolerates duplicate entries by overwriting the existing entry with the
     * new entry.
     */
    RejectedChangeRecordListener OVERWRITE = new RejectedChangeRecordListener() {

        public Entry handleDuplicateEntry(final AddRequest change, final Entry existingEntry) throws DecodeException {
            // Overwrite existing entries.
            return change;
        }

        public Entry handleDuplicateEntry(final ModifyDNRequest change, final Entry existingEntry,
                final Entry renamedEntry) throws DecodeException {
            // Overwrite existing entries.
            return renamedEntry;
        }

        public void handleRejectedChangeRecord(AddRequest change, LocalizableMessage reason) throws DecodeException {
            // Ignore.
        }

        public void handleRejectedChangeRecord(DeleteRequest change, LocalizableMessage reason)
                throws DecodeException {
            // Ignore.
        }

        public void handleRejectedChangeRecord(ModifyRequest change, LocalizableMessage reason)
                throws DecodeException {
            // Ignore.
        }

        public void handleRejectedChangeRecord(ModifyDNRequest change, LocalizableMessage reason)
                throws DecodeException {
            // Ignore.
        }

    };

    /**
     * Invoked when an attempt was made to add an entry which already exists.
     *
     * @param change
     *            The conflicting add request.
     * @param existingEntry
     *            The pre-existing entry.
     * @return The entry which should be kept.
     * @throws DecodeException
     *             If processing should terminate.
     */
    Entry handleDuplicateEntry(AddRequest change, Entry existingEntry) throws DecodeException;

    /**
     * Invoked when an attempt was made to rename an entry which already exists.
     *
     * @param change
     *            The conflicting add request.
     * @param existingEntry
     *            The pre-existing entry.
     * @param renamedEntry
     *            The renamed entry.
     * @return The entry which should be kept.
     * @throws DecodeException
     *             If processing should terminate.
     */
    Entry handleDuplicateEntry(ModifyDNRequest change, Entry existingEntry, Entry renamedEntry)
            throws DecodeException;

    /**
     * Invoked when an attempt to add an entry was rejected. This may be because
     * the target parent entry was not found, or controls provided with the
     * request are not supported. This method will not be called when the entry
     * to be added already exists, since this is handled by
     * {@link #handleDuplicateEntry(AddRequest, Entry)}.
     *
     * @param change
     *            The rejected add request.
     * @param reason
     *            The reason why the record was rejected.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleRejectedChangeRecord(AddRequest change, LocalizableMessage reason)
            throws DecodeException;

    /**
     * Invoked when an attempt to delete an entry was rejected. This may be
     * because the target entry was not found, or controls provided with the
     * request are not supported.
     *
     * @param change
     *            The rejected delete request.
     * @param reason
     *            The reason why the record was rejected.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleRejectedChangeRecord(DeleteRequest change, LocalizableMessage reason)
            throws DecodeException;

    /**
     * Invoked when an attempt to modify an entry was rejected. This may be
     * because the target entry was not found, or controls provided with the
     * request are not supported.
     *
     * @param change
     *            The rejected modify request.
     * @param reason
     *            The reason why the record was rejected.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleRejectedChangeRecord(ModifyRequest change, LocalizableMessage reason)
            throws DecodeException;

    /**
     * Invoked when an attempt to rename an entry was rejected. This may be
     * because the target entry was not found, or controls provided with the
     * request are not supported. This method will not be called when a renamed
     * entry already exists, since this is handled by
     * {@link #handleDuplicateEntry(ModifyDNRequest, Entry, Entry)}.
     *
     * @param change
     *            The rejected modify DN request.
     * @param reason
     *            The reason why the record was rejected.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleRejectedChangeRecord(ModifyDNRequest change, LocalizableMessage reason)
            throws DecodeException;

}
