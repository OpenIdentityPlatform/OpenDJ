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
 *      Portions copyright 2012-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import java.util.Collection;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;

/**
 * The start TLS extended request as defined in RFC 4511. The Start Transport
 * Layer Security (StartTLS) operation's purpose is to initiate installation of
 * a TLS layer.
 * <p>
 * Use an {@link org.forgerock.opendj.ldap.SSLContextBuilder SSLContextBuilder}
 * when setting up LDAP options needed to use StartTLS.
 * {@link org.forgerock.opendj.ldap.TrustManagers TrustManagers} has methods you
 * can use to set the trust manager for the SSL context builder.
 *
 * <pre>
 * LDAPOptions options = new LDAPOptions();
 * SSLContext sslContext =
 *         new SSLContextBuilder().setTrustManager(...).getSSLContext();
 * options.setSSLContext(sslContext);
 * options.setUseStartTLS(true);
 *
 * String host = ...;
 * int port = ...;
 * LDAPConnectionFactory factory = new LDAPConnectionFactory(host, port, options);
 * Connection connection = factory.getConnection();
 * // Connection uses StartTLS...
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4511">RFC 4511 - Lightweight
 *      Directory Access Protocol (LDAP): The Protocol </a>
 */
public interface StartTLSExtendedRequest extends ExtendedRequest<ExtendedResult> {

    /**
     * A decoder which can be used to decode start TLS extended operation
     * requests.
     */
    ExtendedRequestDecoder<StartTLSExtendedRequest, ExtendedResult> DECODER =
            new StartTLSExtendedRequestImpl.RequestDecoder();

    /**
     * The OID for the start TLS extended operation request.
     */
    String OID = "1.3.6.1.4.1.1466.20037";

    @Override
    StartTLSExtendedRequest addControl(Control control);

    /**
     * Adds the cipher suites enabled for secure connections with the Directory
     * Server. The suites must be supported by the SSLContext specified in
     * {@link #setSSLContext(SSLContext)}. Following a successful call to this
     * method, only the suites listed in the protocols parameter are enabled for
     * use.
     *
     * @param suites
     *            Names of all the suites to enable.
     * @return A reference to this LDAP connection options.
     * @throws UnsupportedOperationException
     *             If this start TLS extended request does not permit the
     *             enabled cipher suites to be set.
     */
    StartTLSExtendedRequest addEnabledCipherSuite(String... suites);

    /**
     * Adds the cipher suites enabled for secure connections with the Directory
     * Server. The suites must be supported by the SSLContext specified in
     * {@link #setSSLContext(SSLContext)}. Following a successful call to this
     * method, only the suites listed in the protocols parameter are enabled for
     * use.
     *
     * @param suites
     *            Names of all the suites to enable.
     * @return A reference to this LDAP connection options.
     * @throws UnsupportedOperationException
     *             If this start TLS extended request does not permit the
     *             enabled cipher suites to be set.
     */
    StartTLSExtendedRequest addEnabledCipherSuite(Collection<String> suites);

    /**
     * Adds the protocol versions enabled for secure connections with the
     * Directory Server. The protocols must be supported by the SSLContext
     * specified in {@link #setSSLContext(SSLContext)}. Following a successful
     * call to this method, only the protocols listed in the protocols parameter
     * are enabled for use.
     *
     * @param protocols
     *            Names of all the protocols to enable.
     * @return A reference to this LDAP connection options.
     * @throws UnsupportedOperationException
     *             If this start TLS extended request does not permit the
     *             enabled protocols to be set.
     */
    StartTLSExtendedRequest addEnabledProtocol(String... protocols);

    /**
     * Adds the protocol versions enabled for secure connections with the
     * Directory Server. The protocols must be supported by the SSLContext
     * specified in {@link #setSSLContext(SSLContext)}. Following a successful
     * call to this method, only the protocols listed in the protocols parameter
     * are enabled for use.
     *
     * @param protocols
     *            Names of all the protocols to enable.
     * @return A reference to this LDAP connection options.
     * @throws UnsupportedOperationException
     *             If this start TLS extended request does not permit the
     *             enabled protocols to be set.
     */
    StartTLSExtendedRequest addEnabledProtocol(Collection<String> protocols);

    @Override
    <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
            throws DecodeException;

    @Override
    List<Control> getControls();

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return an array of protocols or empty set if the default protocols are
     *         to be used.
     */
    List<String> getEnabledCipherSuites();

    /**
     * Returns the names of the protocol versions which are currently enabled
     * for secure connections with the Directory Server.
     *
     * @return an array of protocols or empty set if the default protocols are
     *         to be used.
     */
    List<String> getEnabledProtocols();

    @Override
    String getOID();

    @Override
    ExtendedResultDecoder<ExtendedResult> getResultDecoder();

    /**
     * Returns the SSLContext that should be used when installing the TLS layer.
     *
     * @return The SSLContext that should be used when installing the TLS layer.
     */
    SSLContext getSSLContext();

    @Override
    ByteString getValue();

    @Override
    boolean hasValue();

    /**
     * Sets the SSLContext that should be used when installing the TLS layer.
     *
     * @param sslContext
     *            The SSLContext that should be used when installing the TLS
     *            layer.
     * @return This startTLS request.
     */
    StartTLSExtendedRequest setSSLContext(SSLContext sslContext);
}
