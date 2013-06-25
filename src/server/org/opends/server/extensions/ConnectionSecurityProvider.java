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
 *      Portions copyright 2012 ForgeRock AS.
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
