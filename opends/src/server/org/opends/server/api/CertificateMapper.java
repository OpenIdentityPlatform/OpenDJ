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
package org.opends.server.api;
import org.opends.messages.Message;



import java.security.cert.Certificate;
import java.util.List;

import org.opends.server.admin.std.server.CertificateMapperCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required to uniquely map an SSL client certificate to
 * a Directory Server user entry.
 *
 * @param  <T>  The type of configuration handled by this certificate
 *              mapper.
 */
public abstract class CertificateMapper
       <T extends CertificateMapperCfg>
{
  /**
   * Initializes this certificate mapper based on the information in
   * the provided configuration entry.
   *
   * @param  configuration  The configuration that should be used to
   *                        intialize this certificate mapper.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid certificate mapper
   *                           configuration.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeCertificateMapper(T configuration)
         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this certificate mapper.  It should be possible to call this
   * method on an uninitialized certificate mapper instance in order
   * to determine whether the certificate mapper would be able to use
   * the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The certificate mapper configuration
   *                              for which to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this certificate mapper, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      CertificateMapperCfg configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by certificate mapper
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any finalization that may be necessary for this
   * certificate mapper.  By default, no finalization is performed.
   */
  public void finalizeCertificateMapper()
  {
    // No implementation is required by default.
  }



  /**
   * Establishes a mapping between the information in the provided
   * certificate chain and a single user entry in the Directory
   * Server.
   *
   * @param  certificateChain  The certificate chain presented by the
   *                           client during SSL negotiation.  The
   *                           peer certificate will be listed first,
   *                           followed by the ordered issuer chain
   *                           as appropriate.
   *
   * @return  The entry for the user to whom the mapping was
   *          established, or <CODE>null</CODE> if no mapping was
   *          established and no special message is required to send
   *          back to the client.
   *
   * @throws  DirectoryException  If a problem occurred while
   *                              attempting to establish the mapping.
   *                              This may include internal failures,
   *                              a mapping which matches multiple
   *                              users, or any other case in which an
   *                              error message should be returned to
   *                              the client.
   */
  public abstract Entry mapCertificateToUser(Certificate[]
                                                  certificateChain)
         throws DirectoryException;
}

