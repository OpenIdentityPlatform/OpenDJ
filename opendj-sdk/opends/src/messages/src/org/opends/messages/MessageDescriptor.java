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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.messages;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Base class for all Message descriptor classes.
 */
public abstract class MessageDescriptor {

  /**
   * ID for messages that don't have a real ID.
   */
  public static final int NULL_ID = -1;

  /**
   * The maximum number of arguments that can be handled by
   * a specific subclass.  If you define more subclasses be
   * sure to increment this number appropriately.
   */
  static public final int DESCRIPTOR_MAX_ARG_HANDLER = 11;

  /**
   * The base name of the specific argument handling subclasses
   * defined below.  The class names consist of the base name
   * followed by a number indicating the number of arguments
   * that they handle when creating messages or the letter "N"
   * meaning any number of arguments.
   */
  public static final String DESCRIPTOR_CLASS_BASE_NAME = "Arg";

  /**
   * Subclass for creating messages with no arguements.
   */
  static public class Arg0 extends MessageDescriptor {

    /**
     * Cached copy of the message created by this descriptor.  We can
     * get away with this for the zero argument message because it is
     * immutable.
     */
    private Message message;

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg0(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
      message = new Message(this);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg0(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
      message = new Message(this);
    }

    /**
     * Creates a message.
     * @return Message object
     */
    public Message get() {
      return message;
    }

  }

