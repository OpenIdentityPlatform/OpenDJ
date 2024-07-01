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
 * Copyright 2016 ForgeRock AS.
 */

package org.opends.server.backends.pluggable.spi;

import org.forgerock.opendj.ldap.ByteSequence;

import java.util.NoSuchElementException;

/**
 * Implementation of an empty {@link Cursor}, for simulating no records to cursor on.
 * <p>
 * Cursor behaves as follows:
 * <ul>
 * <li>Positioning to a key or index will fail, returning {@code false}.</li>
 * <li>Reading the key or value will return {@code null}.</li>
 * <li>Deleting the current element is not supported, {@link UnsupportedOperationException} will be thrown.</li>
 * </ul>
 * </p>
 *
 * @param <K> Type of the simulated record's key
 * @param <V> Type of the simulated record's value
 */
public final class EmptyCursor<K, V> implements Cursor<K, V>
{
  @Override
  public boolean positionToKey(ByteSequence key)
  {
    return false;
  }

  @Override
  public boolean positionToKeyOrNext(ByteSequence key)
  {
    return false;
  }

  @Override
  public boolean positionToLastKey()
  {
    return false;
  }

  @Override
  public boolean positionToIndex(int index)
  {
    return false;
  }

  @Override
  public boolean next()
  {
    return false;
  }

  @Override
  public boolean isDefined()
  {
    return false;
  }

  @Override
  public K getKey() throws NoSuchElementException
  {
    return null;
  }

  @Override
  public V getValue() throws NoSuchElementException
  {
    return null;
  }

  @Override
  public void delete() throws NoSuchElementException, UnsupportedOperationException
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close()
  {
    // Nothing to do
  }
}
