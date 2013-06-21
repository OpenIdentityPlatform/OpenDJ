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
 *      Copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.sync.filters;



import org.forgerock.i18n.LocalizableException;
import org.forgerock.i18n.LocalizableMessage;



/**
 * The result of processing a change record in a {@code Filter}.
 */
public final class FilterResult
{

  /**
   * The set of possible actions that a {@code Filter} may return in order to
   * indicate how change record processing should continue.
   */
  public enum Action
  {

    /**
     * Indicates that the change record was processed successfully by the
     * filter, and it should now be processed by any remaining filters.
     */
    NEXT,

    /**
     * Indicates that the change record was processed successfully by the
     * filter, but it should not be processed by any remaining filters.
     */
    STOP,

    /**
     * Indicates that the change record could not be processed by the filter due
     * to a non-fatal error, and it should not be processed by any remaining
     * filters. However, subsequent change records can still be processed.
     */
    FAIL,

    /**
     * Indicates that a fatal error occurred and it will not be possible to
     * process any more change records.
     */
    FATAL;
  }



  /**
   * Returns a filter result whose action is {@link Action#FAIL} and which has
   * the specified cause.
   *
   * @param cause
   *          The exception which caused processing of the change record to
   *          stop.
   * @return A filter result indicating that processing of the change record
   *         should stop due to an error.
   */
  public static FilterResult fail(final Throwable cause)
  {
    return new FilterResult(Action.FAIL, null, cause);
  }



  /**
   * Returns a filter result whose action is {@link Action#FATAL} and which has
   * the specified cause.
   *
   * @param cause
   *          The exception which caused the filter chain to fail.
   * @return A filter result indicating that the filter chain has failed and no
   *         more change records can be processed.
   */
  public static FilterResult fatal(final Throwable cause)
  {
    return new FilterResult(Action.FATAL, null, cause);
  }



  /**
   * Returns a filter result whose action is {@link Action#STOP} and which has
   * no cause or message.
   *
   * @return A filter result indicating that processing of the change record
   *         should stop.
   */
  public static FilterResult stop()
  {
    return stop(null);
  }



  /**
   * Returns a filter result whose action is {@link Action#STOP} and which has
   * the specified message, but no cause.
   *
   * @param message
   *          A message explaining why processing of the change record should
   *          stop.
   * @return A filter result indicating that processing of the change record
   *         should stop.
   */
  public static FilterResult stop(final LocalizableMessage message)
  {
    if (message == null)
    {
      return STOP;
    }
    else
    {
      return new FilterResult(Action.STOP, message, null);
    }
  }



  private final Action action;

  private final LocalizableMessage message;

  private final Throwable cause;

  private static final FilterResult NEXT = new FilterResult(Action.NEXT, null,
      null);

  private static final FilterResult STOP = new FilterResult(Action.STOP, null,
      null);



  /**
   * Returns a filter result whose action is {@link Action#NEXT} and which has
   * no cause or message.
   *
   * @return A filter result indicating that processing of the change record
   *         should continue.
   */
  public static FilterResult next()
  {
    return NEXT;
  }



  private FilterResult(final Action action, final LocalizableMessage message,
      final Throwable cause)
  {
    this.action = action;
    this.cause = cause;

    if (message != null)
    {
      this.message = message;
    }
    else if (cause != null)
    {
      if (cause instanceof LocalizableException)
      {
        this.message = ((LocalizableException) cause).getMessageObject();
      }
      else
      {
        this.message = LocalizableMessage.raw("%s", cause.getMessage());
      }
    }
    else
    {
      this.message = null;
    }
  }



  /**
   * Returns the action associated with this filter result.
   *
   * @return The action associated with this filter result.
   */
  public Action getAction()
  {
    return action;
  }



  /**
   * Returns the exception which caused processing to be stopped.
   *
   * @return The exception which caused processing to be stopped, may be
   *         {@code null}.
   */
  public Throwable getCause()
  {
    return cause;
  }



  /**
   * Returns the optional localizable message describing why processing has
   * stopped.
   *
   * @return The localizable message describing why processing has stopped, may
   *         be {@code null}.
   */
  public LocalizableMessage getMessage()
  {
    return message;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    builder.append("FilterResult(");
    builder.append(action);
    if (message != null)
    {
      builder.append(',');
      builder.append(message);
    }
    if (cause != null)
    {
      builder.append(',');
      builder.append(cause);
    }
    builder.append(')');
    return builder.toString();
  }

}
