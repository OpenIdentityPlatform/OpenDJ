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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2012 ForgeRock AS.
 */

package org.opends.server.extensions;



import java.nio.channels.ByteChannel;
import java.security.cert.Certificate;



/**
 * This interface can be used to define connection security providers.
 */
public interface ConnectionSecurityProvider
{

  /**
   * Return a certificate chain array.
   *
   * @return A certificate chain array.
   */
  Certificate[] getClientCertificateChain();



  /**
   * Return the name of a provider.
   *
   * @return String representing the name of a provider.
   */
  String getName();



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
   * Returns the security provider's byte channel.
   *
   * @return The security provider's byte channel.
   */
  ByteChannel getChannel();
}
