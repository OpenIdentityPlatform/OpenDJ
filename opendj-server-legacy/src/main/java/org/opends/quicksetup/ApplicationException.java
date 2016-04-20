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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * This exception is used to encapsulate all the error that we might have
 * during the installation.
 *
 * @see org.opends.quicksetup.installer.Installer
 */
public class ApplicationException extends OpenDsException {

  private static final long serialVersionUID = -3527273444231560341L;

  private ReturnCode type;

  /**
   * Creates a new ApplicationException of type FILE_SYSTEM_ERROR.
   * @param msg localized exception message
   * @param e Exception cause
   * @return ApplicationException with Type property being FILE_SYSTEM_ERROR
   */
  public static ApplicationException createFileSystemException(LocalizableMessage msg, Exception e)
  {
    return new ApplicationException(ReturnCode.FILE_SYSTEM_ACCESS_ERROR, msg, e);
  }

  /**
   * The constructor of the ApplicationException.
   *
   * @param type
   *          the type of error we have.
   * @param localizedMsg
   *          a localized string describing the problem.
   * @param rootCause
   *          the root cause of this exception.
   */
  public ApplicationException(ReturnCode type, LocalizableMessage localizedMsg,
                              Throwable rootCause)
  {
    super(localizedMsg, rootCause);
    this.type = type;
  }

  /**
   * Returns the Type of this exception.
   * @return the Type of this exception.
   */
  public ReturnCode getType()
  {
    return type;
  }

  @Override
  public String toString()
  {
    return getMessage();
  }
}
