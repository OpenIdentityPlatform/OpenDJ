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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;

/**
 * Makes every key store written by {@link Platform} fail to replace its file at once, as a file
 * mounted on its own into a container does, or fail to give the new file the group of the old
 * one, so that it is written in place.
 */
public final class KeyStoreReplaceFailure
{
  private KeyStoreReplaceFailure()
  {
  }

  /**
   * Makes the replace of a key store file fail until the result is closed.
   *
   * @return what puts the replace back
   */
  public static AutoCloseable failKeyStoreReplace()
  {
    final Platform.KeyStoreReplacer replacer = Platform.keyStoreReplacer;
    Platform.keyStoreReplacer = (temp, target) -> {
      throw new AtomicMoveNotSupportedException(temp.toString(), target.toString(), "a test");
    };
    return () -> Platform.keyStoreReplacer = replacer;
  }

  /**
   * Makes the new file of every key store written keep the group it is made with, as on a file
   * system which ignores a change of group, until the result is closed.
   *
   * @return what puts the group back
   */
  public static AutoCloseable ignoreKeyStoreGroup()
  {
    return setKeyStoreGroup((view, group) -> {
      // the group of the new file stays as it is
    });
  }

  /**
   * Makes the new file of every key store written refuse the group of the old one, as for a
   * group this process is not a member of, until the result is closed.
   *
   * @return what puts the group back
   */
  public static AutoCloseable refuseKeyStoreGroup()
  {
    return setKeyStoreGroup((view, group) -> {
      throw new IOException("a test: not a member of " + group.getName());
    });
  }

  private static AutoCloseable setKeyStoreGroup(Platform.KeyStoreGroupSetter setter)
  {
    final Platform.KeyStoreGroupSetter old = Platform.keyStoreGroupSetter;
    Platform.keyStoreGroupSetter = setter;
    return () -> Platform.keyStoreGroupSetter = old;
  }
}
