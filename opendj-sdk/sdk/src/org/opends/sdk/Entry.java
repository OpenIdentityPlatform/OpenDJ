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

package org.opends.sdk;



import java.util.Collection;

import org.opends.sdk.schema.ObjectClass;

import com.sun.opends.sdk.util.LocalizedIllegalArgumentException;



/**
 * An entry, comprising of a distinguished name and zero or more
 * attributes.
 * <p>
 * Some methods require a schema in order to decode their parameters
 * (e.g. {@link #addAttribute(String, Object...)} and
 * {@link #setName(String)}). In these cases the default schema is used
 * unless an alternative schema is specified in the {@code Entry}
 * constructor. The default schema is not used for any other purpose. In
 * particular, an {@code Entry} will permit attributes to be added which
 * have been decoded using a different schema.
 * <p>
 * Full LDAP modify semantics are provided via the {@link #addAttribute}, {@link #removeAttribute}, and {@link #replaceAttribute} methods.
 * <p>
 * Implementations should specify any constraints or special behavior.
 * In particular, which methods are supported, and the order in which
 * attributes are returned using the {@link #getAttributes()} method.
 * <p>
 * TODO: can we return collections/lists instead of iterables?
 * <p>
 * TODO: containsAttributeValue(String, Object)
 */
public interface Entry
{

  /**
   * Adds all of the attribute values contained in {@code attribute} to
   * this entry, merging with any existing attribute values (optional
   * operation). If {@code attribute} is empty then this entry is left
   * unchanged.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify add semantics.
   *
   * @param attribute
   *          The attribute values to be added to this entry, merging
   *          with any existing attribute values.
   * @param duplicateValues
   *          A collection into which duplicate values will be added, or
   *          {@code null} if duplicate values should not be saved.
   * @return {@code true} if this entry changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be added.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  boolean addAttribute(Attribute attribute,
      Collection<ByteString> duplicateValues)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code attribute} to
   * this entry, merging with any existing attribute values (optional
   * operation). If {@code attribute} is empty then this entry is left
   * unchanged.
   * <p>
   * If {@code attribute} is an instance of {@code Attribute} then it
   * will be added to this entry as if {@link #addAttribute} was called.
   * <p>
   * If {@code attribute} is not an instance of {@code Attribute} then
   * its attribute description will be decoded using the schema
   * associated with this entry, and any attribute values which are not
   * instances of {@code ByteString} will be converted using the
   * {@link ByteString#valueOf(Object)} method.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify add semantics.
   *
   * @param attribute
   *          The attribute values to be added to this entry merging
   *          with any existing attribute values.
   * @return {@code true} if this entry changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be added.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  boolean addAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code values} to
   * this entry, merging with any existing attribute values (optional
   * operation). If {@code values} is {@code null} or empty then this
   * entry is left unchanged.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   * <p>
   * Any attribute values which are not instances of {@code ByteString}
   * will be converted using the {@link ByteString#valueOf(Object)}
   * method.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify add semantics.
   *
   * @param attributeDescription
   *          The name of the attribute whose values are to be added.
   * @param values
   *          The attribute values to be added to this entry, merging
   *          any existing attribute values.
   * @return This entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be added.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  Entry addAttribute(String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Removes all the attributes from this entry (optional operation).
   *
   * @return This entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes to be removed.
   */
  Entry clearAttributes() throws UnsupportedOperationException;



  /**
   * Indicates whether or not this entry contains the named attribute.
   *
   * @param attributeDescription
   *          The name of the attribute.
   * @return {@code true} if this entry contains the named attribute,
   *         otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  boolean containsAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * Indicates whether or not this entry contains the named attribute.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   *
   * @param attributeDescription
   *          The name of the attribute.
   * @return {@code true} if this entry contains the named attribute,
   *         otherwise {@code false}.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  boolean containsAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * Indicates whether or not this entry contains the provided object
   * class.
   *
   * @param objectClass
   *          The object class.
   * @return {@code true} if this entry contains the object class,
   *         otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code objectClass} was {@code null}.
   */
  boolean containsObjectClass(ObjectClass objectClass)
      throws NullPointerException;



