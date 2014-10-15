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
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.util.Reject;

/**
 * The persistent search request control as defined in
 * draft-ietf-ldapext-psearch. This control allows a client to receive
 * notification of changes that occur in an LDAP server.
 * <p>
 * You can examine the entry change notification response control to get more
 * information about a change returned by the persistent search.
 *
 * <pre>
 * Connection connection = ...;
 *
 * SearchRequest request =
 *         Requests.newSearchRequest(
 *                 "dc=example,dc=com", SearchScope.WHOLE_SUBTREE,
 *                 "(objectclass=inetOrgPerson)", "cn")
 *                 .addControl(PersistentSearchRequestControl.newControl(
 *                             true, true, true, // critical,changesOnly,returnECs
 *                             PersistentSearchChangeType.ADD,
 *                             PersistentSearchChangeType.DELETE,
 *                             PersistentSearchChangeType.MODIFY,
 *                             PersistentSearchChangeType.MODIFY_DN));
 *
 * ConnectionEntryReader reader = connection.search(request);
 *
 * while (reader.hasNext()) {
 *     if (!reader.isReference()) {
 *         SearchResultEntry entry = reader.readEntry(); // Entry that changed
 *
 *         EntryChangeNotificationResponseControl control = entry.getControl(
 *                 EntryChangeNotificationResponseControl.DECODER,
 *                 new DecodeOptions());
 *
 *         PersistentSearchChangeType type = control.getChangeType();
 *         if (type.equals(PersistentSearchChangeType.MODIFY_DN)) {
 *             // Previous DN: control.getPreviousName()
 *         }
 *         // Change number: control.getChangeNumber());
 *     }
 * }
 *
 * </pre>
 *
 * @see EntryChangeNotificationResponseControl
 * @see PersistentSearchChangeType
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-ldapext-psearch">draft-ietf-ldapext-psearch
 *      - Persistent Search: A Simple LDAP Change Notification Mechanism </a>
 */
