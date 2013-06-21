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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

/**
 * The Extended operation allows additional operations to be defined for
 * services not already available in the protocol; for example, to implement an
 * operation which installs transport layer security (see
 * {@link StartTLSExtendedRequest}).
 * <p>
 * To determine whether a directory server supports a given extension, read the
 * list of supported extensions from the root DSE to get a collection of
 * extension OIDs, and then check for a match. For example:
 *
 * <pre>
 * Connection connection = ...;
 * Collection&lt;String&gt; supported =
 *     RootDSE.readRootDSE(connection).getSupportedExtendedOperations();
 *
 * ExtendedRequest extension = ...;
 * String OID = extension.getOID();
 * if (supported != null && !supported.isEmpty() && supported.contains(OID)) {
 *     // The extension is supported. Use it here...
 * }
 * </pre>
 *
 * @param <S>
 *            The type of result.
 */
public interface ExtendedRequest<S extends ExtendedResult> extends Request {

    @Override
    ExtendedRequest<S> addControl(Control control);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the numeric OID associated with this extended request.
     *
     * @return The numeric OID associated with this extended request.
     */
    String getOID();

    /**
     * Returns a decoder which can be used to decoded responses to this extended
     * request.
     *
     * @return A decoder which can be used to decoded responses to this extended
     *         request.
     */
    ExtendedResultDecoder<S> getResultDecoder();

    /**
     * Returns the value, if any, associated with this extended request. Its
     * format is defined by the specification of this extended request.
     *
     * @return The value associated with this extended request, or {@code null}
     *         if there is no value.
     */
    ByteString getValue();

    /**
     * Returns {@code true} if this extended request has a value. In some
     * circumstances it may be useful to determine if a extended request has a
     * value, without actually calculating the value and incurring any
     * performance costs.
     *
     * @return {@code true} if this extended request has a value, or
     *         {@code false} if there is no value.
     */
    boolean hasValue();

}
