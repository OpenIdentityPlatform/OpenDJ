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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
 */
package org.opends.quicksetup.util;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.types.OpenDsException;

/**
 * The exception representing an incompatible java version being used.  Even
 * if the code can be run under 1.6, some bugs might be found that prevent from
 * using some of the 1.6 releases.
 */
public class IncompatibleVersionException extends OpenDsException
{

  private static final long serialVersionUID = 4283735375192567277L;
  /**
   * Constructor of the IncompatibleVersionException.
   * @param msg the error message.
   * @param rootCause the root cause.
   */
  public IncompatibleVersionException(LocalizableMessage msg, Throwable rootCause)
  {
    super(msg, rootCause);
  }
}
