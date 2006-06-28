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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.types.AttributeType;

/**
 * The configuration of an attribute index including the kind of
 * indexing enabled and the index entry limit for each kind of indexing.
 */
public class IndexConfig
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.backends.jeb.IndexConfig";

  /**
   * The attribute type of the indexed attribute.
   */
  private AttributeType attributeType;

  /**
   * Indicates whether this index is configured for attribute equality.
   */
  private boolean isEquality = false;

  /**
   * Indicates whether this index is configured for attribute presence.
   */
  private boolean isPresence = false;

  /**
   * Indicates whether this index is configured for attribute substrings.
   */
  private boolean isSubstring = false;

  /**
   * Indicates whether this index is configured for attribute ordering.
   */
  private boolean isOrdering = false;

  /**
   * The index entry limit for the attribute equality database.
   */
  private int equalityEntryLimit = 4000;

  /**
   * The index entry limit for the attribute presence database.
   */
  private int presenceEntryLimit = 4000;

  /**
   * The index entry limit for the attribute substring database.
   */
  private int substringEntryLimit = 4000;

  /**
   * The configuration of substring length for the attribute substring database.
   */
  private int substringLength = 6;

  /**
   * The limit on the number of entry IDs that may be retrieved by cursoring
   * through an index.
   */
  private int cursorEntryLimit = 100000;

  /**
   * Constructs an index configuration for the given attribute type.
   * @param attributeType The attribute type of the index.
   */
  public IndexConfig(AttributeType attributeType)
  {
    this.attributeType = attributeType;
  }

  /**
   * Get the attribute type of the indexed attribute.
   * @return The attribute type of the indexed attribute.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }

  /**
   * Determine if this index is configured for attribute equality.
   * @return true if the index is configured for attribute equality.
   */
  public boolean isEqualityIndex()
  {
    return isEquality;
  }

  /**
   * Configures this index for attribute equality.
   * @param isEquality Sets attribute equality indexing if true.
   */
  public void setEqualityIndex(boolean isEquality)
  {
    this.isEquality = isEquality;
  }

  /**
   * Determine if this index is configured for attribute presence.
   * @return true if the index is configured for attribute presence.
   */
  public boolean isPresenceIndex()
  {
    return isPresence;
  }

  /**
   * Configures this index for attribute presence.
   * @param isPresence Sets attribute presence indexing if true.
   */
  public void setPresenceIndex(boolean isPresence)
  {
    this.isPresence = isPresence;
  }

  /**
   * Determine if this index is configured for attribute substrings.
   * @return true if the index is configured for attribute substrings.
   */
  public boolean isSubstringIndex()
  {
    return isSubstring;
  }

  /**
   * Configures this index for attribute substrings.
   * @param isSubstring Sets attribute substring indexing if true.
   */
  public void setSubstringIndex(boolean isSubstring)
  {
    this.isSubstring = isSubstring;
  }

  /**
   * Determine if this index is configured for attribute ordering.
   * @return true if the index is configured for attribute ordering.
   */
  public boolean isOrderingIndex()
  {
    return isOrdering;
  }

  /**
   * Configures this index for attribute ordering.
   * @param isOrdering Sets attribute ordering indexing if true.
   */
  public void setOrderingIndex(boolean isOrdering)
  {
    this.isOrdering = isOrdering;
  }

  /**
   * Get the configured entry limit for attribute equality indexing.
   * @return The index entry limit, or 0 if there is no limit.
   */
  public int getEqualityEntryLimit()
  {
    return equalityEntryLimit;
  }

  /**
   * Set the configured entry limit for attribute presence indexing.
   * @param indexEntryLimit The index entry limit, or 0 if there is no limit.
   */
  public void setPresenceEntryLimit(int indexEntryLimit)
  {
    presenceEntryLimit = indexEntryLimit;
  }

  /**
   * Get the configured entry limit for attribute presence indexing.
   * @return The index entry limit, or 0 if there is no limit.
   */
  public int getPresenceEntryLimit()
  {
    return presenceEntryLimit;
  }

  /**
   * Set the configured entry limit for attribute substring indexing.
   * @param indexEntryLimit The index entry limit, or 0 if there is no limit.
   */
  public void setSubstringEntryLimit(int indexEntryLimit)
  {
    substringEntryLimit = indexEntryLimit;
  }

  /**
   * Get the configured entry limit for attribute substring indexing.
   * @return The index entry limit, or 0 if there is no limit.
   */
  public int getSubstringEntryLimit()
  {
    return substringEntryLimit;
  }

  /**
   * Set the configured entry limit for attribute equality indexing.
   * @param indexEntryLimit The index entry limit, or 0 if there is no limit.
   */
  public void setEqualityEntryLimit(int indexEntryLimit)
  {
    equalityEntryLimit = indexEntryLimit;
  }

  /**
   * Get the configured substring length for attribute substring indexing.
   * @return The configured attribute substring length.
   */
  public int getSubstringLength()
  {
    return substringLength;
  }

  /**
   * Set the configured substring length for attribute substring indexing.
   *
   * @param substringLength The configured attribute substring length.
   */
  public void setSubstringLength(int substringLength)
  {
    this.substringLength = substringLength;
  }

  /**
   * Get the configured limit on the number of entry IDs that may be retrieved
   * by cursoring through the index.
   *
   * @return The index cursor entry limit.
   */
  public int getCursorEntryLimit()
  {
    return cursorEntryLimit;
  }
}
