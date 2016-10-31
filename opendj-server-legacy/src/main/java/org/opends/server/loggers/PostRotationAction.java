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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

/** This interface describes the action that needs to be taken at the time of a rotation. */
interface PostRotationAction
{
  /**
   * The action that needs to be executed. Returns true
   * if the action succeeded and false otherwise.
   *
   * @return  <CODE>true</CODE> if the rotation action was successful, or
   *          <CODE>false</CODE> if it was not.
   */
  boolean execute();
}
