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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



/**
 * This class defines a set of methods that are available for use by
 * pre-parse plugins for abandon operations.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
public interface PreParseAbandonOperation
       extends PreParseOperation
{
  /**
   * Retrieves the message ID of the operation that should be
   * abandoned.
   *
   * @return  The message ID of the operation that should be
   *          abandoned.
   */
  public int getIDToAbandon();
}

