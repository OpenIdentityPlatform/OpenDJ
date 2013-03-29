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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.responses;

import java.util.List;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;

/**
 * A Search Result Reference represents an area not yet explored during a Search
 * operation.
 */
public interface SearchResultReference extends Response {

    @Override
    SearchResultReference addControl(Control control);

    /**
     * Adds the provided continuation reference URI to this search result
     * reference.
     *
     * @param uri
     *            The continuation reference URI to be added.
     * @return This search result reference.
     * @throws UnsupportedOperationException
     *             If this search result reference does not permit continuation
     *             reference URI to be added.
     * @throws NullPointerException
     *             If {@code uri} was {@code null}.
     */
    SearchResultReference addURI(String uri);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns a {@code List} containing the continuation reference URIs
     * included with this search result reference. The returned {@code List} may
     * be modified if permitted by this search result reference.
     *
     * @return A {@code List} containing the continuation reference URIs.
     */
    List<String> getURIs();

}
