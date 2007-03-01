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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of an X.509 trust manager which will
 * interactively prompt the user (via the CLI) whether a given certificate
 * should be trusted.  It should only be used by interactive command-line tools,
 * since it will block until it gets a response from the user.
 * <BR><BR>
 * Note that this class is only intended for client-side use, and therefore may
 * not be used by a server to determine whether a client certificate is trusted.
 */
public class PromptTrustManager
       implements X509TrustManager
{



  // The singleton trust manager array for this class.
  private static TrustManager[] trustManagerArray =
       new TrustManager[] { new PromptTrustManager() };



  /**
   * Creates a new instance of this prompt trust manager.
   */
  private PromptTrustManager()
  {

    // No implementation is required.
  }



  /**
   * Retrieves the trust manager array that should be used to initialize an SSL
   * context in cases where the user should be interactively prompted about
   * whether to trust the server certificate.
   *
   * @return  The trust manager array that should be used to initialize an SSL
   *          context in cases where the user should be interactively prompted
   *          about whether to trust the server certificate.
   */
  public static TrustManager[] getTrustManagers()
  {

    return trustManagerArray;
  }



  /**
   * Determines whether an SSL client with the provided certificate chain should
   * be trusted.  This implementation is not intended for server-side use, and
   * therefore this method will always throw an exception.
   *
   * @param  chain     The certificate chain for the SSL client.
   * @param  authType  The authentication type based on the client certificate.
   *
   * @throws  CertificateException  To indicate that the provided client
   *                                certificate is not trusted.
   */
  public void checkClientTrusted(X509Certificate[] chain, String authType)
         throws CertificateException
  {

    int    msgID   = MSGID_PROMPTTM_REJECTING_CLIENT_CERT;
    String message = getMessage(msgID);
    throw new CertificateException(message);
  }



  /**
   * Determines whether an SSL server with the provided certificate chain should
   * be trusted.  In this case, the user will be interactively prompted as to
   * whether the certificate should be trusted.
   *
   * @param  chain     The certificate chain for the SSL server.
   * @param  authType  The key exchange algorithm used.
   *
   * @throws  CertificateException  If the user rejects the certificate.
   */
  public void checkServerTrusted(X509Certificate[] chain, String authType)
         throws CertificateException
  {

    if ((chain == null) || (chain.length == 0))
    {
      System.out.println(getMessage(MSGID_PROMPTTM_NO_SERVER_CERT_CHAIN));
    }
    else
    {
      Date currentDate   = new Date();
      Date notAfterDate  = chain[0].getNotAfter();
      Date notBeforeDate = chain[0].getNotBefore();

      if (currentDate.after(notAfterDate))
      {
        int    msgID   = MSGID_PROMPTTM_CERT_EXPIRED;
        String message = getMessage(msgID, String.valueOf(notAfterDate));
        System.err.println(message);
      }
      else if (currentDate.before(notBeforeDate))
      {
        int    msgID   = MSGID_PROMPTTM_CERT_NOT_YET_VALID;
        String message = getMessage(msgID, String.valueOf(notBeforeDate));
        System.err.println(message);
      }

      System.out.println(getMessage(MSGID_PROMPTTM_SERVER_CERT,
                                    chain[0].getSubjectDN().getName(),
                                    chain[0].getIssuerDN().getName(),
                                    String.valueOf(notBeforeDate),
                                    String.valueOf(notAfterDate)));
    }


    String prompt = getMessage(MSGID_PROMPTTM_YESNO_PROMPT);
    BufferedReader reader =
         new BufferedReader(new InputStreamReader(System.in));
    while (true)
    {
      try
      {
        System.out.print(prompt);
        String line = reader.readLine().toLowerCase();
        if (line.equals("y") || line.equals("yes"))
        {
          // Returning without an exception is sufficient to consider the
          // certificate trusted.
          return;
        }
        else if (line.equals("n") || line.equals("no"))
        {
          int msgID = MSGID_PROMPTTM_USER_REJECTED;
          String message = getMessage(msgID);
          throw new CertificateException(message);
        }
      } catch (IOException ioe) {}

      System.out.println();
    }
  }



  /**
   * Retrieves the set of certificate authority certificates which are trusted
   * for authenticating peers.
   *
   * @return  An empty array, since we don't care what certificates are
   *          presented because we will always prompt the user.
   */
  public X509Certificate[] getAcceptedIssuers()
  {

    return new X509Certificate[0];
  }
}

