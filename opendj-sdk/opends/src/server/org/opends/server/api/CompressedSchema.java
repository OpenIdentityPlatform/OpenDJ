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
package org.opends.server.api;



import java.util.Map;

import org.opends.server.types.*;


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
   * @param  entryBuffer   The buffer to encode the object classes to.
   * @param  objectClasses The set of object classes for which to
   *                       retrieve the corresponding byte array
   *                       token.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public abstract void
         encodeObjectClasses(ByteStringBuilder entryBuffer,
                             Map<ObjectClass,String> objectClasses)
         throws DirectoryException;



  /**
   * Decodes an object class set from the provided byte string.
   *
   * @param  entryBuffer         The byte string containing the
   *                             object class set identifier.
   *
   * @return  The decoded object class set.
   *
   * @throws  DirectoryException  If the provided byte array cannot be
   *                              decoded as an object class set.
   */
  public abstract Map<ObjectClass,String>
         decodeObjectClasses(ByteSequenceReader entryBuffer)
         throws DirectoryException;



  /**
   * Encodes the information in the provided attribute to a byte
   * array.
   *
   * @param  entryBuffer The buffer to encode the attribute to.
   * @param  attribute   The attribute to be encoded.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to determine the appropriate
   *                              identifier.
   */
  public abstract void encodeAttribute(ByteStringBuilder entryBuffer,
                                       Attribute attribute)
         throws DirectoryException;



  /**
   * Decodes the contents of the provided array as an attribute at the
   * current position.
   *
   * @param  entryBuffer   The byte array containing the encoded
   *                       entry.
   *
   * @return  The decoded attribute.
   *
   * @throws  DirectoryException  If the attribute could not be
   *                              decoded properly for some reason.
   */
  public abstract Attribute decodeAttribute(
      ByteSequenceReader entryBuffer) throws DirectoryException;
}

