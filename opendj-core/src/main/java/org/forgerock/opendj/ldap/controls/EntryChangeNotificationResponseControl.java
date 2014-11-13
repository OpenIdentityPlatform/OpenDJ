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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.util.Reject;

/**
 * The entry change notification response control as defined in
 * draft-ietf-ldapext-psearch. This control provides additional information
 * about the change that caused a particular entry to be returned as the result
 * of a persistent search.
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
 * @see PersistentSearchRequestControl
 * @see PersistentSearchChangeType
 * @see <a
 *      href="http://tools.ietf.org/html/draft-ietf-ldapext-psearch">draft-ietf-ldapext-psearch
 *      - Persistent Search: A Simple LDAP Change Notification Mechanism </a>
 */
public final class EntryChangeNotificationResponseControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
    /**
     * The OID for the entry change notification response control.
     */
    public static final String OID = "2.16.840.1.113730.3.4.7";

    /**
     * A decoder which can be used for decoding the entry change notification
     * response control.
     */
    public static final ControlDecoder<EntryChangeNotificationResponseControl> DECODER =
            new ControlDecoder<EntryChangeNotificationResponseControl>() {

                public EntryChangeNotificationResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control, options);

                    if (control instanceof EntryChangeNotificationResponseControl) {
                        return (EntryChangeNotificationResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_ECN_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_ECN_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    String previousDNString = null;
                    long changeNumber = -1;
                    PersistentSearchChangeType changeType;
                    final ASN1Reader reader = ASN1.getReader(control.getValue());
                    try {
                        reader.readStartSequence();

                        final int changeTypeInt = reader.readEnumerated();
                        switch (changeTypeInt) {
                        case 1:
                            changeType = PersistentSearchChangeType.ADD;
                            break;
                        case 2:
                            changeType = PersistentSearchChangeType.DELETE;
                            break;
                        case 4:
                            changeType = PersistentSearchChangeType.MODIFY;
                            break;
                        case 8:
                            changeType = PersistentSearchChangeType.MODIFY_DN;
                            break;
                        default:
                            final LocalizableMessage message =
                                    ERR_ECN_BAD_CHANGE_TYPE.get(changeTypeInt);
                            throw DecodeException.error(message);
                        }

                        if (reader.hasNextElement()
                                && (reader.peekType() == ASN1.UNIVERSAL_OCTET_STRING_TYPE)) {
                            if (changeType != PersistentSearchChangeType.MODIFY_DN) {
                                final LocalizableMessage message =
                                        ERR_ECN_ILLEGAL_PREVIOUS_DN.get(String.valueOf(changeType));
                                throw DecodeException.error(message);
                            }

                            previousDNString = reader.readOctetStringAsString();
                        }
                        if (reader.hasNextElement()
                                && (reader.peekType() == ASN1.UNIVERSAL_INTEGER_TYPE)) {
                            changeNumber = reader.readInteger();
                        }
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("%s", e));

                        final LocalizableMessage message =
                                ERR_ECN_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
                        throw DecodeException.error(message, e);
                    }

                    final Schema schema =
                            options.getSchemaResolver().resolveSchema(previousDNString);
                    DN previousDN = null;
                    if (previousDNString != null) {
                        try {
                            previousDN = DN.valueOf(previousDNString, schema);
                        } catch (final LocalizedIllegalArgumentException e) {
                            final LocalizableMessage message =
                                    ERR_ECN_INVALID_PREVIOUS_DN.get(getExceptionMessage(e));
                            throw DecodeException.error(message, e);
                        }
                    }

                    return new EntryChangeNotificationResponseControl(control.isCritical(),
                            changeType, previousDN, changeNumber);
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * Creates a new entry change notification response control with the
     * provided change type and optional previous distinguished name and change
     * number.
     *
     * @param type
     *            The change type for this change notification control.
     * @param previousName
     *            The distinguished name that the entry had prior to a modify DN
     *            operation, or <CODE>null</CODE> if the operation was not a
     *            modify DN.
     * @param changeNumber
     *            The change number for the associated change, or a negative
     *            value if no change number is available.
     * @return The new control.
     * @throws NullPointerException
     *             If {@code type} was {@code null}.
     */
    public static EntryChangeNotificationResponseControl newControl(
            final PersistentSearchChangeType type, final DN previousName, final long changeNumber) {
        return new EntryChangeNotificationResponseControl(false, type, previousName, changeNumber);
    }

    /**
     * Creates a new entry change notification response control with the
     * provided change type and optional previous distinguished name and change
     * number. The previous distinguished name, if provided, will be decoded
     * using the default schema.
     *
     * @param type
     *            The change type for this change notification control.
     * @param previousName
     *            The distinguished name that the entry had prior to a modify DN
     *            operation, or <CODE>null</CODE> if the operation was not a
     *            modify DN.
     * @param changeNumber
     *            The change number for the associated change, or a negative
     *            value if no change number is available.
     * @return The new control.
     * @throws LocalizedIllegalArgumentException
     *             If {@code previousName} is not a valid LDAP string
     *             representation of a DN.
     * @throws NullPointerException
     *             If {@code type} was {@code null}.
     */
    public static EntryChangeNotificationResponseControl newControl(
            final PersistentSearchChangeType type, final String previousName,
            final long changeNumber) {
        return new EntryChangeNotificationResponseControl(false, type, DN.valueOf(previousName),
                changeNumber);
    }

    /** The previous DN for this change notification control. */
    private final DN previousName;

    /** The change number for this change notification control. */
    private final long changeNumber;

    /** The change type for this change notification control. */
    private final PersistentSearchChangeType changeType;

    private final boolean isCritical;

    private EntryChangeNotificationResponseControl(final boolean isCritical,
            final PersistentSearchChangeType changeType, final DN previousName,
            final long changeNumber) {
        Reject.ifNull(changeType);
        this.isCritical = isCritical;
        this.changeType = changeType;
        this.previousName = previousName;
        this.changeNumber = changeNumber;
    }

    /**
     * Returns the change number for this entry change notification control.
     *
     * @return The change number for this entry change notification control, or
     *         a negative value if no change number is available.
     */
    public long getChangeNumber() {
        return changeNumber;
    }

    /**
     * Returns the change type for this entry change notification control.
     *
     * @return The change type for this entry change notification control.
     */
    public PersistentSearchChangeType getChangeType() {
        return changeType;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns the distinguished name that the entry had prior to a modify DN
     * operation, or <CODE>null</CODE> if the operation was not a modify DN.
     *
     * @return The distinguished name that the entry had prior to a modify DN
     *         operation.
     */
    public DN getPreviousName() {
        return previousName;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            writer.writeStartSequence();
            writer.writeInteger(changeType.intValue());

            if (previousName != null) {
                writer.writeOctetString(previousName.toString());
            }

            if (changeNumber > 0) {
                writer.writeInteger(changeNumber);
            }
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

    /** {@inheritDoc} */
    public boolean isCritical() {
        return isCritical;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("EntryChangeNotificationResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", changeType=");
        builder.append(changeType);
        builder.append(", previousDN=\"");
        builder.append(previousName);
        builder.append("\"");
        builder.append(", changeNumber=");
        builder.append(changeNumber);
        builder.append(")");
        return builder.toString();
    }

}
