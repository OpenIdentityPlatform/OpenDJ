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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import com.sun.opends.sdk.util.Validator;



/**
 * A mutable sequence of localizable messages and their parameters. As
 * messages are appended they are translated to their string
 * representation for storage using the locale specified in the
 * constructor.
 * <p>
 * Note that before you use this class you should consider whether it is
 * appropriate. In general composing messages by appending message to
 * each other may not produce a message that is formatted appropriately
 * for all locales.
 * <p>
 * It is usually better to create messages by composition. In other
 * words you should create a base message that contains one or more
 * string argument specifiers (%s) and define other message objects to
 * use as replacement variables. In this way language translators have a
 * change to reformat the message for a particular locale if necessary.
 *
 * @see LocalizableMessage
 */
public final class LocalizableMessageBuilder implements Appendable, CharSequence,
    Serializable
{

  private static final long serialVersionUID = -3292823563904285315L;

  // Used internally to store appended messages.
  private final List<LocalizableMessage> messages = new LinkedList<LocalizableMessage>();



  /**
   * Creates a new message builder whose content is initially empty.
   */
  public LocalizableMessageBuilder()
  {
    // Nothing to do.
  }



  /**
   * Creates a new message builder whose content is initially equal to
   * the provided message.
   *
   * @param message
   *          The initial content of the message builder.
   * @throws NullPointerException
   *           If {@code message} was {@code null}.
   */
  public LocalizableMessageBuilder(LocalizableMessage message) throws NullPointerException
  {
    append(message);
  }



  /**
   * Creates a new message builder whose content is initially equal to
   * the provided message builder.
   *
   * @param builder
   *          The initial content of the message builder.
   * @throws NullPointerException
   *           If {@code builder} was {@code null}.
   */
  public LocalizableMessageBuilder(LocalizableMessageBuilder builder)
  {
    for (final LocalizableMessage message : builder.messages)
    {
      this.messages.add(message);
    }
  }



  /**
   * Creates a new message builder whose content is initially equal to
   * the {@code String} representation of the provided {@code Object}.
   *
   * @param object
   *          The initial content of the message builder, may be {@code
   *          null}.
   */
  public LocalizableMessageBuilder(Object object)
  {
    append(object);
  }



  /**
   * Appends the provided character to this message builder.
   *
   * @param c
   *          The character to be appended.
   * @return A reference to this message builder.
   */
  public LocalizableMessageBuilder append(char c)
  {
    return append(LocalizableMessage.valueOf(c));
  }



  /**
   * Appends the provided character sequence to this message builder.
   *
   * @param cs
   *          The character sequence to be appended.
   * @return A reference to this message builder.
   * @throws NullPointerException
   *           If {@code cs} was {@code null}.
   */
  public LocalizableMessageBuilder append(CharSequence cs)
      throws NullPointerException
  {
    Validator.ensureNotNull(cs);
    return append((Object) cs);
  }



  /**
   * Appends a subsequence of the provided character sequence to this
   * message builder.
   * <p>
   * An invocation of this method of the form {@code append(cs, start,
   * end)}, behaves in exactly the same way as the invocation
   *
   * <pre>
   * append(cs.subSequence(start, end))
   * </pre>
   *
   * @param cs
   *          The character sequence to be appended.
   * @param start
   *          The index of the first character in the subsequence.
   * @param end
   *          The index of the character following the last character in
   *          the subsequence.
   * @return A reference to this message builder.
   * @throws IndexOutOfBoundsException
   *           If {@code start} or {@code end} are negative, {@code
   *           start} is greater than {@code end}, or {@code end} is
   *           greater than {@code csq.length()}.
   * @throws NullPointerException
   *           If {@code cs} was {@code null}.
   */
  public LocalizableMessageBuilder append(CharSequence cs, int start, int end)
      throws IndexOutOfBoundsException, NullPointerException
  {
    return append(cs.subSequence(start, end));
  }



  /**
   * Appends the provided integer to this message builder.
   *
   * @param value
   *          The integer to be appended.
   * @return A reference to this message builder.
   */
  public LocalizableMessageBuilder append(int value)
  {
    return append(LocalizableMessage.valueOf(value));
  }



  /**
   * Appends the provided message to this message builder.
   *
   * @param message
   *          The message to be appended.
   * @return A reference to this message builder.
   * @throws NullPointerException
   *           If {@code message} was {@code null}.
   */
  public LocalizableMessageBuilder append(LocalizableMessage message)
      throws NullPointerException
  {
    Validator.ensureNotNull(message);
    messages.add(message);
    return this;
  }



  /**
   * Appends the {@code String} representation of the provided {@code
   * Object} to this message builder.
   *
   * @param object
   *          The object to be appended, may be {@code null}.
   * @return A reference to this message builder.
   */
  public LocalizableMessageBuilder append(Object object)
  {
    return append(LocalizableMessage.valueOf(object));
  }



  /**
   * Returns the {@code char} value at the specified index of the
   * {@code String} representation of this message builder in the
   * default locale.
   *
   * @param index
   *          The index of the {@code char} value to be returned.
   * @return The specified {@code char} value.
   * @throws IndexOutOfBoundsException
   *           If the {@code index} argument is negative or not less
   *           than {@code length()}.
   */
  public char charAt(int index) throws IndexOutOfBoundsException
  {
    return charAt(Locale.getDefault(), index);
  }



  /**
   * Returns the {@code char} value at the specified index of the
   * {@code String} representation of this message builder in the
   * specified locale.
   *
   * @param locale
   *          The locale.
   * @param index
   *          The index of the {@code char} value to be returned.
   * @return The specified {@code char} value.
   * @throws IndexOutOfBoundsException
   *           If the {@code index} argument is negative or not less
   *           than {@code length()}.
   * @throws NullPointerException
   *           If {@code locale} was {@code null}.
   */
  public char charAt(Locale locale, int index)
      throws IndexOutOfBoundsException, NullPointerException
  {
    return toString(locale).charAt(index);
  }



  /**
   * Returns the length of the {@code String} representation of this
   * message builder in the default locale.
   *
   * @return The length of the {@code String} representation of this
   *         message builder in the default locale.
   */
  public int length()
  {
    return length(Locale.getDefault());
  }



  /**
   * Returns the length of the {@code String} representation of this
   * message builder in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return The length of the {@code String} representation of this
   *         message builder in the specified locale.
   * @throws NullPointerException
   *           If {@code locale} was {@code null}.
   */
  public int length(Locale locale) throws NullPointerException
  {
    return toString(locale).length();
  }



  /**
   * Returns a new {@code CharSequence} which is a subsequence of the
   * {@code String} representation of this message builder in the
   * default locale. The subsequence starts with the {@code char} value
   * at the specified index and ends with the {@code char} value at
   * index {@code end - 1} . The length (in {@code char}s) of the
   * returned sequence is {@code end - start}, so if {@code start ==
   * end} then an empty sequence is returned.
   *
   * @param start
   *          The start index, inclusive.
   * @param end
   *          The end index, exclusive.
   * @return The specified subsequence.
   * @throws IndexOutOfBoundsException
   *           If {@code start} or {@code end} are negative, if {@code
   *           end} is greater than {@code length()}, or if {@code
   *           start} is greater than {@code end}.
   */
  public CharSequence subSequence(int start, int end)
      throws IndexOutOfBoundsException
  {
    return subSequence(Locale.getDefault(), start, end);
  }



  /**
   * Returns a new {@code CharSequence} which is a subsequence of the
   * {@code String} representation of this message builder in the
   * specified locale. The subsequence starts with the {@code char}
   * value at the specified index and ends with the {@code char} value
   * at index {@code end - 1} . The length (in {@code char}s) of the
   * returned sequence is {@code end - start}, so if {@code start ==
   * end} then an empty sequence is returned.
   *
   * @param locale
   *          The locale.
   * @param start
   *          The start index, inclusive.
   * @param end
   *          The end index, exclusive.
   * @return The specified subsequence.
   * @throws IndexOutOfBoundsException
   *           If {@code start} or {@code end} are negative, if {@code
   *           end} is greater than {@code length()}, or if {@code
   *           start} is greater than {@code end}.
   * @throws NullPointerException
   *           If {@code locale} was {@code null}.
   */
  public CharSequence subSequence(Locale locale, int start, int end)
      throws IndexOutOfBoundsException, NullPointerException
  {
    return toString(locale).subSequence(start, end);
  }



  /**
   * Returns the {@link LocalizableMessage} representation of this message builder.
   * Subsequent changes to this message builder will not modify the
   * returned {@code LocalizableMessage}.
   *
   * @return The {@code LocalizableMessage} representation of this message builder.
   */
  public LocalizableMessage toMessage()
  {
    if (messages.isEmpty())
    {
      return LocalizableMessage.EMPTY;
    }

    final int sz = messages.size();
    final StringBuffer fmtString = new StringBuffer(sz * 2);
    for (int i = 0; i < sz; i++)
    {
      fmtString.append("%s");
    }

    return LocalizableMessage.raw(fmtString, messages.toArray());
  }



  /**
   * Returns the {@code String} representation of this message builder
   * in the default locale.
   *
   * @return The {@code String} representation of this message builder.
   */
  public String toString()
  {
    return toString(Locale.getDefault());
  }



  /**
   * Returns the {@code String} representation of this message builder
   * in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return The {@code String} representation of this message builder.
   * @throws NullPointerException
   *           If {@code locale} was {@code null}.
   */
  public String toString(Locale locale)
  {
    final StringBuilder builder = new StringBuilder();
    for (final LocalizableMessage message : messages)
    {
      builder.append(message.toString(locale));
    }
    return builder.toString();
  }

}