  /**
   * Indicates whether or not this entry contains the named object
   * class.
   *
   * @param objectClass
   *          The name of the object class.
   * @return {@code true} if this entry contains the object class,
   *         otherwise {@code false}.
   * @throws NullPointerException
   *           If {@code objectClass} was {@code null}.
   */
  boolean containsObjectClass(String objectClass)
      throws NullPointerException;



  /**
   * Returns {@code true} if {@code object} is an entry which is equal
   * to this entry. Two entries are considered equal if their
   * distinguished names are equal, they both have the same number of
   * attributes, and every attribute contained in the first entry is
   * also contained in the second entry.
   *
   * @param object
   *          The object to be tested for equality with this entry.
   * @return {@code true} if {@code object} is an entry which is equal
   *         to this entry, or {@code false} if not.
   */
  boolean equals(Object object);



  /**
   * Returns an {@code Iterable} containing all the attributes in this
   * entry having an attribute description which is a sub-type of the
   * provided attribute description. The returned {@code Iterable} may
   * be used to remove attributes if permitted by this entry.
   *
   * @param attributeDescription
   *          The name of the attributes to be returned.
   * @return An {@code Iterable} containing the matching attributes.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Iterable<Attribute> findAttributes(
      AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * Returns an {@code Iterable} containing all the attributes in this
   * entry having an attribute description which is a sub-type of the
   * provided attribute description. The returned {@code Iterable} may
   * be used to remove attributes if permitted by this entry.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   *
   * @param attributeDescription
   *          The name of the attributes to be returned.
   * @return An {@code Iterable} containing the matching attributes.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Iterable<Attribute> findAttributes(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * Returns the named attribute contained in this entry, or {@code
   * null} if it is not included with this entry.
   *
   * @param attributeDescription
   *          The name of the attribute to be returned.
   * @return The named attribute, or {@code null} if it is not included
   *         with this entry.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Attribute getAttribute(AttributeDescription attributeDescription)
      throws NullPointerException;



  /**
   * Returns the named attribute contained in this entry, or {@code
   * null} if it is not included with this entry.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   *
   * @param attributeDescription
   *          The name of the attribute to be returned.
   * @return The named attribute, or {@code null} if it is not included
   *         with this entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Attribute getAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException, NullPointerException;



  /**
   * Returns the number of attributes in this entry.
   *
   * @return The number of attributes.
   */
  int getAttributeCount();



  /**
   * Returns an {@code Iterable} containing the attributes in this
   * entry. The returned {@code Iterable} may be used to remove
   * attributes if permitted by this entry.
   *
   * @return An {@code Iterable} containing the attributes.
   */
  Iterable<Attribute> getAttributes();



  /**
   * Returns the string representation of the distinguished name of this
   * entry.
   *
   * @return The string representation of the distinguished name.
   */
  DN getName();



  /**
   * Returns an {@code Iterable} containing the names of the object
   * classes in this entry. The returned {@code Iterable} may be used to
   * remove object classes if permitted by this entry.
   *
   * @return An {@code Iterable} containing the object classes.
   */
  Iterable<String> getObjectClasses();



  /**
   * Returns the hash code for this entry. It will be calculated as the
   * sum of the hash codes of the distinguished name and all of the
   * attributes.
   *
   * @return The hash code for this entry.
   */
  int hashCode();



