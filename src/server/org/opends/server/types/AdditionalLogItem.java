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
 *      Copyright 2011 ForgeRock AS
 */
package org.opends.server.types;



import org.opends.server.util.Validator;



/**
 * An additional log item for an operation which may be processed in the access
 * log.
 * <p>
 * Log items comprise of a source class, a key, and an optional value. If no
 * value is present then only the key will be displayed in the log, otherwise
 * both the key and value will usually be displayed using the format
 * {@code key=value}. Log item values are {@code Object} instances whose string
 * representation will be derived using the object's {@code toString()} method.
 * <p>
 * Log implementations may use the source class and/or key in order to filter
 * out unwanted log items.
 */
public final class AdditionalLogItem
{
  /**
   * Creates a new additional log item using the provided source and key, but no
   * value.
   *
   * @param source
   *          The class which generated the additional log item.
   * @param key
   *          The log item key.
   * @return The new additional log item.
   */
  public static AdditionalLogItem keyOnly(final Class<?> source,
      final String key)
  {
    Validator.ensureNotNull(source, key);
    return new AdditionalLogItem(source, key, null, false);
  }



  /**
   * Creates a new additional log item using the provided source, key, and
   * value. The value will be surrounded by quotes when serialized as a string.
   *
   * @param source
   *          The class which generated the additional log item.
   * @param key
   *          The log item key.
   * @param value
   *          The log item value.
   * @return The new additional log item.
   */
  public static AdditionalLogItem quotedKeyValue(final Class<?> source,
      final String key, final Object value)
  {
    Validator.ensureNotNull(source, key, value);
    return new AdditionalLogItem(source, key, value, true);
  }



  /**
   * Creates a new additional log item using the provided source, key, and
   * value. The value will not be surrounded by quotes when serialized as a
   * string.
   *
   * @param source
   *          The class which generated the additional log item.
   * @param key
   *          The log item key.
   * @param value
   *          The log item value.
   * @return The new additional log item.
   */
  public static AdditionalLogItem unquotedKeyValue(final Class<?> source,
      final String key, final Object value)
  {
    Validator.ensureNotNull(source, key, value);
    return new AdditionalLogItem(source, key, value, false);
  }



  private final Class<?> source;

  private final String key;

  private final Object value;

  private final boolean isQuoted;



  /**
   * Creates a new additional log item.
   *
   * @param source
   *          The class which generated the additional log item.
   * @param key
   *          The log item key.
   * @param value
   *          The log item value.
   * @param isQuoted
   *          {@code true} if this item's value should be surrounded by quotes
   *          during serialization.
   */
  private AdditionalLogItem(final Class<?> source, final String key,
      final Object value, final boolean isQuoted)
  {
    this.source = source;
    this.key = key;
    this.value = value;
    this.isQuoted = isQuoted;
  }



  /**
   * Returns the log item key.
   *
   * @return The log item key.
   */
  public String getKey()
  {
    return key;
  }



  /**
   * Returns the class which generated the additional log item.
   *
   * @return The class which generated the additional log item.
   */
  public Class<?> getSource()
  {
    return source;
  }



  /**
   * Returns the log item value, or {@code null} if this log item does not have
   * a value.
   *
   * @return The log item value, or {@code null} if this log item does not have
   *         a value.
   */
  public Object getValue()
  {
    return value;
  }



  /**
   * Returns {@code true} if this item's value should be surrounded by quotes
   * during serialization.
   *
   * @return {@code true} if this item's value should be surrounded by quotes
   *         during serialization.
   */
  public boolean isQuoted()
  {
    return isQuoted;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    if (value == null)
    {
      return key;
    }
    else
    {
      final StringBuilder builder = new StringBuilder(key.length() + 16);
      toString(builder);
      return builder.toString();
    }
  }



  /**
   * Appends the string representation of this additional log item to the
   * provided string builder.
   *
   * @param builder
   *          The string builder.
   * @return A reference to the updated string builder.
   */
  public StringBuilder toString(final StringBuilder builder)
  {
    builder.append(key);
    if (value != null)
    {
      builder.append('=');
      if (isQuoted)
      {
        builder.append('\'');
      }
      builder.append(value.toString());
      if (isQuoted)
      {
        builder.append('\'');
      }
    }
    return builder;
  }

}
