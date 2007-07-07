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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.X509TrustManager;

import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.UtilityMessages.*;



/**
 * This class implements an X.509 trust manager that will be used to wrap an
 * existing trust manager and makes it possible to reject a presented
 * certificate if that certificate is outside the validity window.
 */
public class ExpirationCheckTrustManager
       implements X509TrustManager
{
  // The trust manager that is wrapped by this trust manager.
  private X509TrustManager trustManager;



  /**
   * Creates a new instance of this trust manager that will wrap the provided
   * trust manager.
   *
   * @param  trustManager  The trust manager to be wrapped by this trust
   *                       manager.
   */
  public ExpirationCheckTrustManager(X509TrustManager trustManager)
  {
    this.trustManager = trustManager;
  }



  /**
   * Determines whether to trust the peer based on the provided certificate
   * chain.  In this case, the peer will only be trusted if all certificates in
   * the chain are within the validity window and the parent trust manager also
   * accepts the certificate.
   *
   * @param  chain     The peer certificate chain.
   * @param  authType  The authentication type based on the client certificate.
   *
   * @throws  CertificateException  If the client certificate chain is not
   *                                trusted.
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
         throws CertificateException
  {
    Date currentDate = new Date();
    for (X509Certificate c : chain)
    {
      try
      {
        c.checkValidity(currentDate);
      }
      catch (CertificateExpiredException cee)
      {
        int msgID = MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_EXPIRED;
        String message = getMessage(msgID, c.getSubjectDN().getName(),
                                    String.valueOf(c.getNotAfter()));
        logError(ErrorLogCategory.CONNECTION_HANDLING,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        throw cee;
      }
      catch (CertificateNotYetValidException cnyve)
      {
        int msgID = MSGID_EXPCHECK_TRUSTMGR_CLIENT_CERT_NOT_YET_VALID;
        String message = getMessage(msgID, c.getSubjectDN().getName(),
                                    String.valueOf(c.getNotBefore()));
        logError(ErrorLogCategory.CONNECTION_HANDLING,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        throw cnyve;
      }
    }

    trustManager.checkClientTrusted(chain, authType);
  }



  /**
   * Determines whether to trust the peer based on the provided certificate
   * chain.  In this case, the peer will only be trusted if all certificates in
   * the chain are within the validity window and the parent trust manager also
   * accepts the certificate.
   *
   * @param  chain     The peer certificate chain.
   * @param  authType  The key exchange algorithm used.
   *
   * @throws  CertificateException  If the server certificate chain is not
   *                                trusted.
   */
  public void checkServerTrusted(X509Certificate[] chain, String authType)
         throws CertificateException
  {
    Date currentDate = new Date();
    for (X509Certificate c : chain)
    {
      try
      {
        c.checkValidity(currentDate);
      }
      catch (CertificateExpiredException cee)
      {
        int msgID = MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_EXPIRED;
        String message = getMessage(msgID, c.getSubjectDN().getName(),
                                    String.valueOf(c.getNotAfter()));
        logError(ErrorLogCategory.CONNECTION_HANDLING,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        throw cee;
      }
      catch (CertificateNotYetValidException cnyve)
      {
        int msgID = MSGID_EXPCHECK_TRUSTMGR_SERVER_CERT_NOT_YET_VALID;
        String message = getMessage(msgID, c.getSubjectDN().getName(),
                                    String.valueOf(c.getNotBefore()));
        logError(ErrorLogCategory.CONNECTION_HANDLING,
                 ErrorLogSeverity.SEVERE_WARNING, message, msgID);

        throw cnyve;
      }
    }

    trustManager.checkServerTrusted(chain, authType);
  }



  /**
   * Retrieves the set of CA certificates which are trusted for authenticating
   * peers.  This will be taken from the parent trust manager.
   *
   * @return  A non-null (possibly empty) array of acceptable CA issuer
   *          certificates.
   */
  public X509Certificate[] getAcceptedIssuers()
  {
    return trustManager.getAcceptedIssuers();
  }
}

