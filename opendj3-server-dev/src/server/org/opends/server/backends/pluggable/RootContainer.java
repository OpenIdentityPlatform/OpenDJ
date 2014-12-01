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
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.Map;

import org.opends.server.types.DN;

/**
 * Container for a whole database environment. A single database environment
 * contains data for one or several suffixes. Each suffix has a set of key-value
 * stores a.k.a indexes.
 * <p>
 * A root container is in a 1-1 relationship with a backend. This design allows
 * a single backend to support several baseDNs inside the same database
 * environment.
 * <p>
 * A root container is composed of suffix containers, one for each base DN and
 * manages their lifecycle.
 *
 * @param <T>
 *          the type of the suffix containers
 */
public interface RootContainer<T extends SuffixContainer>
{

  /**
   * Returns a Map of baseDN to suffix containers.
   *
   * @return a Map of baseDN to suffix containers
   */
  Map<DN, T> getSuffixContainers();

  /**
   * Returns the number of entries managed by all the suffix containers under
   * this root container.
   *
   * @return the number of entries managed by this root container, or -1 if the
   *         count not not be determined
   */
  long getEntryCount();
}
