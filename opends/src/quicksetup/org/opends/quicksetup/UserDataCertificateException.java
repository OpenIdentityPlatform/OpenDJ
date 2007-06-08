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

package org.opends.quicksetup;

import java.security.cert.X509Certificate;

/**
 * This exception is used when there is a certificate issue and the user must
 * be asked to accept it or not.
 * It will be thrown by the class that is in charge of validating the user data
 * (the Application class).
 *
 */
public class UserDataCertificateException extends UserDataException
{
  private String host;
  private int port;
  private X509Certificate[] chain;
  private String authType;
  private Type type;
  /**
   * The enumeration for the different types of the exception.
   */
  public enum Type
  {
    /**
     * The certificate was not trusted.
     */
    NOT_TRUSTED,
    /**
     * The certificate's subject DN's value and the host name we tried to
     * connect to do not match.
     */
    HOST_NAME_MISMATCH
  }

  private static final long serialVersionUID = 6404258710409027956L;

  /**
   * Constructor for UserDataCertificateException.
   * @param step the step in the wizard where the exception occurred.
   * @param localizedMessage the localized message describing the error.
   * @param t the root cause for this exception.
   * @param host the host we tried to connect to.
   * @param port the port we tried to connect to.
   * @param chain the certificate chain.
   * @param authType the authentication type.
   * @param type the type of the exception.
   */
  public UserDataCertificateException(WizardStep step, String localizedMessage,
      Throwable t, String host, int port, X509Certificate[] chain,
      String authType, Type type)
  {
    super(step, localizedMessage);
    initCause(t);
    this.host = host;
    this.port = port;
    this.chain = chain;
    this.authType = authType;
    this.type = type;
  }

  /**
   * Returns the host we tried to connect to when this exception was generated.
   * @return the host we tried to connect to when this exception was generated.
   */
  public String getHost()
  {
    return host;
  }

  /**
   * Returns the port we tried to connect to when this exception was generated.
   * @return the port we tried to connect to when this exception was generated.
   */
  public int getPort()
  {
    return port;
  }

  /**
   * Returns the auth type used when this certificate exception occurred.
   * @return the auth type used when this certificate exception occurred.
   */
  public String getAuthType()
  {
    return authType;
  }

  /**
   * Returns the type of exception.
   * @return the type of exception.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the certificate chain received when this exception was generated.
   * @return the certificate chain received when this exception was generated.
   */
  public X509Certificate[] getChain()
  {
    return chain;
  }
}