  /**
   * Removes all of the attribute values contained in {@code attribute}
   * from this entry if it is present (optional operation). If {@code
   * attribute} is empty then the entire attribute will be removed if it
   * is present.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify delete semantics.
   *
   * @param attribute
   *          The attribute values to be removed from this entry, which
   *          may be empty if the entire attribute is to be removed.
   * @param missingValues
   *          A collection into which missing values will be added, or
   *          {@code null} if missing values should not be saved.
   * @return {@code true} if this entry changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be removed.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  boolean removeAttribute(Attribute attribute,
      Collection<ByteString> missingValues)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes the named attribute from this entry if it is present
   * (optional operation). If this attribute does not contain the
   * attribute, the call leaves this entry unchanged and returns {@code
   * false}.
   *
   * @param attributeDescription
   *          The name of the attribute to be removed.
   * @return {@code true} if this entry changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes to be removed.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  boolean removeAttribute(AttributeDescription attributeDescription)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Removes the named attribute from this entry if it is present
   * (optional operation). If this attribute does not contain the
   * attribute, the call leaves this entry unchanged.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   *
   * @param attributeDescription
   *          The name of the attribute to be removed.
   * @return This entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes to be removed.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Entry removeAttribute(String attributeDescription)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Removes all of the attribute values contained in {@code values}
   * from the named attribute in this entry if it is present (optional
   * operation). If {@code values} is {@code null} or empty then the
   * entire attribute will be removed if it is present.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   * <p>
   * Any attribute values which are not instances of {@code ByteString}
   * will be converted using the {@link ByteString#valueOf(Object)}
   * method.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify delete semantics.
   *
   * @param attributeDescription
   *          The name of the attribute whose values are to be removed.
   * @param values
   *          The attribute values to be removed from this entry, which
   *          may be {@code null} or empty if the entire attribute is to
   *          be removed.
   * @return This entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be removed.
   * @throws NullPointerException
   *           If {@code attributeDescription} was {@code null}.
   */
  Entry removeAttribute(String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code attribute} to
   * this entry, replacing any existing attribute values (optional
   * operation). If {@code attribute} is empty then the entire attribute
   * will be removed if it is present.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify replace semantics.
   *
   * @param attribute
   *          The attribute values to be added to this entry, replacing
   *          any existing attribute values, and which may be empty if
   *          the entire attribute is to be removed.
   * @return {@code true} if this entry changed as a result of this
   *         call.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be replaced.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  boolean replaceAttribute(Attribute attribute)
      throws UnsupportedOperationException, NullPointerException;



  /**
   * Adds all of the attribute values contained in {@code values} to
   * this entry, replacing any existing attribute values (optional
   * operation). If {@code values} is {@code null} or empty then the
   * entire attribute will be removed if it is present.
   * <p>
   * The attribute description will be decoded using the schema
   * associated with this entry.
   * <p>
   * Any attribute values which are not instances of {@code ByteString}
   * will be converted using the {@link ByteString#valueOf(Object)}
   * method.
   * <p>
   * <b>NOTE:</b> This method implements LDAP Modify replace semantics.
   *
   * @param attributeDescription
   *          The name of the attribute whose values are to be replaced.
   * @param values
   *          The attribute values to be added to this entry, replacing
   *          any existing attribute values, and which may be {@code
   *          null} or empty if the entire attribute is to be removed.
   * @return This entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code attributeDescription} could not be decoded
   *           using the schema associated with this entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit attributes or their values
   *           to be replaced.
   * @throws NullPointerException
   *           If {@code attribute} was {@code null}.
   */
  Entry replaceAttribute(String attributeDescription, Object... values)
      throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of this entry (optional operation).
   *
   * @param dn
   *          The string representation of the distinguished name.
   * @return This entry.
   * @throws LocalizedIllegalArgumentException
   *           If {@code dn} could not be decoded using the schema
   *           associated with this entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  Entry setName(String dn) throws LocalizedIllegalArgumentException,
      UnsupportedOperationException, NullPointerException;



  /**
   * Sets the distinguished name of this entry (optional operation).
   *
   * @param dn
   *          The distinguished name.
   * @return This entry.
   * @throws UnsupportedOperationException
   *           If this entry does not permit the distinguished name to
   *           be set.
   * @throws NullPointerException
   *           If {@code dn} was {@code null}.
   */
  Entry setName(DN dn) throws UnsupportedOperationException,
      NullPointerException;



  /**
   * Returns a string representation of this entry.
   *
   * @return The string representation of this entry.
   */
  String toString();
}
