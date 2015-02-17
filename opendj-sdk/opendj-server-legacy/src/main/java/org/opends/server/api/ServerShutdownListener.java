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
package org.opends.server.api;
import org.forgerock.i18n.LocalizableMessage;




/**
 * This interface defines a method that may be used to notify various
 * Directory Server components that the server is shutting down.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public interface ServerShutdownListener
{
  /**
   * Retrieves the human-readable name for this shutdown listener.
   *
   * @return  The human-readable name for this shutdown listener.
   */
  String getShutdownListenerName();



  /**
   * Indicates that the Directory Server has received a request to
   * stop running and that this shutdown listener should take any
   * action necessary to prepare for it.
   *
   * @param  reason  The human-readable reason for the shutdown.
   */
  void processServerShutdown(LocalizableMessage reason);
}