public final class PersistentSearchRequestControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the persistent search request control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.3";

    /**
     * A decoder which can be used for decoding the persistent search request
     * control.
     */
    public static final ControlDecoder<PersistentSearchRequestControl> DECODER =
            new ControlDecoder<PersistentSearchRequestControl>() {

                public PersistentSearchRequestControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof PersistentSearchRequestControl) {
                        return (PersistentSearchRequestControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_PSEARCH_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The control must always have a value.
                        final LocalizableMessage message = ERR_PSEARCH_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    boolean changesOnly;
                    boolean returnECs;
                    int changeTypes;

                    try {
                        reader.readStartSequence();

                        changeTypes = (int) reader.readInteger();
                        changesOnly = reader.readBoolean();
                        returnECs = reader.readBoolean();

                        reader.readEndSequence();
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("Unable to read sequence", e));

                        final LocalizableMessage message =
                                ERR_PSEARCH_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }

                    final Set<PersistentSearchChangeType> changeTypeSet =
                            EnumSet.noneOf(PersistentSearchChangeType.class);

                    if ((changeTypes & 15) != 0) {
                        final LocalizableMessage message =
                                ERR_PSEARCH_BAD_CHANGE_TYPES.get(changeTypes);
                        throw DecodeException.error(message);
                    }

                    if ((changeTypes & 1) != 0) {
                        changeTypeSet.add(PersistentSearchChangeType.ADD);
                    }

                    if ((changeTypes & 2) != 0) {
                        changeTypeSet.add(PersistentSearchChangeType.DELETE);
                    }

                    if ((changeTypes & 4) != 0) {
                        changeTypeSet.add(PersistentSearchChangeType.MODIFY);
                    }

                    if ((changeTypes & 8) != 0) {
                        changeTypeSet.add(PersistentSearchChangeType.MODIFY_DN);
                    }

                    return new PersistentSearchRequestControl(control.isCritical(), changesOnly,
                            returnECs, Collections.unmodifiableSet(changeTypeSet));
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new persistent search request control.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored
     * @param changesOnly
     *            Indicates whether or not only updated entries should be
     *            returned (added, modified, deleted, or subject to a modifyDN
     *            operation). If this parameter is {@code false} then the search
     *            will initially return all the existing entries which match the
     *            filter.
     * @param returnECs
     *            Indicates whether or not the entry change notification control
     *            should be included in updated entries that match the
     *            associated search criteria.
     * @param changeTypes
     *            The types of update operation for which change notifications
     *            should be returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code changeTypes} was {@code null}.
     */
    public static PersistentSearchRequestControl newControl(final boolean isCritical,
            final boolean changesOnly, final boolean returnECs,
            final Collection<PersistentSearchChangeType> changeTypes) {
        Reject.ifNull(changeTypes);

        final Set<PersistentSearchChangeType> copyOfChangeTypes =
                EnumSet.noneOf(PersistentSearchChangeType.class);
        copyOfChangeTypes.addAll(changeTypes);
        return new PersistentSearchRequestControl(isCritical, changesOnly, returnECs, Collections
                .unmodifiableSet(copyOfChangeTypes));
    }

    /**
     * Creates a new persistent search request control.
     *
     * @param isCritical
     *            {@code true} if it is unacceptable to perform the operation
     *            without applying the semantics of this control, or
     *            {@code false} if it can be ignored
     * @param changesOnly
     *            Indicates whether or not only updated entries should be
     *            returned (added, modified, deleted, or subject to a modifyDN
     *            operation). If this parameter is {@code false} then the search
     *            will initially return all the existing entries which match the
     *            filter.
     * @param returnECs
     *            Indicates whether or not the entry change notification control
     *            should be included in updated entries that match the
     *            associated search criteria.
     * @param changeTypes
     *            The types of update operation for which change notifications
     *            should be returned.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code changeTypes} was {@code null}.
     */
    public static PersistentSearchRequestControl newControl(final boolean isCritical,
            final boolean changesOnly, final boolean returnECs,
            final PersistentSearchChangeType... changeTypes) {
        Reject.ifNull((Object) changeTypes);

        return newControl(isCritical, changesOnly, returnECs, Arrays.asList(changeTypes));
    }

    /**
     * Indicates whether to only return entries that have been updated
     * since the beginning of the search.
     */
    private final boolean changesOnly;

    /**
     * Indicates whether entries returned as a result of changes to
     * directory data should include the entry change notification control.
     */
    private final boolean returnECs;

    /** The logical OR of change types associated with this control. */
    private final Set<PersistentSearchChangeType> changeTypes;

    private final boolean isCritical;

    private PersistentSearchRequestControl(final boolean isCritical, final boolean changesOnly,
            final boolean returnECs, final Set<PersistentSearchChangeType> changeTypes) {
        this.isCritical = isCritical;
        this.changesOnly = changesOnly;
        this.returnECs = returnECs;
        this.changeTypes = changeTypes;
    }

    /**
     * Returns an unmodifiable set containing the types of update operation for
     * which change notifications should be returned.
     *
     * @return An unmodifiable set containing the types of update operation for
     *         which change notifications should be returned.
     */
    public Set<PersistentSearchChangeType> getChangeTypes() {
        return changeTypes;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();

            int changeTypesInt = 0;
            for (final PersistentSearchChangeType changeType : changeTypes) {
                changeTypesInt |= changeType.intValue();
            }
            writer.writeInteger(changeTypesInt);

            writer.writeBoolean(changesOnly);
            writer.writeBoolean(returnECs);
            writer.writeEndSequence();
            return buffer.toByteString();
        } catch (final IOException ioe) {
            // This should never happen unless there is a bug somewhere.
            throw new RuntimeException(ioe);
        }
    }

    /** {@inheritDoc} */
    public boolean hasValue() {
        return true;
    }

    /**
     * Returns {@code true} if only updated entries should be returned (added,
     * modified, deleted, or subject to a modifyDN operation), otherwise
     * {@code false} if the search will initially return all the existing
     * entries which match the filter.
     *
     * @return {@code true} if only updated entries should be returned (added,
     *         modified, deleted, or subject to a modifyDN operation), otherwise
     *         {@code false} if the search will initially return all the
     *         existing entries which match the filter.
     */
    public boolean isChangesOnly() {
        return changesOnly;
    }

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /**
     * Returns {@code true} if the entry change notification control should be
     * included in updated entries that match the associated search criteria.
     *
     * @return {@code true} if the entry change notification control should be
     *         included in updated entries that match the associated search
     *         criteria.
     */
    public boolean isReturnECs() {
        return returnECs;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("PersistentSearchRequestControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", changeTypes=[");

        boolean comma = false;
        for (final PersistentSearchChangeType type : changeTypes) {
            if (comma) {
                builder.append(", ");
            }
            builder.append(type);
            comma = true;
        }

        builder.append("](");
        builder.append(changeTypes);
        builder.append("), changesOnly=");
        builder.append(changesOnly);
        builder.append(", returnECs=");
        builder.append(returnECs);
        builder.append(")");
        return builder.toString();
    }
}
