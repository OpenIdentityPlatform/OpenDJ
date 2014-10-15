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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package com.forgerock.opendj.ldap.controls;

import static com.forgerock.opendj.util.StaticUtils.byteToHex;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;
import static com.forgerock.opendj.ldap.CoreMessages.*;

import java.io.IOException;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

import org.forgerock.util.Reject;

/**
 * The Sun-defined account usability response control. The OID for this control
 * is 1.3.6.1.4.1.42.2.27.9.5.8, and it has a value encoded according to the
 * following BNF:
 *
 * <pre>
 * ACCOUNT_USABLE_RESPONSE ::= CHOICE {
 *      is_available           [0] INTEGER, -- Seconds before expiration --
 *      is_not_available       [1] MORE_INFO }
 *
 * MORE_INFO ::= SEQUENCE {
 *      inactive               [0] BOOLEAN DEFAULT FALSE,
 *      reset                  [1] BOOLEAN DEFAULT FALSE,
 *      expired                [2] BOOLEAN DEFAULT_FALSE,
 *      remaining_grace        [3] INTEGER OPTIONAL,
 *      seconds_before_unlock  [4] INTEGER OPTIONAL }
 * </pre>
 *
 * @see AccountUsabilityRequestControl
 */
public final class AccountUsabilityResponseControl implements Control {

    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /**
     * The OID for the account usability response control.
     */
    public static final String OID = AccountUsabilityRequestControl.OID;

    /**
     * A decoder which can be used for decoding the account usability response
     * control.
     */
    public static final ControlDecoder<AccountUsabilityResponseControl> DECODER =
            new ControlDecoder<AccountUsabilityResponseControl>() {

                public AccountUsabilityResponseControl decodeControl(final Control control,
                        final DecodeOptions options) throws DecodeException {
                    Reject.ifNull(control);

                    if (control instanceof AccountUsabilityResponseControl) {
                        return (AccountUsabilityResponseControl) control;
                    }

                    if (!control.getOID().equals(OID)) {
                        final LocalizableMessage message =
                                ERR_ACCTUSABLERES_CONTROL_BAD_OID.get(control.getOID(), OID);
                        throw DecodeException.error(message);
                    }

                    if (!control.hasValue()) {
                        // The response control must always have a value.
                        final LocalizableMessage message = ERR_ACCTUSABLERES_NO_CONTROL_VALUE.get();
                        throw DecodeException.error(message);
                    }

                    try {
                        final ASN1Reader reader = ASN1.getReader(control.getValue());
                        switch (reader.peekType()) {
                        case TYPE_SECONDS_BEFORE_EXPIRATION:
                            final int secondsBeforeExpiration = (int) reader.readInteger();
                            return new AccountUsabilityResponseControl(control.isCritical(), true,
                                    false, false, false, -1, false, 0, secondsBeforeExpiration);
                        case TYPE_MORE_INFO:
                            boolean isInactive = false;
                            boolean isReset = false;
                            boolean isExpired = false;
                            boolean isLocked = false;
                            int remainingGraceLogins = -1;
                            int secondsBeforeUnlock = 0;

                            reader.readStartSequence();
                            if (reader.hasNextElement() && (reader.peekType() == TYPE_INACTIVE)) {
                                isInactive = reader.readBoolean();
                            }
                            if (reader.hasNextElement() && (reader.peekType() == TYPE_RESET)) {
                                isReset = reader.readBoolean();
                            }
                            if (reader.hasNextElement() && (reader.peekType() == TYPE_EXPIRED)) {
                                isExpired = reader.readBoolean();
                            }
                            if (reader.hasNextElement()
                                    && (reader.peekType() == TYPE_REMAINING_GRACE_LOGINS)) {
                                remainingGraceLogins = (int) reader.readInteger();
                            }
                            if (reader.hasNextElement()
                                    && (reader.peekType() == TYPE_SECONDS_BEFORE_UNLOCK)) {
                                isLocked = true;
                                secondsBeforeUnlock = (int) reader.readInteger();
                            }
                            reader.readEndSequence();

                            return new AccountUsabilityResponseControl(control.isCritical(), false,
                                    isInactive, isReset, isExpired, remainingGraceLogins, isLocked,
                                    secondsBeforeUnlock, -1);

                        default:
                            final LocalizableMessage message =
                                    ERR_ACCTUSABLERES_UNKNOWN_VALUE_ELEMENT_TYPE
                                            .get(byteToHex(reader.peekType()));
                            throw DecodeException.error(message);
                        }
                    } catch (final IOException e) {
                        logger.debug(LocalizableMessage.raw("%s", e));

                        final LocalizableMessage message =
                                ERR_ACCTUSABLERES_DECODE_ERROR.get(getExceptionMessage(e));
                        throw DecodeException.error(message);
                    }
                }

                public String getOID() {
                    return OID;
                }
            };

