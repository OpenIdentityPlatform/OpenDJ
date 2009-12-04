package com.sun.opends.sdk.util;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * Created by IntelliJ IDEA.
 * User: boli
 * Date: Oct 22, 2009
 * Time: 3:10:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class SSLUtils
{
  public static SSLContext getSSLContext(TrustManager trustManager,
                                         KeyManager keyManager)
      throws KeyManagementException, NoSuchAlgorithmException {
    TrustManager[] tm = null;
    if (trustManager != null)
    {
      tm = new TrustManager[] {trustManager};
    }

    KeyManager[] km = null;
    if (keyManager != null)
    {
      km = new KeyManager[] {keyManager};
    }

    SSLContext sslContext = SSLContext.getInstance("TLSv1");
    sslContext.init(km, tm, null);

    return sslContext;
  }
}
