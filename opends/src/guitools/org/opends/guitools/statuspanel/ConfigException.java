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

package org.opends.guitools.statuspanel;

import org.opends.messages.Message;
import org.opends.server.types.OpenDsException;

/**
 * Exception thrown when there is an error with the configuration (for instance
 * a valid URL for the requested protocol could not be found).
 */
public class ConfigException extends OpenDsException
{
  private static final long serialVersionUID = 1266482779183126905L;

  /**
   * Constructor for the exception.
   * @param msg the localized message to be used.
   */
  public ConfigException(Message msg)
  {
    super(msg);
  }
}