    /**
     * The BER type to use for the seconds before expiration when the account is
     * available.
     */
    private static final byte TYPE_SECONDS_BEFORE_EXPIRATION = (byte) 0x80;

    /**
     * The BER type to use for the MORE_INFO sequence when the account is not
     * available.
     */
    private static final byte TYPE_MORE_INFO = (byte) 0xA1;

    /**
     * The BER type to use for the MORE_INFO element that indicates that the
     * account has been inactivated.
     */
    private static final byte TYPE_INACTIVE = (byte) 0x80;

    /**
     * The BER type to use for the MORE_INFO element that indicates that the
     * password has been administratively reset.
     */
    private static final byte TYPE_RESET = (byte) 0x81;

    /**
     * The BER type to use for the MORE_INFO element that indicates that the
     * user's password is expired.
     */
    private static final byte TYPE_EXPIRED = (byte) 0x82;

    /**
     * The BER type to use for the MORE_INFO element that provides the number of
     * remaining grace logins.
     */
    private static final byte TYPE_REMAINING_GRACE_LOGINS = (byte) 0x83;

    /**
     * The BER type to use for the MORE_INFO element that indicates that the
     * password has been administratively reset.
     */
    private static final byte TYPE_SECONDS_BEFORE_UNLOCK = (byte) 0x84;

    /**
     * Creates a new account usability response control that may be used to
     * indicate that the account is not available and provide information about
     * the underlying reason.
     *
     * @param isInactive
     *            Indicates whether the user's account has been inactivated by
     *            an administrator.
     * @param isReset
     *            Indicates whether the user's password has been reset by an
     *            administrator.
     * @param isExpired
     *            Indicates whether the user's password has expired.
     * @param remainingGraceLogins
     *            The number of grace logins remaining. A value of {@code 0}
     *            indicates that there are none remaining. A value of {@code -1}
     *            indicates that grace login functionality is not enabled.
     * @param isLocked
     *            Indicates whether the user's account is currently locked out.
     * @param secondsBeforeUnlock
     *            The length of time in seconds until the account is unlocked. A
     *            value of {@code -1} indicates that the account will not be
     *            automatically unlocked and must be reset by an administrator.
     * @return The new control.
     */
    public static AccountUsabilityResponseControl newControl(final boolean isInactive,
            final boolean isReset, final boolean isExpired, final int remainingGraceLogins,
            final boolean isLocked, final int secondsBeforeUnlock) {
        return new AccountUsabilityResponseControl(false, false, isInactive, isReset, isExpired,
                remainingGraceLogins, isLocked, secondsBeforeUnlock, -1);
    }

    /**
     * Creates a new account usability response control that may be used to
     * indicate that the account is available and provide the number of seconds
     * until expiration.
     *
     * @param secondsBeforeExpiration
     *            The length of time in seconds until the user's password
     *            expires, or {@code -1} if the user's password will not expire
     *            or the expiration time is unknown.
     * @return The new control.
     */
    public static AccountUsabilityResponseControl newControl(final int secondsBeforeExpiration) {
        return new AccountUsabilityResponseControl(false, true, false, false, false, -1, false, 0,
                secondsBeforeExpiration);
    }

    /** Indicates whether the user's account is usable. */
    private final boolean isUsable;

    /** Indicates whether the user's password is expired. */
    private final boolean isExpired;

    /** Indicates whether the user's account is inactive. */
    private final boolean isInactive;

    /** Indicates whether the user's account is currently locked. */
    private final boolean isLocked;

    /**
     * Indicates whether the user's password has been reset and must be
     * changed before anything else can be done.
     */
    private final boolean isReset;

    /** The number of remaining grace logins, if available. */
    private final int remainingGraceLogins;

    /**
     * The length of time in seconds before the user's password expires,
     * if available.
     */
    private final int secondsBeforeExpiration;

    /**
     * The length of time before the user's account is unlocked, if
     * available.
     */
    private final int secondsBeforeUnlock;

    private final boolean isCritical;

    /** Prevent direct instantiation. */
    private AccountUsabilityResponseControl(final boolean isCritical, final boolean isUsable,
            final boolean isInactive, final boolean isReset, final boolean isExpired,
            final int remainingGraceLogins, final boolean isLocked, final int secondsBeforeUnlock,
            final int secondsBeforeExpiration) {
        this.isCritical = isCritical;
        this.isUsable = isUsable;
        this.isInactive = isInactive;
        this.isReset = isReset;
        this.isExpired = isExpired;
        this.remainingGraceLogins = remainingGraceLogins;
        this.isLocked = isLocked;
        this.secondsBeforeUnlock = secondsBeforeUnlock;
        this.secondsBeforeExpiration = secondsBeforeExpiration;
    }

