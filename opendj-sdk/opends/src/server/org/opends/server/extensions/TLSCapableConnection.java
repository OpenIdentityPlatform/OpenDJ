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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;



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
   * Indicates whether TLS protection is actually available for the underlying
   * client connection.  If there is any reason that TLS protection cannot be
   * enabled on this client connection, then it should be appended to the
   * provided buffer.
   *
   * @param  unavailableReason  The buffer used to hold the reason that TLS is
   *                            not available on the underlying client
   *                            connection.
   *
   * @return  <CODE>true</CODE> if TLS is available on the underlying client
   *          connection, or <CODE>false</CODE> if it is not.
   */
  public boolean tlsProtectionAvailable(StringBuilder unavailableReason);



  /**
   * Installs the TLS connection security provider on this client connection.
   * If an error occurs in the process, then the underlying client connection
   * must be terminated and an exception must be thrown to indicate the
   * underlying cause.
   *
   * @throws  DirectoryException  If the TLS connection security provider could
   *                              not be enabled and the underlying connection
   *                              has been closed.
   */
  public void enableTLSConnectionSecurityProvider()
         throws DirectoryException;



  /**
   * Disables the TLS connection security provider on this client connection.
   * This must also eliminate any authentication that had been performed on the
   * client connection so that it is in an anonymous state.  If a problem occurs
   * while attempting to revert the connection to a non-TLS-protected state,
   * then an exception must be thrown and the client connection must be
   * terminated.
   *
   * @throws  DirectoryException  If TLS protection cannot be reverted and the
   *                              underlying client connection has been closed.
   */
  public void disableTLSConnectionSecurityProvider()
         throws DirectoryException;



  /**
   * Sends a response to the client in the clear rather than through the
   * encrypted channel.  This should only be used when processing the StartTLS
   * extended operation to send the response in the clear after the SSL
   * negotiation has already been initiated.
   *
   * @param  operation  The operation for which to send the response in the
   *                    clear.
   *
   * @throws  DirectoryException  If a problem occurs while sending the response
   *                              in the clear.
   */
  public void sendClearResponse(Operation operation)
         throws DirectoryException;
}

