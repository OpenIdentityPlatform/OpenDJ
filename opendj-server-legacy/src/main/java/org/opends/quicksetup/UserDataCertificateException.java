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

package org.opends.quicksetup;
import org.forgerock.i18n.LocalizableMessage;

import java.security.cert.X509Certificate;

/**
 * This exception is used when there is a certificate issue and the user must
 * be asked to accept it or not.
 * It will be thrown by the class that is in charge of validating the user data
 * (the Application class).
 */
public class UserDataCertificateException extends UserDataException
{
  private String host;
  private int port;
  private X509Certificate[] chain;
  private String authType;
  private Type type;
  /** The enumeration for the different types of the exception. */
  public enum Type
  {
    /** The certificate was not trusted. */
    NOT_TRUSTED,
    /** The certificate's subject DN's value and the host name we tried to connect to do not match. */
    HOST_NAME_MISMATCH
  }

  private static final long serialVersionUID = 6404258710409027956L;

  /**
   * Constructor for UserDataCertificateException.
   * @param step the step in the wizard where the exception occurred.
   * @param message describing the error.
   * @param t the root cause for this exception.
   * @param host the host we tried to connect to.
   * @param port the port we tried to connect to.
   * @param chain the certificate chain.
   * @param authType the authentication type.
   * @param type the type of the exception.
   */
  public UserDataCertificateException(WizardStep step, LocalizableMessage message,
      Throwable t, String host, int port, X509Certificate[] chain,
      String authType, Type type)
  {
    super(step, message, t);
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


