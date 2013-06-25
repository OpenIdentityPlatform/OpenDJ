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
package org.opends.server.admin;
import org.opends.messages.Message;



import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import org.opends.server.admin.std.meta.RootCfgDefn;
import org.opends.server.util.Validator;



/**
 * An interface for querying the properties of a tag.
 * <p>
 * Tags are used to group related managed objects together into
 * categories.
 */
public final class Tag implements Comparable<Tag> {

  // All the tags.
  private static final Map<String, Tag> tags = new HashMap<String, Tag>();



  /**
   * Defines a new tag with the specified name.
   *
   * @param name
   *          The name of the new tag.
   */
  public static void define(String name) {
    Tag tag = new Tag(name);

    // Register the tag.
    tags.put(name, tag);
  }



  /**
   * Returns the tag associated with the specified name.
   *
   * @param name
   *          The name of the tag.
   * @return Returns the tag associated with the specified name.
   * @throws IllegalArgumentException
   *           If the tag name was not recognized.
   */
  public static Tag valueOf(String name) throws IllegalArgumentException {
    Validator.ensureNotNull(name);

    // Hack to force initialization of the tag definitions.
    RootCfgDefn.getInstance();

    Tag tag = tags.get(name.toLowerCase());

    if (tag == null) {
      throw new IllegalArgumentException("Unknown tag \"" + name + "\"");
    }

    return tag;
  }



  /**
   * Returns an unmodifiable collection view of the set of registered
   * tags.
   *
   * @return Returns an unmodifiable collection view of the set of
   *         registered tags.
   */
  public static Collection<Tag> values() {
    // Hack to force initialization of the tag definitions.
    RootCfgDefn.getInstance();

    return Collections.unmodifiableCollection(tags.values());
  }

  // The name of the tag.
  private final String name;



  // Private constructor.
  private Tag(String name) {
    this.name = name;
  }



  /**
   * {@inheritDoc}
   */
  public final int compareTo(Tag o) {
    return name.compareTo(o.name);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj instanceof Tag) {
      Tag other = (Tag) obj;
      return other.name.equals(this.name);
    }

    return false;
  }



  /**
   * Gets the name of this tag.
   *
   * @return Returns the name of this tag.
   */
  public final String getName() {
    return name;
  }



  /**
   * Gets the synopsis of this tag in the default locale.
   *
   * @return Returns the synopsis of this tag in the default locale.
   */
  public final Message getSynopsis() {
    return getSynopsis(Locale.getDefault());
  }



  /**
   * Gets the synopsis of this tag in the specified locale.
   *
   * @param locale
   *          The locale.
   * @return Returns the synopsis of this tag in the specified locale.
   */
  public final Message getSynopsis(Locale locale) {
    ManagedObjectDefinitionI18NResource resource =
      ManagedObjectDefinitionI18NResource.getInstance();
    String property = "tag." + name + ".synopsis";
    try {
      return resource.getMessage(RootCfgDefn.getInstance(), property, locale);
    } catch (MissingResourceException e) {
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode() {
    return name.hashCode();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public final String toString() {
    return name;
  }

}
