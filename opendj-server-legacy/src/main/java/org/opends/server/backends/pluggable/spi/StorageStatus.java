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
 *      Copyright 2015 ForgeRock AS
 */

package org.opends.server.backends.pluggable.spi;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Represents the current status of a storage with respect to its resources.
 */
public final class StorageStatus
{
  /** Internal States. */
  private enum Code
  {
    /** Storage is working normally. */
    WORKING,
    /** Storage resources start getting scarce. */
    LOCKED_DOWN,
    /** Storage has no resources to execute operations. */
    UNUSABLE
  }

  /** Hopefully resources are always in this state. */
  private static final StorageStatus WORKING = new StorageStatus(Code.WORKING, null);

  /** Current status. */
  private final Code code;
  /** Current warning/error message. */
  private final LocalizableMessage reason;

  /**
   * Returns normal state.
   *
   * @return normal state
   */
  public static StorageStatus working()
  {
    return WORKING;
  }

  /**
   * Returns state for resources getting scarce.
   *
   * @param reason the message to forward
   * @return state for resources getting scarce
   */
  public static StorageStatus lockedDown(LocalizableMessage reason)
  {
    return new StorageStatus(Code.LOCKED_DOWN, reason);
  }

  /**
   * Returns state for no more resources.
   *
   * @param reason the message to forward
   * @return state for no more resources
   */
  public static StorageStatus unusable(LocalizableMessage reason)
  {
    return new StorageStatus(Code.UNUSABLE, reason);
  }

  private StorageStatus(Code code, LocalizableMessage reason)
  {
    this.code = code;
    this.reason = reason;
  }

  /**
   * Returns true if resources are getting scarce.
   *
   * @return true if resources are getting scarce
   */
  public boolean isLockedDown()
  {
    return code == Code.LOCKED_DOWN;
  }

  /**
   * Returns true if state is normal.
   *
   * @return true if state is normal
   */
  public boolean isWorking()
  {
    return code == Code.WORKING;
  }

  /**
   * Returns true if no more resources are available.
   *
   * @return true if no more resources are available
   */
  public boolean isUnusable()
  {
    return code == Code.UNUSABLE;
  }

  /**
   * Returns the error message for non working states.
   *
   * @return the error message for non working states
   */
  public LocalizableMessage getReason()
  {
    return reason;
  }
}
