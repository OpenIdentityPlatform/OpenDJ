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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.quicksetup;
import org.forgerock.i18n.LocalizableMessage;

import org.opends.server.types.OpenDsException;

/**
 * This exception is used to encapsulate all the error that we might have
 * during the installation.
 *
 * @see org.opends.quicksetup.installer.Installer
 * @see org.opends.quicksetup.installer.webstart.WebStartInstaller
 * @see org.opends.quicksetup.installer.offline.OfflineInstaller
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
  public static ApplicationException createFileSystemException(LocalizableMessage msg,
      Exception e)
  {
    return new ApplicationException(ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
        msg, e);
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

  /** {@inheritDoc} */
  public String toString()
  {
    return getMessage();
  }
}
