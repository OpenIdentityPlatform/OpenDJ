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
 * Copyright 2011 ForgeRock AS.
 */

package org.forgerock.opendj.ldif;

import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * A listener interface which is notified whenever LDIF records are skipped,
 * malformed, or fail schema validation.
 * <p>
 * By default the {@link #FAIL_FAST} listener is used.
 */
public interface RejectedLDIFListener {
    /**
     * The default handler which ignores skipped records but which terminates
     * processing by throwing a {@code DecodeException} as soon as a record is
     * found to be malformed or rejected due to a schema validation failure.
     */
    RejectedLDIFListener FAIL_FAST = new RejectedLDIFListener() {

        @Override
        public void handleMalformedRecord(final long lineNumber, final List<String> lines,
                final LocalizableMessage reason) throws DecodeException {
            // Fail fast.
            throw DecodeException.error(reason);
        }

        @Override
        public void handleSchemaValidationFailure(final long lineNumber, final List<String> lines,
                final List<LocalizableMessage> reasons) throws DecodeException {
            // Fail fast - just use first message.
            throw DecodeException.error(reasons.get(0));
        }

        @Override
        public void handleSchemaValidationWarning(final long lineNumber, final List<String> lines,
                final List<LocalizableMessage> reasons) throws DecodeException {
            // Ignore schema validation warnings.
        }

        @Override
        public void handleSkippedRecord(final long lineNumber, final List<String> lines,
                final LocalizableMessage reason) throws DecodeException {
            // Ignore skipped records.
        }
    };

    /**
     * A handler which ignores all rejected record notifications.
     */
    RejectedLDIFListener IGNORE_ALL = new RejectedLDIFListener() {

        @Override
        public void handleMalformedRecord(final long lineNumber, final List<String> lines,
                final LocalizableMessage reason) throws DecodeException {
            // Ignore malformed records.
        }

        @Override
        public void handleSchemaValidationFailure(final long lineNumber, final List<String> lines,
                final List<LocalizableMessage> reasons) throws DecodeException {
            // Ignore schema validation failures.
        }

        @Override
        public void handleSchemaValidationWarning(final long lineNumber, final List<String> lines,
                final List<LocalizableMessage> reasons) throws DecodeException {
            // Ignore schema validation warnings.
        }

        @Override
        public void handleSkippedRecord(final long lineNumber, final List<String> lines,
                final LocalizableMessage reason) throws DecodeException {
            // Ignore skipped records.
        }
    };

    /**
     * Invoked when a record was rejected because it was malformed in some way
     * and could not be decoded.
     *
     * @param lineNumber
     *            The line number within the source location in which the
     *            malformed record is located, if known, otherwise {@code -1}.
     * @param lines
     *            The content of the malformed record.
     * @param reason
     *            The reason why the record is malformed.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleMalformedRecord(long lineNumber, List<String> lines, LocalizableMessage reason)
            throws DecodeException;

    /**
     * Invoked when a record was rejected because it does not conform to the
     * schema and schema validation is enabled.
     *
     * @param lineNumber
     *            The line number within the source location in which the
     *            rejected record is located, if known, otherwise {@code -1}.
     * @param lines
     *            The content of the record which failed schema validation.
     * @param reasons
     *            The reasons why the record failed schema validation.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleSchemaValidationFailure(long lineNumber, List<String> lines,
            List<LocalizableMessage> reasons) throws DecodeException;

    /**
     * Invoked when a record was not rejected but contained one or more schema
     * validation warnings.
     *
     * @param lineNumber
     *            The line number within the source location in which the record
     *            is located, if known, otherwise {@code -1}.
     * @param lines
     *            The content of the record which contained schema validation
     *            warnings.
     * @param reasons
     *            The schema validation warnings.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleSchemaValidationWarning(long lineNumber, List<String> lines,
            List<LocalizableMessage> reasons) throws DecodeException;

    /**
     * Invoked when a record was skipped because it did not match filter
     * criteria defined by the reader.
     *
     * @param lineNumber
     *            The line number within the source location in which the
     *            skipped record is located, if known, otherwise {@code -1}.
     * @param lines
     *            The content of the record which was skipped.
     * @param reason
     *            The reason why the record was skipped.
     * @throws DecodeException
     *             If processing should terminate.
     */
    void handleSkippedRecord(long lineNumber, List<String> lines, LocalizableMessage reason)
            throws DecodeException;

}
