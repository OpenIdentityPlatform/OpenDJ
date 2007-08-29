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
package org.opends.server.api;



import java.util.Map;

import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ObjectClass;



/**
 * This class provides a utility for interacting with compressed
 * representations of schema elements.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class CompressedSchema
{
  /**
   * Encodes the provided set of object classes to a byte array.  If
   * the same set had been previously encoded, then the cached value
   * will be used.  Otherwise, a new value will be created.
   *
   * @param  objectClasses  The set of object classes for which to
   *                        retrieve the corresponding byte array
   *                        token.
   *
   * @return  A byte array containing the identifier assigned to the
   *          object class set.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public abstract byte[]
         encodeObjectClasses(Map<ObjectClass,String> objectClasses)
         throws DirectoryException;



  /**
   * Decodes an object class set from the provided byte array.
   *
   * @param  encodedObjectClasses  The byte array containing the
   *                               object class set identifier.
   *
   * @return  The decoded object class set.
   *
   * @throws  DirectoryException  If the provided byte array cannot be
   *                              decoded as an object class set.
   */
  public abstract Map<ObjectClass,String>
         decodeObjectClasses(byte[] encodedObjectClasses)
         throws DirectoryException;



  /**
   * Encodes the information in the provided attribute to a byte
   * array.
   *
   * @param  attribute  The attribute to be encoded.
   *
   * @return  An encoded representation of the provided attribute.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public abstract byte[] encodeAttribute(Attribute attribute)
         throws DirectoryException;



  /**
   * Decodes the contents of the provided array as an attribute.
   *
   * @param  encodedEntry  The byte array containing the encoded
   *                       entry.
   * @param  startPos      The position within the array of the first
   *                       byte for the attribute to decode.
   * @param  length        The number of bytes contained in the
   *                       encoded attribute.
   *
   * @return  The decoded attribute.
   *
   * @throws  DirectoryException  If the attribute could not be
   *                              decoded properly for some reason.
   */
  public abstract Attribute decodeAttribute(byte[] encodedEntry,
                                            int startPos, int length)
          throws DirectoryException;
}

