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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.util.cli;



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;



/**
 * The result of running a {@link Menu}. The result indicates to the
 * application how it should proceed:
 * <ul>
 * <li>{@link #again()} - the menu should be displayed again. A good
 * example of this is when a user chooses to view some help. Normally,
 * after the help is displayed, the user is allowed to select another
 * option
 * <li>{@link #cancel()} - the user chose to cancel any task
 * currently in progress and go back to the previous main menu if
 * applicable
 * <li>{@link #success()} - the user chose to apply any task
 * currently in progress and go back to the previous menu if
 * applicable. Any result values applicable to the chosen option can
 * be retrieved using {@link #getValue()} or {@link #getValues()}
 * <li>{@link #quit()} - the user chose to quit the application and
 * cancel all outstanding tasks.
 * </ul>
 *
 * @param <T>
 *          The type of result value(s) contained in success results.
 *          Use <code>Void</code> if success results should not
 *          contain values.
 */
public final class MenuResult<T> {

  /**
   * The type of result returned from the menu.
   */
  private static enum Type {
    /**
     * The user selected an option which did not return a result, so
     * the menu should be displayed again.
     */
    AGAIN,

    /**
     * The user did not select an option and instead chose to cancel
     * the current task.
     */
    CANCEL,

    /**
     * The user did not select an option and instead chose to quit the
     * entire application.
     */
    QUIT,

    /**
     * The user selected an option which succeeded and returned one or
     * more result values.
     */
    SUCCESS
  }



  /**
   * Creates a new menu result indicating that the menu should be
   * displayed again. A good example of this is when a user chooses to
   * view some help. Normally, after the help is displayed, the user
   * is allowed to select another option.
   *
   * @param <T>
   *          The type of result value(s) contained in success
   *          results. Use <code>Void</code> if success results
   *          should not contain values.
   * @return Returns a new menu result indicating that the menu should
   *         be displayed again.
   */
  public static <T> MenuResult<T> again() {
    return new MenuResult<T>(Type.AGAIN, Collections.<T> emptyList());
  }



  /**
   * Creates a new menu result indicating that the user chose to
   * cancel any task currently in progress and go back to the previous
   * main menu if applicable.
   *
   * @param <T>
   *          The type of result value(s) contained in success
   *          results. Use <code>Void</code> if success results
   *          should not contain values.
   * @return Returns a new menu result indicating that the user chose
   *         to cancel any task currently in progress and go back to
   *         the previous main menu if applicable.
   */
  public static <T> MenuResult<T> cancel() {
    return new MenuResult<T>(Type.CANCEL, Collections.<T> emptyList());
  }



  /**
   * Creates a new menu result indicating that the user chose to quit
   * the application and cancel all outstanding tasks.
   *
   * @param <T>
   *          The type of result value(s) contained in success
   *          results. Use <code>Void</code> if success results
   *          should not contain values.
   * @return Returns a new menu result indicating that the user chose
   *         to quit the application and cancel all outstanding tasks.
   */
  public static <T> MenuResult<T> quit() {
    return new MenuResult<T>(Type.QUIT, Collections.<T> emptyList());
  }



  /**
   * Creates a new menu result indicating that the user chose to apply
   * any task currently in progress and go back to the previous menu
   * if applicable. The menu result will not contain any result
   * values.
   *
   * @param <T>
   *          The type of result value(s) contained in success
   *          results. Use <code>Void</code> if success results
   *          should not contain values.
   * @return Returns a new menu result indicating that the user chose
   *         to apply any task currently in progress and go back to
   *         the previous menu if applicable.The menu result will not
   *         contain any result values.
   */
  public static <T> MenuResult<T> success() {
    return success(Collections.<T> emptySet());
  }



  /**
   * Creates a new menu result indicating that the user chose to apply
   * any task currently in progress and go back to the previous menu
   * if applicable. The menu result will contain the provided values,
   * which can be retrieved using {@link #getValue()} or
   * {@link #getValues()}.
   *
   * @param <T>
   *          The type of the result values.
   * @param values
   *          The result values.
   * @return Returns a new menu result indicating that the user chose
   *         to apply any task currently in progress and go back to
   *         the previous menu if applicable. The menu result will
   *         contain the provided values, which can be retrieved using
   *         {@link #getValue()} or {@link #getValues()}.
   */
  public static <T> MenuResult<T> success(Collection<T> values) {
    return new MenuResult<T>(Type.SUCCESS, new ArrayList<T>(values));
  }



  /**
   * Creates a new menu result indicating that the user chose to apply
   * any task currently in progress and go back to the previous menu
   * if applicable. The menu result will contain the provided value,
   * which can be retrieved using {@link #getValue()} or
   * {@link #getValues()}.
   *
   * @param <T>
   *          The type of the result value.
   * @param value
   *          The result value.
   * @return Returns a new menu result indicating that the user chose
   *         to apply any task currently in progress and go back to
   *         the previous menu if applicable. The menu result will
   *         contain the provided value, which can be retrieved using
   *         {@link #getValue()} or {@link #getValues()}.
   */
  public static <T> MenuResult<T> success(T value) {
    return success(Collections.singleton(value));
  }

  // The type of result returned from the menu.
  private final Type type;

  // The menu result value(s).
  private final Collection<T> values;



  // Private constructor.
  private MenuResult(Type type, Collection<T> values) {
    this.type = type;
    this.values = values;
  }



  /**
   * Gets the menu result value if this is a menu result indicating
   * success.
   *
   * @return Returns the menu result value, or <code>null</code> if
   *         there was no result value or if this is not a success
   *         menu result.
   * @see #isSuccess()
   */
  public T getValue() {
    if (values.isEmpty()) {
      return null;
    } else {
      return values.iterator().next();
    }
  }



  /**
   * Gets the menu result values if this is a menu result indicating
   * success.
   *
   * @return Returns the menu result values, which may be empty if
   *         there were no result values or if this is not a success
   *         menu result.
   * @see #isSuccess()
   */
  public Collection<T> getValues() {
    return new ArrayList<T>(values);
  }



  /**
   * Determines if this menu result indicates that the menu should be
   * displayed again. A good example of this is when a user chooses to
   * view some help. Normally, after the help is displayed, the user
   * is allowed to select another option.
   *
   * @return Returns <code>true</code> if this menu result indicates
   *         that the menu should be displayed again.
   */
  public boolean isAgain() {
    return type == Type.AGAIN;
  }



  /**
   * Determines if this menu result indicates that the user chose to
   * cancel any task currently in progress and go back to the previous
   * main menu if applicable.
   *
   * @return Returns <code>true</code> if this menu result indicates
   *         that the user chose to cancel any task currently in
   *         progress and go back to the previous main menu if
   *         applicable.
   */
  public boolean isCancel() {
    return type == Type.CANCEL;
  }



  /**
   * Determines if this menu result indicates that the user chose to
   * quit the application and cancel all outstanding tasks.
   *
   * @return Returns <code>true</code> if this menu result indicates
   *         that the user chose to quit the application and cancel
   *         all outstanding tasks.
   */
  public boolean isQuit() {
    return type == Type.QUIT;
  }



  /**
   * Determines if this menu result indicates that the user chose to
   * apply any task currently in progress and go back to the previous
   * menu if applicable. Any result values can be retrieved using the
   * {@link #getValue()} or {@link #getValues()} methods.
   *
   * @return Returns <code>true</code> if this menu result indicates
   *         that the user chose to apply any task currently in
   *         progress and go back to the previous menu if applicable.
   * @see #getValue()
   * @see #getValues()
   */
  public boolean isSuccess() {
    return type == Type.SUCCESS;
  }
}
