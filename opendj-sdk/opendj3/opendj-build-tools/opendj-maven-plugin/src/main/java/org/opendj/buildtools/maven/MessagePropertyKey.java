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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.opendj.buildtools.maven;



/**
 * OpenDJ messages defined in properties files must be defined with the ordinal
 * and in most cases category and severity encoded in the message key. This
 * class helps with generating and parsing of these keys. Keys must be of the
 * form DESCRIPTION_ORDINAL where:
 * <ul>
 * <li>DESCRIPTION is an uppercase string containing characters and the
 * underscore character for describing the purpose of the message.</li>
 * <li>ORDINAL is an integer that makes the message unique within the property
 * file.</li>
 * </ul>
 */
final class MessagePropertyKey implements Comparable<MessagePropertyKey>
{

  /**
   * Creates a message property key from a string value.
   * 
   * @param keyString
   *          from properties file
   * @return MessagePropertyKey created from string
   */
  static MessagePropertyKey parseString(final String keyString)
  {
    return new MessagePropertyKey(keyString);
  }



  private final String description;



  /**
   * Creates a parameterized instance.
   * 
   * @param description
   *          of this key
   */
  MessagePropertyKey(final String description)
  {
    this.description = description;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int compareTo(final MessagePropertyKey k)
  {
    return description.compareTo(k.description);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return getPropertyKeyName(true);
  }



  /**
   * Gets the description of this key.
   * 
   * @return description of this key
   */
  String getDescription()
  {
    return this.description;
  }



  /**
   * Gets the name of the LocalizableMessageDescriptor as it should appear in
   * the messages file.
   * 
   * @return name of message descriptor
   */
  String getMessageDescriptorName()
  {
    String name = this.description;

    name = name.replaceFirst("^MILD_WARN", "WARN");
    name = name.replaceFirst("^SEVERE_WARN", "WARN");
    name = name.replaceFirst("^MILD_ERR", "ERR");
    name = name.replaceFirst("^SEVERE_ERR", "ERR");
    name = name.replaceFirst("^FATAL_ERR", "ERR");

    return name;
  }



  /**
   * Gets the name of the key as it would appear in a properties file.
   * 
   * @param includeOrdinal
   *          in the name
   * @return string representing the property key
   */
  String getPropertyKeyName(final boolean includeOrdinal)
  {
    return description;
  }

}
