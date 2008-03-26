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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.quicksetup.util;

import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * The exception representing an incompatible java version being used.  Even
 * if the code can be run under 1.5, some bugs have been found in some versions
 * of the JVM that prevent OpenDS to work properly (see
 */
public class IncompatibleVersionException extends OpenDsException
{

  private static final long serialVersionUID = 4283735375192567277L;
  /**
   * Constructor of the IncompatibleVersionException.
   * @param msg the error message.
   * @param rootCause the root cause.
   */
  public IncompatibleVersionException(Message msg, Throwable rootCause)
  {
    super(msg, rootCause);
  }
}