  /**
   * Subclass for creating messages with one arguement.
   */
  static public class Arg1<T1> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg1(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg1(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     */
    public Message get(T1 a1) {
      return new Message(this, a1);
    }

  }

  /**
   * Subclass for creating messages with two arguements.
   */
  static public class Arg2<T1, T2> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg2(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg2(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     */
    public Message get(T1 a1, T2 a2) {
      return new Message(this, a1, a2);
    }

  }

  /**
   * Subclass for creating messages with three arguements.
   */
  static public class Arg3<T1, T2, T3> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg3(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg3(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3) {
      return new Message(this, a1, a2, a3);
    }

  }

  /**
   * Subclass for creating messages with four arguements.
   */
  static public class Arg4<T1, T2, T3, T4> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg4(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg4(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4) {
      return new Message(this, a1, a2, a3, a4);
    }

  }

  /**
   * Subclass for creating messages with five arguements.
   */
  static public class Arg5<T1, T2, T3, T4, T5> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg5(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg5(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5) {
      return new Message(this, a1, a2, a3, a4, a5);
    }

  }

  /**
   * Subclass for creating messages with six arguements.
   */
  static public class Arg6<T1, T2, T3, T4, T5, T6> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg6(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg6(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6) {
      return new Message(this, a1, a2, a3, a4, a5, a6);
    }

  }

  /**
   * Subclass for creating messages with seven arguements.
   */
  static public class Arg7<T1, T2, T3, T4, T5, T6, T7>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg7(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg7(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     * @param a7 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6, T7 a7) {
      return new Message(this, a1, a2, a3, a4, a5, a6, a7);
    }

  }

  /**
   * Subclass for creating messages with eight arguements.
   */
  static public class Arg8<T1, T2, T3, T4, T5, T6, T7, T8>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg8(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg8(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     * @param a7 message argument
     * @param a8 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6,
                          T7 a7, T8 a8) {
      return new Message(this, a1, a2, a3, a4, a5, a6, a7, a8);
    }

  }

  /**
   * Subclass for creating messages with nine arguements.
   */
  static public class Arg9<T1, T2, T3, T4, T5, T6, T7, T8, T9>
          extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg9(String rbBase, String key, Category category,
              Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg9(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     * @param a7 message argument
     * @param a8 message argument
     * @param a9 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6,
                          T7 a7, T8 a8, T9 a9) {
      return new Message(this, a1, a2, a3, a4, a5, a6, a7, a8, a9);
    }

  }

  /**
   * Subclass for creating messages with ten arguments.
   */
  static public class Arg10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>
          extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg10(String rbBase, String key, Category category,
               Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg10(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     * @param a7 message argument
     * @param a8 message argument
     * @param a9 message argument
     * @param a10 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6,
                          T7 a7, T8 a8, T9 a9, T10 a10) {
      return new Message(this, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10);
    }

  }

  /**
   * Subclass for creating messages with eleven arguements.
   */
  static public class Arg11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg11(String rbBase, String key, Category category,
               Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public Arg11(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param a1 message argument
     * @param a2 message argument
     * @param a3 message argument
     * @param a4 message argument
     * @param a5 message argument
     * @param a6 message argument
     * @param a7 message argument
     * @param a8 message argument
     * @param a9 message argument
     * @param a10 message argument
     * @param a11 message argument
     */
    public Message get(T1 a1, T2 a2, T3 a3, T4 a4, T5 a5, T6 a6,
                          T7 a7, T8 a8, T9 a9, T10 a10, T11 a11) {
      return new Message(this, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11);
    }

  }

  /**
   * Subclass for creating messages with an any number of arguements.
   * In general this class should be used when a message needs to be
   * defined with more arguments that can be handled with the current
   * number of subclasses
   */
  static public class ArgN extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public ArgN(String rbBase, String key, Category category,
               Severity severity, int ordinal) {
      super(rbBase, key, category, severity, ordinal);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     */
    public ArgN(String rbBase, String key, int mask,
              Severity severity, int ordinal) {
      super(rbBase, key, mask, severity, ordinal);
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param args message arguments
     */
    public Message get(Object... args) {
      return new Message(this, args);
    }

  }

  /**
   * A descriptor for creating a raw message from a <code>String</code>.
   * In general this descriptor should NOT be used internally.  OpenDS
   * plugins may want to use the mechanism to create messages without
   * storing their strings in resource bundles.
   */
  static class Raw extends MessageDescriptor {

    private String formatString;

    /**
     * Creates a parameterized instance.
     * @param formatString for created messages
     */
    Raw(CharSequence formatString) {
      this(formatString, Category.USER_DEFINED, Severity.INFORMATION);
    }

    /**
     * Creates a parameterized instance.
     * @param formatString for created messages
     * @param category for created messages
     * @param severity for created messages
     */
    Raw(CharSequence formatString, Category category,
                                Severity severity) {
      super(null, null, category, severity, null);
      this.formatString = formatString != null ? formatString.toString() : "";
    }

    /**
     * Creates a parameterized instance.  Created messages will
     * have a category of <code>Category.USER_DEFINED</code>.
     * @param formatString for created messages
     * @param mask for created messages
     * @param severity for created messages
     */
    Raw(CharSequence formatString, int mask, Severity severity) {
      super(null, null, mask, severity, null);
      this.formatString = formatString != null ? formatString.toString() : "";
    }

    /**
     * Creates a message with arguments that will replace format
     * specifiers in the assocated format string when the message
     * is rendered to string representation.
     * @return Message object
     * @param args message arguments
     */
    public Message get(Object... args) {
      return new Message(this, args);
    }

    /**
     * Overridden in order to bypass the resource bundle
     * plumbing and return the format string directly.
     * @param locale ignored
     * @return format string
     */
    @Override
    String getFormatString(Locale locale) {
      return this.formatString;
    }

  }

  /** String for accessing backing resource bundle. */
  protected String rbBase;

  /** Used for accessing format string from the resource bundle. */
  protected String key;

  /** Category for messages created by this descriptor. */
  protected Category category;

  /**
   * Custom mask associated with messages created by this
   * descriptor.  The value of this variable might be null
   * to indicate that the mask should come from
   * <code>category</code>.
   */
  protected Integer mask;

  /**
   * The severity associated with messages created by this
   * descriptor.
   */
  protected Severity severity;

  /**
   * The value that makes a message unique among other messages
   * having the same severity and category.  May be null for
   * raw messages.
   */
  protected Integer ordinal;

  /**
   * Obtains the category of this descriptor.  Gauranteed not to be null.
   * @return Category of this message
   */
  public Category getCategory() {
    return this.category;
  }

  /**
   * Obtains the severity of this descriptor.  Gauranteed not to be null.
   * @return Category of this message
   */
  public Severity getSeverity() {
    return this.severity;
  }

  /**
   * Obtains the ordinal value for this message which makes messages
   * unique among messages defined with the same category and severity.
   * @return int ordinal value
   */
  public int getOrdinal() {
    return this.ordinal;
  }

  /**
   * Returns the ID unique to all OpenDS messages.
   * @return unique ID
   */
  public int getId() {
    if (this.ordinal == null) { // ordinal may be null for raw messages
      return NULL_ID;
    } else {
      return this.ordinal | this.category.getMask() | this.severity.getMask();
    }
  }

  /**
   * Obtains the mask of this descriptor.  The mask will either be
   * the mask of the associated <code>Category</code> or the mask
   * explicitly set in the constructor.
   * @return Integer mask value
   */
  public int getMask() {
    if (this.mask != null) {
      return this.mask;
    } else {
      return this.category.getMask();
    }
  }

  /**
   * Returns the key for accessing the message template in a resource bundle.
   * May be null for raw messages.
   * @return key of this message
   */
  public String getKey() {
    return this.key;
  }

  /**
   * Obtains the resource bundle base string used to access the
   * resource bundle containing created message's format string.
   * May be null for raw messages.
   * @return string base
   */
  public String getBase() {
    return this.rbBase;
  }

  /**
   * Obtains the format string for constructing the string
   * value of this message according to the default
   * locale.
   * @return format string
   */
  String getFormatString() {
    return getFormatString(Locale.getDefault());
  }

  /**
   * Obtains the format string for constructing the string
   * value of this message according to the requested
   * locale.
   * @param locale for the returned format string
   * @return format string
   */
  String getFormatString(Locale locale) {
    ResourceBundle bundle = getBundle(locale);
    return bundle.getString(this.key);
  }

  private ResourceBundle getBundle(Locale locale) {
    if (locale == null) locale = Locale.getDefault();
    return ResourceBundle.getBundle(this.rbBase, locale);
  }

  /**
   * Creates a parameterized message descriptor.
   * @param rbBase string for accessing the underlying message bundle
   * @param key for accessing the format string from the message bundle
   * @param category of any created message
   * @param severity of any created message
   * @param ordinal of any created message
   */
  private MessageDescriptor(String rbBase, String key, Category category,
                     Severity severity, Integer ordinal) {
    if (category == null) {
      throw new NullPointerException("Null Category value for message " +
              "descriptor with key " + key);
    }
    if (severity == null) {
      throw new NullPointerException("Null Severity value for message " +
              "descriptor with key " + key);
    }
    this.rbBase = rbBase;
    this.key = key;
    this.category = category;
    this.severity = severity;
    this.ordinal = ordinal;
  }

  /**
   * Creates a parameterized message descriptor.  Messages created by
   * this descriptor will have a category of <code>Category.USER_DEFINED</code>
   * and have a custom mask indicated by <code>mask</code>.
   * @param rbBase string for accessing the underlying message bundle
   * @param key for accessing the format string from the message bundle
   * @param mask custom mask
   * @param severity of any created message
   * @param ordinal of any created message
   */
  private MessageDescriptor(String rbBase, String key, int mask,
                     Severity severity, Integer ordinal) {
    this(rbBase, key, Category.USER_DEFINED, severity, ordinal);
    this.mask = mask;
  }


}
