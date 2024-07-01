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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import org.forgerock.i18n.LocalizableMessageBuilder;

/** This interface represents a Backend Query. */
// @FunctionalInterface
interface IndexQuery
{
  /**
   * Evaluates the index query and returns the EntryIDSet.
   *
   * @param debugMessage If not null, diagnostic message will be written
   *                      which will help to determine why the returned
   *                      EntryIDSet is not defined.
   * @param indexNameOut If not null, output parameter for the name of the index type actually used to return
   *                      index results.
   * @return The non null EntryIDSet as a result of evaluating this query
   */
  EntryIDSet evaluate(LocalizableMessageBuilder debugMessage, StringBuilder indexNameOut);
}
