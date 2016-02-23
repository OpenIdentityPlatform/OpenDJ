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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This interface defines a set of methods that must be implemented by a class
 * (expected to be a client connection) that can dynamically enable and disable
 * the TLS connection security provider.  This will be used by the StartTLS
 * extended operation handler to perform the core function of enabling TLS on an
 * established connection.
 */
public interface TLSCapableConnection
{
  /**
   * Prepares this connection for using TLS and returns whether TLS protection
   * is actually available for the underlying client connection. If there is any
   * reason that TLS protection cannot be enabled on this client connection,
   * then it should be appended to the provided buffer.
   *
   * @param  unavailableReason  The buffer used to hold the reason that TLS is
   *                            not available on the underlying client
   *                            connection.
   *
   * @return  <CODE>true</CODE> if TLS is available on the underlying client
   *          connection, or <CODE>false</CODE> if it is not.
   */
  boolean prepareTLS(LocalizableMessageBuilder unavailableReason);
}

