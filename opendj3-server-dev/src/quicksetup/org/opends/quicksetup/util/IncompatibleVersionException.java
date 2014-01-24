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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
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