    /** {@inheritDoc} */
    public String getOID() {
        return OID;
    }

    /**
     * Returns the number of remaining grace logins for the user. This value is
     * unreliable if the user's password has not expired.
     *
     * @return The number of remaining grace logins for the user, or {@code -1}
     *         if the grace logins feature is not enabled for the user.
     */
    public int getRemainingGraceLogins() {
        return remainingGraceLogins;
    }

    /**
     * Returns the length of time in seconds before the user's password expires.
     * This value is unreliable if the account is not available.
     *
     * @return The length of time in seconds before the user's password expires,
     *         or {@code -1} if it is unknown or password expiration is not
     *         enabled for the user.
     */
    public int getSecondsBeforeExpiration() {
        return secondsBeforeExpiration;
    }

    /**
     * Returns the length of time in seconds before the user's account is
     * automatically unlocked. This value is unreliable is the user's account is
     * not locked.
     *
     * @return The length of time in seconds before the user's account is
     *         automatically unlocked, or {@code -1} if it requires
     *         administrative action to unlock the account.
     */
    public int getSecondsBeforeUnlock() {
        return secondsBeforeUnlock;
    }

    /** {@inheritDoc} */
    public ByteString getValue() {
        final ByteStringBuilder buffer = new ByteStringBuilder();
        final ASN1Writer writer = ASN1.getWriter(buffer);
        try {
            if (secondsBeforeExpiration < 0) {
                writer.writeInteger(TYPE_SECONDS_BEFORE_EXPIRATION, secondsBeforeExpiration);
            } else {
                writer.writeStartSequence(TYPE_MORE_INFO);
                if (isInactive) {
                    writer.writeBoolean(TYPE_INACTIVE, true);
                }

                if (isReset) {
                    writer.writeBoolean(TYPE_RESET, true);
                }

                if (isExpired) {
                    writer.writeBoolean(TYPE_EXPIRED, true);

                    if (remainingGraceLogins >= 0) {
                        writer.writeInteger(TYPE_REMAINING_GRACE_LOGINS, remainingGraceLogins);
                    }
                }

                if (isLocked) {
                    writer.writeInteger(TYPE_SECONDS_BEFORE_UNLOCK, secondsBeforeUnlock);
                }
                writer.writeEndSequence();
            }
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

    /**
     * Returns {@code true} if the user's password has expired.
     *
     * @return <CODE>true</CODE> if the user's password has expired, or
     *         <CODE>false</CODE> if not.
     */
    public boolean isExpired() {
        return isExpired;
    }

    /**
     * Returns {@code true} if the user's account has been inactivated by an
     * administrator.
     *
     * @return <CODE>true</CODE> if the user's account has been inactivated by
     *         an administrator, or <CODE>false</CODE> if not.
     */
    public boolean isInactive() {
        return isInactive;
    }

    /**
     * Returns {@code true} if the user's account is locked for some reason.
     *
     * @return <CODE>true</CODE> if the user's account is locked, or
     *         <CODE>false</CODE> if it is not.
     */
    public boolean isLocked() {
        return isLocked;
    }

    /**
     * Returns {@code true} if the user's password has been administratively
     * reset and the user must change that password before any other operations
     * will be allowed.
     *
     * @return <CODE>true</CODE> if the user's password has been
     *         administratively reset, or <CODE>false</CODE> if not.
     */
    public boolean isReset() {
        return isReset;
    }

    /**
     * Returns {@code true} if the associated user account is available for use.
     *
     * @return <CODE>true</CODE> if the associated user account is available, or
     *         <CODE>false</CODE> if not.
     */
    public boolean isUsable() {
        return isUsable;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("AccountUsableResponseControl(oid=");
        builder.append(getOID());
        builder.append(", criticality=");
        builder.append(isCritical());
        builder.append(", isUsable=");
        builder.append(isUsable);
        if (isUsable) {
            builder.append(",secondsBeforeExpiration=");
            builder.append(secondsBeforeExpiration);
        } else {
            builder.append(",isInactive=");
            builder.append(isInactive);
            builder.append(",isReset=");
            builder.append(isReset);
            builder.append(",isExpired=");
            builder.append(isExpired);
            builder.append(",remainingGraceLogins=");
            builder.append(remainingGraceLogins);
            builder.append(",isLocked=");
            builder.append(isLocked);
            builder.append(",secondsBeforeUnlock=");
            builder.append(secondsBeforeUnlock);
        }
        builder.append(")");
        return builder.toString();
    }
}
