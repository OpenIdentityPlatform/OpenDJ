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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.util;



import java.util.Locale;
import java.util.ResourceBundle;

import org.opends.sdk.LocalizableMessage;



/**
 * An opaque handle to a localizable message.
 */
public abstract class LocalizableMessageDescriptor
{
  /**
   * Subclass for creating messages with no arguments.
   */
  public static final class Arg0 extends LocalizableMessageDescriptor
  {

    /**
     * Cached copy of the message created by this descriptor. We can get
     * away with this for the zero argument message because it is
     * immutable.
     */
    private final LocalizableMessage message;

    private final boolean requiresFormat;



    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg0(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
      message = newMessage(this);
      requiresFormat = containsArgumentLiterals(getFormatString());
    }



    /**
     * Creates a message.
     *
     * @return LocalizableMessage object
     */
    public LocalizableMessage get()
    {
      return message;
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return requiresFormat;
    }
  }



  /**
   * Subclass for creating messages with one argument.
   *
   * @param <T1>
   *          The type of the first message argument.
   */
  public static final class Arg1<T1> extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg1(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     */
    public LocalizableMessage get(T1 a1)
    {
      return newMessage(this, a1);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with two arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   */
  public static final class Arg2<T1, T2> extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg2(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2)
    {
      return newMessage(this, a1, a2);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with three arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   */
  public static final class Arg3<T1, T2, T3> extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg3(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3)
    {
      return newMessage(this, a1, a2, a3);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with four arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   */
  public static final class Arg4<T1, T2, T3, T4> extends
      LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg4(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4)
    {
      return newMessage(this, a1, a2, a3, a4);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with five arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   * @param <T5>
   *          The type of the fifth message argument.
   */
  public static final class Arg5<T1, T2, T3, T4, T5> extends
      LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg5(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     * @param a5
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5)
    {
      return newMessage(this, a1, a2, a3, a4, a5);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with six arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   * @param <T5>
   *          The type of the fifth message argument.
   * @param <T6>
   *          The type of the sixth message argument.
   */
  public static final class Arg6<T1, T2, T3, T4, T5, T6> extends
      LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg6(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     * @param a5
     *          message argument
     * @param a6
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6)
    {
      return newMessage(this, a1, a2, a3, a4, a5, a6);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with seven arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   * @param <T5>
   *          The type of the fifth message argument.
   * @param <T6>
   *          The type of the sixth message argument.
   * @param <T7>
   *          The type of the seventh message argument.
   */
  public static final class Arg7<T1, T2, T3, T4, T5, T6, T7> extends
      LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg7(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     * @param a5
     *          message argument
     * @param a6
     *          message argument
     * @param a7
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6, T7 a7)
    {
      return newMessage(this, a1, a2, a3, a4, a5, a6, a7);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with eight arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   * @param <T5>
   *          The type of the fifth message argument.
   * @param <T6>
   *          The type of the sixth message argument.
   * @param <T7>
   *          The type of the seventh message argument.
   * @param <T8>
   *          The type of the eighth message argument.
   */
  public static final class Arg8<T1, T2, T3, T4, T5, T6, T7, T8>
      extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg8(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     * @param a5
     *          message argument
     * @param a6
     *          message argument
     * @param a7
     *          message argument
     * @param a8
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6, T7 a7,
        T8 a8)
    {
      return newMessage(this, a1, a2, a3, a4, a5, a6, a7, a8);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with nine arguments.
   *
   * @param <T1>
   *          The type of the first message argument.
   * @param <T2>
   *          The type of the second message argument.
   * @param <T3>
   *          The type of the third message argument.
   * @param <T4>
   *          The type of the fourth message argument.
   * @param <T5>
   *          The type of the fifth message argument.
   * @param <T6>
   *          The type of the sixth message argument.
   * @param <T7>
   *          The type of the seventh message argument.
   * @param <T8>
   *          The type of the eighth message argument.
   * @param <T9>
   *          The type of the ninth message argument.
   */
  public static final class Arg9<T1, T2, T3, T4, T5, T6, T7, T8, T9>
      extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public Arg9(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param a1
     *          message argument
     * @param a2
     *          message argument
     * @param a3
     *          message argument
     * @param a4
     *          message argument
     * @param a5
     *          message argument
     * @param a6
     *          message argument
     * @param a7
     *          message argument
     * @param a8
     *          message argument
     * @param a9
     *          message argument
     */
    public LocalizableMessage get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6, T7 a7,
        T8 a8, T9 a9)
    {
      return newMessage(this, a1, a2, a3, a4, a5, a6, a7, a8, a9);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * Subclass for creating messages with an any number of arguments. In
   * general this class should be used when a message needs to be
   * defined with more arguments that can be handled with the current
   * number of subclasses
   */
  public static final class ArgN extends LocalizableMessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     *
     * @param rbBase
     *          base of the backing resource bundle
     * @param key
     *          for accessing the format string from the resource bundle
     * @param classLoader
     *          the class loader to be used to get the ResourceBundle
     */
    public ArgN(String rbBase, String key, ClassLoader classLoader)
    {
      super(rbBase, key, classLoader);
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param args
     *          message arguments
     */
    public LocalizableMessage get(Object... args)
    {
      return newMessage(this, args);
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return true;
    }

  }



  /**
   * A descriptor for creating a raw message from a <code>String</code>.
   * In general this descriptor should NOT be used internally. OpenDS
   * plugins may want to use the mechanism to create messages without
   * storing their strings in resource bundles.
   */
  public static final class Raw extends LocalizableMessageDescriptor
  {

    private final String formatString;

    private final boolean requiresFormatter;



    /**
     * Creates a parameterized instance.
     *
     * @param formatString
     *          for created messages
     */
    public Raw(CharSequence formatString)
    {
      super(null, null, null);
      this.formatString = formatString != null ? formatString
          .toString() : "";
      this.requiresFormatter = this.formatString.matches(".*%.*");
    }



    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message is
     * rendered to string representation.
     *
     * @return LocalizableMessage object
     * @param args
     *          message arguments
     */
    public LocalizableMessage get(Object... args)
    {
      return newMessage(this, args);
    }



    /**
     * Overridden in order to bypass the resource bundle plumbing and
     * return the format string directly.
     *
     * @param locale
     *          ignored
     * @return format string
     */
    public String getFormatString(Locale locale)
    {
      return this.formatString;
    }



    /**
     * {@inheritDoc}
     */
    public boolean requiresFormatter()
    {
      return this.requiresFormatter;
    }

  }



  // Container for caching the last locale specific format string.
  private static final class CachedFormatString
  {
    private final Locale locale;

    private final String formatString;



    private CachedFormatString(Locale locale, String formatString)
    {
      this.locale = locale;
      this.formatString = formatString;
    }
  }



  /**
   * Factory interface for creating messages. Only LocalizableMessage should
   * implement this.
   */
  public static interface MessageFactory
  {
    /**
     * Creates a new parameterized message instance.
     *
     * @param descriptor
     *          The message descriptor.
     * @param args
     *          The message parameters.
     * @return The new message.
     */
    LocalizableMessage newMessage(LocalizableMessageDescriptor descriptor, Object... args);
  }



  /**
   * We use a factory for creating LocalizableMessage objects in order to avoid
   * exposing this class in the public API.
   */
  public static MessageFactory MESSAGE_FACTORY;

  // Force MESSAGE_FACTORY to be set.
  static
  {
    try
    {
      Class.forName("org.opends.sdk.LocalizableMessage");
    }
    catch (ClassNotFoundException e)
    {
      throw new RuntimeException(e);
    }
  }



  /**
   * Indicates whether or not formatting should be applied to the given
   * format string. Note that a format string might have literal
   * specifiers (%% or %n for example) that require formatting but are
   * not replaced by arguments.
   *
   * @param s
   *          candidate for formatting
   * @return boolean where true indicates that the format string
   *         requires formatting
   */
  private static final boolean containsArgumentLiterals(String s)
  {
    return s.matches(".*%[n|%].*"); // match Formatter literals
  }



  private static LocalizableMessage newMessage(LocalizableMessageDescriptor descriptor,
      Object... args)
  {
    return MESSAGE_FACTORY.newMessage(descriptor, args);
  }



  // String for accessing backing resource bundle.
  private final String rbBase;

  // Used for accessing format string from the resource bundle.
  private final String key;

  /*
   * The class loader to be used to retrieve the ResourceBundle. If null
   * the default class loader will be used.
   */
  private final ClassLoader classLoader;

  // It's ok if there are race conditions.
  private CachedFormatString cachedFormatString = null;



  /**
   * Creates a parameterized message descriptor.
   *
   * @param rbBase
   *          string for accessing the underlying message bundle
   * @param key
   *          for accessing the format string from the message bundle
   * @param classLoader
   *          the class loader to be used to get the ResourceBundle
   */
  private LocalizableMessageDescriptor(String rbBase, String key,
      ClassLoader classLoader)
  {
    this.rbBase = rbBase;
    this.key = key;
    this.classLoader = classLoader;
  }



  /**
   * Returns the format string which should be used when creating the
   * string representation of this message using the specified locale.
   *
   * @param locale
   *          The locale.
   * @return The format string.
   * @throws NullPointerException
   *           If {@code locale} was {@code null}.
   */
  public String getFormatString(Locale locale)
      throws NullPointerException
  {
    Validator.ensureNotNull(locale);

    // Fast path.
    final CachedFormatString cfs = cachedFormatString;
    if (cfs != null && cfs.locale == locale)
    {
      return cfs.formatString;
    }

    // There's a potential race condition here but it's benign - we'll
    // just do a bit more work than needed.
    final ResourceBundle bundle = getBundle(locale);
    final String formatString = bundle.getString(key);
    cachedFormatString = new CachedFormatString(locale, formatString);

    return formatString;
  }



  /**
   * Indicates whether or not this descriptor format string should be
   * processed by {@code Formatter} during string rendering.
   *
   * @return {@code true} if a {@code Formatter} should be used,
   *         otherwise {@code false}.
   */
  public abstract boolean requiresFormatter();



  private ResourceBundle getBundle(Locale locale)
  {
    if (locale == null)
    {
      locale = Locale.getDefault();
    }
    if (classLoader == null)
    {
      return ResourceBundle.getBundle(this.rbBase, locale);
    }
    else
    {
      return ResourceBundle.getBundle(this.rbBase, locale, classLoader);
    }
  }



  /**
   * Returns the format string which should be used when creating the
   * string representation of this message using the default locale.
   *
   * @return The format string.
   */
  final String getFormatString()
  {
    return getFormatString(Locale.getDefault());
  }

}
