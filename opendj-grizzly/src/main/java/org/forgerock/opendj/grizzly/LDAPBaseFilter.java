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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.grizzly;

import org.forgerock.opendj.ldap.DecodeOptions;
import org.glassfish.grizzly.filterchain.BaseFilter;

/**
 * Base class for LDAP-enabled filter.
 */
abstract class LDAPBaseFilter extends BaseFilter {

    /**
     * The maximum BER element size, or <code>0</code> to indicate that there is
     * no limit.
     */
    final int maxASN1ElementSize;

    /**
     * Allow to control how to decode requests and responses.
     */
    final DecodeOptions decodeOptions;

    /**
     * Creates a filter with provided decode options and max size of
     * ASN1 element.
     *
     * @param options
     *            control how to decode requests and responses
     * @param maxASN1ElementSize
     *            The maximum BER element size, or <code>0</code> to indicate
     *            that there is no limit.
     */
    LDAPBaseFilter(final DecodeOptions options, final int maxASN1ElementSize) {
        this.decodeOptions = options;
        this.maxASN1ElementSize = maxASN1ElementSize;
    }
}
