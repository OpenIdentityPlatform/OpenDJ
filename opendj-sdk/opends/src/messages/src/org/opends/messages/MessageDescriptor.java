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
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all Message descriptor classes.
 */
@org.opends.server.types.PublicAPI(
    stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate=false,
    mayExtend=false,
    mayInvoke=true)
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
   * Subclass for creating messages with no arguments.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg0 extends MessageDescriptor {

    /**
     * Cached copy of the message created by this descriptor.  We can
     * get away with this for the zero argument message because it is
     * immutable.
     */
    private Message message;

    private boolean requiresFormat;

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg0(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
      message = new Message(this);
      requiresFormat = containsArgumentLiterals(getFormatString());
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg0(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
      message = new Message(this);
      requiresFormat = containsArgumentLiterals(getFormatString());
    }

    /**
     * Creates a message.
     * @return Message object
     */
    public Message get() {
      return message;
    }

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return requiresFormat;
    }
  }

  /**
   * Subclass for creating messages with one argument.
   * @param <T1> The type of the first message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg1<T1> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg1(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg1(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with two arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg2<T1, T2> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg2(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg2(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with three arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg3<T1, T2, T3> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg3(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg3(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with four arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg4<T1, T2, T3, T4> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg4(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg4(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with five arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg5<T1, T2, T3, T4, T5> extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg5(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg5(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with six arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg6<T1, T2, T3, T4, T5, T6> extends
      MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg6(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg6(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with seven arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   * @param <T7> The type of the seventh message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg7<T1, T2, T3, T4, T5, T6, T7>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg7(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg7(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with eight arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   * @param <T7> The type of the seventh message argument.
   * @param <T8> The type of the eighth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg8<T1, T2, T3, T4, T5, T6, T7, T8>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg8(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg8(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with nine arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   * @param <T7> The type of the seventh message argument.
   * @param <T8> The type of the eighth message argument.
   * @param <T9> The type of the ninth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg9<T1, T2, T3, T4, T5, T6, T7, T8, T9>
          extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg9(String rbBase, String key, Category category,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg9(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with ten arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   * @param <T7> The type of the seventh message argument.
   * @param <T8> The type of the eighth message argument.
   * @param <T9> The type of the ninth message argument.
   * @param <T10> The type of the tenth message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10>
          extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg10(String rbBase, String key, Category category,
               Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg10(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with eleven arguments.
   * @param <T1> The type of the first message argument.
   * @param <T2> The type of the second message argument.
   * @param <T3> The type of the third message argument.
   * @param <T4> The type of the fourth message argument.
   * @param <T5> The type of the fifth message argument.
   * @param <T6> The type of the sixth message argument.
   * @param <T7> The type of the seventh message argument.
   * @param <T8> The type of the eighth message argument.
   * @param <T9> The type of the ninth message argument.
   * @param <T10> The type of the tenth message argument.
   * @param <T11> The type of the eleventh message argument.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class Arg11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11>
          extends MessageDescriptor
  {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg11(String rbBase, String key, Category category,
               Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public Arg11(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * Subclass for creating messages with an any number of arguments.
   * In general this class should be used when a message needs to be
   * defined with more arguments that can be handled with the current
   * number of subclasses
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
      mayInstantiate=true,
      mayExtend=false,
      mayInvoke=true)
  static public final class ArgN extends MessageDescriptor {

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param category of created messages
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public ArgN(String rbBase, String key, Category category,
               Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, category, severity, ordinal, classLoader);
    }

    /**
     * Creates a parameterized instance.
     * @param rbBase base of the backing resource bundle
     * @param key for accessing the format string from the resource bundle
     * @param mask to apply to the USER_DEFINED category
     * @param severity of created messages
     * @param ordinal of created messages
     * @param classLoader the class loader to be used to get the ResourceBundle
     */
    public ArgN(String rbBase, String key, int mask,
              Severity severity, int ordinal, ClassLoader classLoader) {
      super(rbBase, key, mask, severity, ordinal, classLoader);
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
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return true;
    }

  }

  /**
   * A descriptor for creating a raw message from a <code>String</code>.
   * In general this descriptor should NOT be used internally.  OpenDS
   * plugins may want to use the mechanism to create messages without
   * storing their strings in resource bundles.
   */
  @org.opends.server.types.PublicAPI(
      stability=org.opends.server.types.StabilityLevel.PRIVATE
  )
  static final class Raw extends MessageDescriptor {

    private String formatString;

    private boolean requiresFormatter;

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
      super(null, null, category, severity, null, null);
      this.formatString = formatString != null ? formatString.toString() : "";
      this.requiresFormatter = formatString.toString().matches(".*%.*");
    }

    /**
     * Creates a parameterized instance.  Created messages will
     * have a category of <code>Category.USER_DEFINED</code>.
     * @param formatString for created messages
     * @param mask for created messages
     * @param severity for created messages
     */
    Raw(CharSequence formatString, int mask, Severity severity) {
      super(null, null, mask, severity, null, null);
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

    /**
     * {@inheritDoc}
     */
    boolean requiresFormatter() {
      return this.requiresFormatter;
    }

  }

  /** String for accessing backing resource bundle. */
  private final String rbBase;

  /** Used for accessing format string from the resource bundle. */
  private final String key;

  /** Category for messages created by this descriptor. */
  private final Category category;

  /**
   * Custom mask associated with messages created by this
   * descriptor.  The value of this variable might be null
   * to indicate that the mask should come from
   * <code>category</code>.
   */
  private final Integer mask;

  /**
   * The severity associated with messages created by this
   * descriptor.
   */
  private final Severity severity;

  /**
   * The value that makes a message unique among other messages
   * having the same severity and category.  May be null for
   * raw messages.
   */
  private final Integer ordinal;

  /**
   * The class loader to be used to retrieve the ResourceBundle.  If null
   * the default class loader will be used.
   */
  private final ClassLoader classLoader;


  private final Map<Locale,String> formatStrMap = new HashMap<Locale,String>();

  /**
   * Obtains the category of this descriptor.  Gauranteed not to be null.
   * @return Category of this message
   */
  public final Category getCategory() {
    return this.category;
  }

  /**
   * Obtains the severity of this descriptor.  Gauranteed not to be null.
   * @return Category of this message
   */
  public final Severity getSeverity() {
    return this.severity;
  }

  /**
   * Obtains the ordinal value for this message which makes messages
   * unique among messages defined with the same category and severity.
   * @return int ordinal value
   */
  public final int getOrdinal() {
    if (this.ordinal == null)
      return 0;
    else
      return this.ordinal;
  }

  /**
   * Returns the ID unique to all OpenDS messages.
   * @return unique ID
   */
  public final int getId() {
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
  public final int getMask() {
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
  public final String getKey() {
    return this.key;
  }

  /**
   * Obtains the resource bundle base string used to access the
   * resource bundle containing created message's format string.
   * May be null for raw messages.
   * @return string base
   */
  public final String getBase() {
    return this.rbBase;
  }

  /**
   * Indicates whether or not this descriptor format string should
   * be processed by Formatter during string rendering.
   * @return boolean where true means Formatter should be used; false otherwise
   * @see java.util.Formatter
   */
  abstract boolean requiresFormatter();

  /**
   * Obtains the format string for constructing the string
   * value of this message according to the default
   * locale.
   * @return format string
   */
  final String getFormatString() {
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
    String fmtStr = formatStrMap.get(locale);
    if (fmtStr == null) {
      ResourceBundle bundle = getBundle(locale);
      fmtStr = bundle.getString(this.key);
      formatStrMap.put(locale, fmtStr);
    }
    return fmtStr;
  }

  /**
   * Indicates whether or not formatting should be applied
   * to the given format string.  Note that a format string
   * might have literal specifiers (%% or %n for example) that
   * require formatting but are not replaced by arguments.
   * @param s candiate for formatting
   * @return boolean where true indicates that the format
   *         string requires formatting
   */
  protected final boolean containsArgumentLiterals(String s) {
    return s.matches(".*%[n|%].*"); // match Formatter literals
  }

  private ResourceBundle getBundle(Locale locale) {
    if (locale == null) locale = Locale.getDefault();
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
   * Creates a parameterized message descriptor.
   * @param rbBase string for accessing the underlying message bundle
   * @param key for accessing the format string from the message bundle
   * @param category of any created message
   * @param severity of any created message
   * @param ordinal of any created message
   * @param classLoader the class loader to be used to get the ResourceBundle
   */
  private MessageDescriptor(String rbBase, String key, Category category,
                     Severity severity, Integer ordinal,
                     ClassLoader classLoader) {
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
    this.classLoader = classLoader;
    this.mask = null;
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
   * @param classLoader the class loader to be used to get the ResourceBundle
   */
  private MessageDescriptor(String rbBase, String key, int mask,
                     Severity severity, Integer ordinal,
                     ClassLoader classLoader) {
    if (severity == null) {
      throw new NullPointerException("Null Severity value for message " +
              "descriptor with key " + key);
    }
    this.rbBase = rbBase;
    this.key = key;
    this.category = Category.USER_DEFINED;
    this.severity = severity;
    this.ordinal = ordinal;
    this.classLoader = classLoader;
    this.mask = mask;
  }

}
