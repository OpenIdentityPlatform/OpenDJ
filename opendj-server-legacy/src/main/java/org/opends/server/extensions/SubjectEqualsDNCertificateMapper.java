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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.SubjectEqualsDNCertificateMapperCfg;
import org.opends.server.api.CertificateMapper;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class implements a very simple Directory Server certificate mapper that
 * will map a certificate to a user only if the subject of the peer certificate
 * exactly matches the DN of a user in the Directory Server.
 */
public class SubjectEqualsDNCertificateMapper
       extends CertificateMapper<SubjectEqualsDNCertificateMapperCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectEqualsDNCertificateMapper()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializeCertificateMapper(SubjectEqualsDNCertificateMapperCfg
                                               configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Establishes a mapping between the information in the provided certificate
   * chain to the DN of a single user in the Directory Server.
   *
   * @param  certificateChain  The certificate chain presented by the client
   *                           during SSL negotiation.  The peer certificate
   *                           will be listed first, followed by the ordered
   *                           issuer chain as appropriate.
   *
   * @return  The DN of the one user to whom the mapping was established, or
   *          <CODE>null</CODE> if no mapping was established and no special
   *         message is required to send back to the client.
   *
   * @throws  DirectoryException  If a problem occurred while attempting to
   *                              establish the mapping.  This may include
   *                              internal failures, a mapping which matches
   *                              multiple users, or any other case in which an
   *                              error message should be returned to the
   *                              client.
   */
  @Override
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    // Make sure that a peer certificate was provided.
    if (certificateChain == null || certificateChain.length == 0)
    {
      LocalizableMessage message = ERR_SEDCM_NO_PEER_CERTIFICATE.get();
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the first certificate in the chain.  It must be an X.509 certificate.
    X509Certificate peerCertificate;
    try
    {
      peerCertificate = (X509Certificate) certificateChain[0];
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SEDCM_PEER_CERT_NOT_X509.get(certificateChain[0].getType());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the subject from the peer certificate and decode it as a DN.
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    DN subjectDN;
    try
    {
      subjectDN = DN.valueOf(peerPrincipal.getName(X500Principal.RFC2253));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SEDCM_CANNOT_DECODE_SUBJECT_AS_DN.get(peerPrincipal, getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }

    // Retrieve the entry with the specified DN from the directory.
    Entry userEntry;
    try
    {
      userEntry = DirectoryServer.getEntry(subjectDN);
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      LocalizableMessage message = ERR_SEDCM_CANNOT_GET_ENTRY.get(subjectDN, de.getMessageObject());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message, de);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_SEDCM_CANNOT_GET_ENTRY.get(subjectDN, getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message, e);
    }

    if (userEntry == null)
    {
      LocalizableMessage message = ERR_SEDCM_NO_USER_FOR_DN.get(subjectDN);
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }
    else
    {
      return userEntry;
    }
  }
}

