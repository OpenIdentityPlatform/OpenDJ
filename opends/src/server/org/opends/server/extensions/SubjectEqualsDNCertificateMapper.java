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



import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.concurrent.locks.Lock;

import org.opends.messages.Message;
import org.opends.server.admin.std.server.SubjectEqualsDNCertificateMapperCfg;
import org.opends.server.api.CertificateMapper;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
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
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * Creates a new instance of this certificate mapper.  Note that all actual
   * initialization should be done in the
   * <CODE>initializeCertificateMapper</CODE> method.
   */
  public SubjectEqualsDNCertificateMapper()
  {
    super();

  }



  /**
   * {@inheritDoc}
   */
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
  public Entry mapCertificateToUser(Certificate[] certificateChain)
         throws DirectoryException
  {
    // Make sure that a peer certificate was provided.
    if ((certificateChain == null) || (certificateChain.length == 0))
    {
      Message message = ERR_SEDCM_NO_PEER_CERTIFICATE.get();
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_SEDCM_PEER_CERT_NOT_X509.get(
          String.valueOf(certificateChain[0].getType()));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Get the subject from the peer certificate and decode it as a DN.
    X500Principal peerPrincipal = peerCertificate.getSubjectX500Principal();
    DN subjectDN;
    try
    {
      subjectDN = DN.decode(peerPrincipal.getName(X500Principal.RFC2253));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_SEDCM_CANNOT_DECODE_SUBJECT_AS_DN.get(
          String.valueOf(peerPrincipal), getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }


    // Acquire a read lock on the user entry.  If this fails, then so will the
    // certificate mapping.
    Lock readLock = null;
    for (int i=0; i < 3; i++)
    {
      readLock = LockManager.lockRead(subjectDN);
      if (readLock != null)
      {
        break;
      }
    }

    if (readLock == null)
    {
      Message message =
          ERR_SEDCM_CANNOT_LOCK_ENTRY.get(String.valueOf(subjectDN));
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      Message message = ERR_SEDCM_CANNOT_GET_ENTRY.get(
          String.valueOf(subjectDN), de.getMessageObject());
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   de);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_SEDCM_CANNOT_GET_ENTRY.get(
          String.valueOf(subjectDN), getExceptionMessage(e));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message,
                                   e);
    }
    finally
    {
      LockManager.unlock(subjectDN, readLock);
    }


    if (userEntry == null)
    {
      Message message = ERR_SEDCM_NO_USER_FOR_DN.get(String.valueOf(subjectDN));
      throw new DirectoryException(ResultCode.INVALID_CREDENTIALS, message);
    }
    else
    {
      return userEntry;
    }
  }
}

