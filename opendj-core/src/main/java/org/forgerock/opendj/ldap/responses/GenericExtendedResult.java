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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Generic Extended result indicates the final status of an Generic Extended
 * operation.
 */
public interface GenericExtendedResult extends ExtendedResult {

    @Override
    GenericExtendedResult addControl(Control control);

    @Override
    GenericExtendedResult addReferralURI(String uri);

    @Override
    Throwable getCause();

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    @Override
    String getDiagnosticMessage();

    @Override
    String getMatchedDN();

    @Override
    String getOID();

    @Override
    List<String> getReferralURIs();

    @Override
    ResultCode getResultCode();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    @Override
    boolean isReferral();

    @Override
    boolean isSuccess();

    @Override
    GenericExtendedResult setCause(Throwable cause);

    @Override
    GenericExtendedResult setDiagnosticMessage(String message);

    @Override
    GenericExtendedResult setMatchedDN(String dn);

    /**
     * Sets the numeric OID, if any, associated with this extended result.
     *
     * @param oid
     *            The numeric OID associated with this extended result, or
     *            {@code null} if there is no value.
     * @return This generic extended result.
     * @throws UnsupportedOperationException
     *             If this generic extended result does not permit the result
     *             name to be set.
     */
    GenericExtendedResult setOID(String oid);

    @Override
    GenericExtendedResult setResultCode(ResultCode resultCode);

    /**
     * Sets the value, if any, associated with this extended result. Its format
     * is defined by the specification of this extended result.
     * <p>
     * If {@code value} is not an instance of {@code ByteString} then it will be
     * converted using the {@link ByteString#valueOfObject(Object)} method.
     *
     * @param value
     *            The value associated with this extended result, or
     *            {@code null} if there is no value.
     * @return This generic extended result.
     * @throws UnsupportedOperationException
     *             If this generic extended result does not permit the result
     *             value to be set.
     */
    GenericExtendedResult setValue(Object value);

}
