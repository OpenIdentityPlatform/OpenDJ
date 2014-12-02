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

import java.io.Closeable;

import org.opends.server.types.DirectoryException;

/**
 * TODO JNR.
 * @param <K> TODO JNR
 * @param <V> TODO JNR
 * @param <T> TODO JNR
 * @param <M> TODO JNR
 */
public interface KeyValueStore<K, V, T, M> extends Closeable
{

  /**
   * TODO JNR.
   *
   * @throws DirectoryException
   *           TODO JNR
   */
  void open() throws DirectoryException;

  /**
   * TODO JNR.
   *
   * @param txn
   *          TODO JNR
   * @param key
   *          TODO JNR
   * @param value
   *          TODO JNR
   * @return TODO JNR
   * @throws DirectoryException
   *           TODO JNR
   */
  boolean insert(T txn, K key, V value) throws DirectoryException;

  /**
   * TODO JNR.
   *
   * @param txn
   *          TODO JNR
   * @param key
   *          TODO JNR
   * @param value
   *          TODO JNR
   * @return TODO JNR
   * @throws DirectoryException
   *           TODO JNR
   */
  boolean put(T txn, K key, V value) throws DirectoryException;

  /**
   * TODO JNR.
   *
   * @param txn
   *          TODO JNR
   * @param key
   *          TODO JNR
   * @param mode
   *          TODO JNR
   * @return TODO JNR
   * @throws DirectoryException
   *           TODO JNR
   */
  V get(T txn, K key, M mode) throws DirectoryException;

  /**
   * TODO JNR.
   *
   * @param txn
   *          TODO JNR
   * @param key
   *          TODO JNR
   * @return TODO JNR
   * @throws DirectoryException
   *           TODO JNR
   */
  boolean remove(T txn, K key) throws DirectoryException;
}
