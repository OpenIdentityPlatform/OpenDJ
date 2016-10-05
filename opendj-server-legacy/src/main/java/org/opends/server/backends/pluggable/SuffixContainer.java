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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import java.io.Closeable;

import org.forgerock.opendj.ldap.DN;

/**
 * Container for a whole suffix environment which stores all entries from the
 * subtree of the suffix' baseDN. A suffix container has a set of key-value
 * stores a.k.a indexes. It stores entries in these key-values stores and
 * maintain the indexes all in sync on updates.
 */
public interface SuffixContainer extends Closeable
{
  /** The name of the index associating normalized DNs to ids. LDAP DNs uniquely identify entries. */
  String DN2ID_INDEX_NAME = "dn2id";
  /**
   * The name of the index associating entry ids to entries. Entry ids are
   * monotonically increasing unique longs and entries are serialized versions
   * of LDAP entries.
   */
  String ID2ENTRY_INDEX_NAME = "id2entry";
  /**
   * The name of the index associating an entry id to the entry id set of all
   * its children, i.e. its immediate children.
   */
  String ID2CHILDREN_INDEX_NAME = "id2children";
  /** The name of the index associating an entry id to the number of immediate children below it. */
  String ID2CHILDREN_COUNT_NAME = "id2childrencount";
  /**
   * The name of the index associating an entry id to the entry id set of all
   * its subordinates, i.e. the children, grand-children, grand-grand-children,
   * ....
   */
  String ID2SUBTREE_INDEX_NAME = "id2subtree";
  /** The name of the index associating normalized DNs to normalized URIs. */
  String REFERRAL_INDEX_NAME = "referral";
  /**
   * The name of the index which associates indexes with their trust state, i.e.
   * does the index needs to be rebuilt ?
   */
  String STATE_INDEX_NAME = "state";
  /** The attribute used to return a search index debug string to the client. */
  String ATTR_DEBUG_SEARCH_INDEX = "debugsearchindex";

  /**
   * Returns the baseDN that this suffix container is responsible for.
   *
   * @return the baseDN that this suffix container is responsible for
   */
  DN getBaseDN();
}
