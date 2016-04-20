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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;



import java.security.cert.CertificateException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.X509TrustManager;



import static org.opends.messages.UtilityMessages.*;


/**
 * This class implements an X.509 trust manager that will be used to wrap an
 * existing trust manager and makes it possible to reject a presented
 * certificate if that certificate is outside the validity window.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class ExpirationCheckTrustManager
       implements X509TrustManager
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The trust manager that is wrapped by this trust manager. */
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
  @Override
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
        logger.error(ERR_EXPCHECK_TRUSTMGR_CLIENT_CERT_EXPIRED,
            c.getSubjectDN().getName(), c.getNotAfter());
        throw cee;
      }
      catch (CertificateNotYetValidException cnyve)
      {
        logger.error(ERR_EXPCHECK_TRUSTMGR_CLIENT_CERT_NOT_YET_VALID,
            c.getSubjectDN().getName(), c.getNotBefore());
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
  @Override
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
        logger.error(ERR_EXPCHECK_TRUSTMGR_SERVER_CERT_EXPIRED,
            c.getSubjectDN().getName(), c.getNotAfter());
        throw cee;
      }
      catch (CertificateNotYetValidException cnyve)
      {
        logger.error(ERR_EXPCHECK_TRUSTMGR_SERVER_CERT_NOT_YET_VALID,
            c.getSubjectDN().getName(), c.getNotBefore());
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
  @Override
  public X509Certificate[] getAcceptedIssuers()
  {
    return trustManager.getAcceptedIssuers();
  }
}

