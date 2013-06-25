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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.types;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * An iterable read-only view of of a set of attribute values returned
 * from methods such as
 * {@link org.opends.server.types.Entry#getAttribute(AttributeType)}.
 * <p>
 * Using instances of this class it is possible to filter out
 * attribute values which do not have the correct options set. This is
 * achieved without having to duplicate the set of attributes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class AttributeValueIterable implements
    Iterable<AttributeValue> {

  // The set of attributes having the same type and options.
  private Iterable<Attribute> attributes;

  // The set of options which all values must contain.
  private HashSet<String> options;



  /**
   * Create a new attribute value iterable object.
   *
   * @param  attributes  The set of attributes having the same type.
   *                     Can be {@code null}.
   */
  public AttributeValueIterable(Iterable<Attribute> attributes) {
    this(attributes, null);

  }

  /**
   * Create a new attribute value iterable object.
   *
   * @param  attributes  The set of attributes having the same type.
   *                     Can be {@code null}.
   * @param  options     The set of options which all values must
   *                     contain, or {@code null} if no options are
   *                     required.
   */
  public AttributeValueIterable(Iterable<Attribute> attributes,
                                HashSet<String> options) {

    this.attributes = attributes;
    this.options = options;
  }

  /**
   * Retrieves an iterator that can be used to cursor through the set
   * of attribute values.
   *
   * @return  An iterator that can be used to cursor through the set
   *          of attribute values.
   */
  public Iterator<AttributeValue> iterator() {

    return new AttributeValueIterator();
  }

  /**
   * Private iterator implementation.
   */
  private class AttributeValueIterator
          implements Iterator<AttributeValue> {
    // Flag indicating whether iteration can proceed.
    private boolean hasNext;

    // The current attribute iterator.
    private Iterator<Attribute> attributeIterator;

    // The current value iterator.
    private Iterator<AttributeValue> valueIterator;

    /**
     * Create a new attribute value iterator over the attribute set.
     */
    private AttributeValueIterator() {

      this.valueIterator = null;

      if (attributes != null) {
        this.attributeIterator = attributes.iterator();
        this.hasNext = skipNonMatchingAttributes();
      } else {
        this.attributeIterator = null;
        this.hasNext = false;
      }
    }

    /**
     * Indicates whether there are more attribute values to return.
     *
     * @return  {@code true} if there are more attribute values to
     *          return, or {@code false} if not.
     */
    public boolean hasNext() {

      return hasNext;
    }

    /**
     * Retrieves the next attribute value in the set.
     *
     * @return  The next attribute value in the set.
     *
     * @throws  NoSuchElementException  If there are no more values to
     *                                  return.
     */
    public AttributeValue next()
           throws NoSuchElementException
    {
      if (hasNext == false) {
        throw new NoSuchElementException();
      }

      AttributeValue value = valueIterator.next();

      // We've reached the end of this array list, so skip to the next
      // non-empty one.
      if (valueIterator.hasNext() == false) {
        hasNext = skipNonMatchingAttributes();
      }

      return value;
    }

    /**
     * Removes the last attribute value retrieved from the set.  Note
     * that this operation is not supported and will always cause an
     * {@code UnsupportedOperationException} to be thrown.
     *
     * @throws  UnsupportedOperationException  If the last value
     *                                         cannot be removed.
     */
    public void remove()
           throws UnsupportedOperationException
    {
      throw new UnsupportedOperationException();
    }

    /**
     * Skip past any empty attributes or attributes that do not have
     * the correct set of options until we find one that contains some
     * values.
     *
     * @return  {@code true} if iteration can continue, or
     *          {@code false} if not.
     */
    private boolean skipNonMatchingAttributes() {

      while (attributeIterator.hasNext()) {
        Attribute attribute = attributeIterator.next();

        if (attribute.hasAllOptions(options)) {
          valueIterator = attribute.iterator();
          if (valueIterator.hasNext()) {
            return true;
          }
        }
      }

      return false;
    }
  }
}
