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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.messages;

import java.util.Locale;
import java.util.List;
import java.util.LinkedList;
import java.io.Serializable;

/**
 * A builder used specifically for messages.  As messages are
 * appended they are translated to their string representation
 * for storage using the locale specified in the constructor.
 *
 * Note that before you use this class you should consider whether
 * it is appropriate.  In general composing messages by appending
 * message to each other may not produce a message that is
 * formatted appropriately for all locales.  It is usually better
 * to create messages by composition.  In other words you should
 * create a base message that contains one or more string argument
 * specifiers (%s) and define other message objects to use as
 * replacement variables.  In this way language translators have
 * a change to reformat the message for a particular locale if
 * necessary.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=true,
    mayExtend=false,
    mayInvoke=true)
public final class MessageBuilder implements Appendable, CharSequence,
    Serializable
{

  private static final long serialVersionUID = -3292823563904285315L;

  /** Used internally to store appended messages. */
  private final StringBuilder sb = new StringBuilder();

  /** Used internally to store appended messages. */
  private final List<Message> messages = new LinkedList<Message>();

  /** Used to render the string representation of appended messages. */
  private final Locale locale;

  /**
   * Constructs an instance that will build messages
   * in the default locale.
   */
  public MessageBuilder() {
    this(Locale.getDefault());
  }

  /**
   * Constructs an instance that will build messages
   * in the default locale having an initial message.
   *
   * @param message initial message
   */
  public MessageBuilder(Message message) {
    this(Locale.getDefault());
    append(message);
  }

  /**
   * Constructs an instance that will build messages
   * in the default locale having an initial message.
   *
   * @param message initial message
   */
  public MessageBuilder(String message) {
    this(Locale.getDefault());
    append(message);
  }

  /**
   * Constructs an instance from another <code>MessageBuilder</code>.
   *
   * @param mb from which to construct a new message builder
   */
  public MessageBuilder(MessageBuilder mb) {
    for (Message msg : mb.messages) {
      this.messages.add(msg);
    }
    this.sb.append(sb);
    this.locale = mb.locale;
  }

  /**
   * Constructs an instance that will build messages
   * in a specified locale.
   *
   * @param locale used for translating appended messages
   */
  public MessageBuilder(Locale locale) {
    this.locale = locale;
  }

  /**
   * Append a message to this builder.  The string
   * representation of the locale specifed in the
   * constructor will be stored in this builder.
   *
   * @param message to be appended
   * @return reference to this builder
   */
  public MessageBuilder append(Message message) {
    if (message != null) {
      sb.append(message.toString(locale));
      messages.add(message);
    }
    return this;
  }

  /**
   * Append an integer to this builder.
   *
   * @param number to append
   * @return reference to this builder
   */
  public MessageBuilder append(int number) {
    append(String.valueOf(number));
    return this;
  }

  /**
   * Append an object to this builder.
   *
   * @param object to append
   * @return reference to this builder
   */
  public MessageBuilder append(Object object) {
    if (object != null) {
      append(String.valueOf(object));
    }
    return this;
  }


  /**
   * Append a string to this builder.
   *
   * @param cs to append
   * @return reference to this builder
   */
  public MessageBuilder append(CharSequence cs) {
    if (cs != null) {
      sb.append(cs);
      if (cs instanceof Message) {
        messages.add((Message)cs);
      } else {
        messages.add(Message.raw(cs));
      }
    }
    return this;
  }

  /**
   * Appends a subsequence of the specified character sequence to this
   * <tt>Appendable</tt>.
   *
   * <p> An invocation of this method of the form <tt>out.append(csq, start,
   * end)</tt> when <tt>csq</tt> is not <tt>null</tt>, behaves in
   * exactly the same way as the invocation
   *
   * <pre>
   *     out.append(csq.subSequence(start, end)) </pre>
   *
   * @param  csq
   *         The character sequence from which a subsequence will be
   *         appended.  If <tt>csq</tt> is <tt>null</tt>, then characters
   *         will be appended as if <tt>csq</tt> contained the four
   *         characters <tt>"null"</tt>.
   *
   * @param  start
   *         The index of the first character in the subsequence
   *
   * @param  end
   *         The index of the character following the last character in the
   *         subsequence
   *
   * @return  A reference to this <tt>Appendable</tt>
   *
   * @throws  IndexOutOfBoundsException
   *          If <tt>start</tt> or <tt>end</tt> are negative, <tt>start</tt>
   *          is greater than <tt>end</tt>, or <tt>end</tt> is greater than
   *          <tt>csq.length()</tt>
   */
  public MessageBuilder append(CharSequence csq, int start, int end)
    throws IndexOutOfBoundsException
  {
    return append(csq.subSequence(start, end));
  }

  /**
   * Appends the specified character to this <tt>Appendable</tt>.
   *
   * @param  c
   *         The character to append
   *
   * @return  A reference to this <tt>Appendable</tt>
   */
  public MessageBuilder append(char c) {
    return append(String.valueOf(c));
  }

  /**
   * Returns a string containing the characters in this sequence in the same
   * order as this sequence.  The length of the string will be the length of
   * this sequence.
   *
   * @return  a string consisting of exactly this sequence of characters
   */
  public String toString() {
    return sb.toString();
  }

  /**
   * Returns a string representation of the appended content
   * in the specific locale.  Only <code>Message</code>s
   * appended to this builder are rendered in the requested
   * locale.  Raw strings appended to this buffer are not
   * translated to different locale.
   *
   * @param locale requested
   * @return String representation
   */
  public String toString(Locale locale) {
    StringBuilder sb = new StringBuilder();
    for (Message m : messages) {
      sb.append(m.toString(locale));
    }
    return sb.toString();
  }

  /**
   * Returns a raw message representation of the appended content.
   * <p>
   * If the first object appended to this <code>MessageBuilder</code>
   * was a <code>Message</code> then the returned message will
   * inherit its category and severity. Otherwise the returned message
   * will have category {@link org.opends.messages.Category#USER_DEFINED}
   *  and severity {@link org.opends.messages.Severity#INFORMATION}.
   *
   * @return Message raw message representing builder content
   */
  public Message toMessage() {
    StringBuffer fmtString = new StringBuffer();
    for (int i = 0; i < messages.size(); i++) {
      fmtString.append("%s");
    }

    if (messages.isEmpty()) {
      return Message.raw(fmtString, messages.toArray());
    } else {
      // Inherit the category and severity of the first message.
      MessageDescriptor md = messages.get(0).getDescriptor();
      return Message.raw(md.getCategory(), md.getSeverity(), fmtString,
          messages.toArray());
    }
  }

  /**
   * Returns the length of the string representation of this builder
   * using the default locale.
   *
   * @return  the number of <code>char</code>s in this message
   */
  public int length() {
    return length(Locale.getDefault());
  }

  /**
   * Returns the <code>char</code> value at the specified index of
   * the string representation of this builder using the default locale.
   *
   * @param   index   the index of the <code>char</code> value to be returned
   *
   * @return  the specified <code>char</code> value
   *
   * @throws  IndexOutOfBoundsException
   *          if the <tt>index</tt> argument is negative or not less than
   *          <tt>length()</tt>
   */
  public char charAt(int index) throws IndexOutOfBoundsException {
    return charAt(Locale.getDefault(), index);
  }

  /**
   * Returns a new <code>CharSequence</code> that is a subsequence of
   * the string representation of this builder using the default locale.
   * The subsequence starts with the <code>char</code>
   * value at the specified index and ends with the <code>char</code>
   * value at index <tt>end - 1</tt>.  The length (in <code>char</code>s)
   * of the returned sequence is <tt>end - start</tt>, so if
   * <tt>start == end</tt> then an empty sequence is returned.
   *
   * @param   start   the start index, inclusive
   * @param   end     the end index, exclusive
   *
   * @return  the specified subsequence
   *
   * @throws  IndexOutOfBoundsException
   *          if <tt>start</tt> or <tt>end</tt> are negative,
   *          if <tt>end</tt> is greater than <tt>length()</tt>,
   *          or if <tt>start</tt> is greater than <tt>end</tt>
   */
  public CharSequence subSequence(int start, int end)
          throws IndexOutOfBoundsException
  {
    return subSequence(Locale.getDefault(), start, end);
  }

  /**
   * Returns the length of the string representation of this builder
   * using a specific locale.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the length
   * @return  the number of <code>char</code>s in this message
   */
  public int length(Locale locale) {
    return toString(locale).length();
  }

  /**
   * Returns the <code>char</code> value at the specified index of
   * the string representation of this builder using a specific locale.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the character
   * @param   index   the index of the <code>char</code> value to be returned
   *
   * @return  the specified <code>char</code> value
   *
   * @throws  IndexOutOfBoundsException
   *          if the <tt>index</tt> argument is negative or not less than
   *          <tt>length()</tt>
   */
  public char charAt(Locale locale, int index)
          throws IndexOutOfBoundsException
  {
    return toString(locale).charAt(index);
  }

  /**
   * Returns a new <code>CharSequence</code> that is a subsequence of
   * the string representation of this builder using a specific locale.
   * The subsequence starts with the <code>char</code>
   * value at the specified index and ends with the <code>char</code>
   * value at index <tt>end - 1</tt>.  The length (in <code>char</code>s)
   * of the returned sequence is <tt>end - start</tt>, so if
   * <tt>start == end</tt> then an empty sequence is returned.
   *
   * @param   locale for which the rendering of this message will be
   *          used in determining the character
   * @param   start   the start index, inclusive
   * @param   end     the end index, exclusive
   *
   * @return  the specified subsequence
   *
   * @throws  IndexOutOfBoundsException
   *          if <tt>start</tt> or <tt>end</tt> are negative,
   *          if <tt>end</tt> is greater than <tt>length()</tt>,
   *          or if <tt>start</tt> is greater than <tt>end</tt>
   */
  public CharSequence subSequence(Locale locale, int start, int end)
    throws IndexOutOfBoundsException
  {
    return toString(locale).subSequence(start, end);
  }


}
