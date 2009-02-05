/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.server.extensions;

import java.nio.channels.ByteChannel;
import java.security.cert.Certificate;

/**
 * This interface can be used to define connection security providers.
 *
 */
public interface ConnectionSecurityProvider {

    /**
     * Factory method: creates a new security ByteChannel
     * layer wrapping the provided ByteChannel.
     *
     * @param channel The byte channel to be wrapped.
     * @return A byte channel wrapping the specified byte channel.
     */
    ByteChannel wrapChannel(ByteChannel channel);

    /**
     * Return a buffer size of the byte channel.
     * @return Integer representing the byte channel application buffer size.
     */
    int getAppBufSize();

    /**
     * Return a certificate chain array.
     *
     * @return A certificate chain array.
     */
    Certificate[] getClientCertificateChain();

    /**
     * Return a Security Strength Factor.
     *
     * @return Integer representing the current SSF of a provider.
     */
    int getSSF();

    /**
     * Return <CODE>true</CODE> if a provider is secure.
     *
     * @return <CODE>true</CODE> if a provider is secure.
     */
    boolean isSecure();

    /**
     * Return the name of a provider.
     *
     * @return String representing the name of a provider.
     */
    String getName();
}
