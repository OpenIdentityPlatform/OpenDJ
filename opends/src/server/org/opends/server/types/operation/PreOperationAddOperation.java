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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types.operation;



import java.util.List;
import java.util.Map;

import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;



/**
 * This class defines a set of methods that are available for use by
 * pre-operation plugins for add operations.  Note that this interface
 * is intended only to define an API for use by plugins and is not
 * intended to be implemented by any custom classes.
 */
public interface PreOperationAddOperation
       extends PreOperationOperation
{
  /**
   * Retrieves the DN of the entry to add in a raw, unparsed form as
   * it was included in the request.  This may or may not actually
   * contain a valid DN, since no validation will have been performed
   * on it.
   *
   * @return  The DN of the entry in a raw, unparsed form.
   */
  public ByteString getRawEntryDN();



  /**
   * Retrieves the set of attributes in their raw, unparsed form as
   * read from the client request.  Some of these attributes may be
   * invalid as no validation will have been performed on them.  The
   * returned list must not be altered by the caller.
   *
   * @return  The set of attributes in their raw, unparsed form as
   *          read from the client request.
   */
  public List<LDAPAttribute> getRawAttributes();



  /**
   * Retrieves the DN of the entry to add.
   *
   * @return  The DN of the entry to add.
   */
  public DN getEntryDN();



  /**
   * Retrieves the set of processed objectclasses for the entry to
   * add.  The contents of the returned map must not be altered by the
   * caller.
   *
   * @return  The set of processed objectclasses for the entry to add.
   */
  public Map<ObjectClass,String> getObjectClasses();



  /**
   * Adds the provided objectclass to the entry to add.  Note that
   * pre-operation plugin processing is invoked after access control
   * and schema validation, so plugins should be careful to only make
   * changes that will not violate either schema or access control
   * rules.
   *
   * @param  objectClass  The objectclass to add to the entry.
   * @param  name         The name to use for the objectclass.
   */
  public void addObjectClass(ObjectClass objectClass, String name);



  /**
   * Removes the provided objectclass from the entry to add.  Note
   * that pre-operation plugin processing is invoked after access
   * control and schema validation, so plugins should be careful to
   * only make changes that will not violate either schema or access
   * control rules.
   *
   * @param  objectClass  The objectclass to remove from the entry.
   */
  public void removeObjectClass(ObjectClass objectClass);



  /**
   * Retrieves the set of processed user attributes for the entry to
   * add.  The contents of the returned map must not be altered by the
   * caller.
   *
   * @return  The set of processed user attributes for the entry to
   *          add.
   */
  public Map<AttributeType,List<Attribute>> getUserAttributes();



  /**
   * Retrieves the set of processed operational attributes for the
   * entry to add.  The contents of the returned map must not be
   * altered by the caller.
   *
   * @return  The set of processed operational attributes for the
   *          entry to add.
   */
  public Map<AttributeType,List<Attribute>>
              getOperationalAttributes();



  /**
   * Sets the specified attribute in the entry to add, overwriting any
   * existing attribute of the specified type if necessary.  Note that
   * pre-operation plugin processing is invoked after access control
   * and schema validation, so plugins should be careful to only make
   * changes that will not violate either schema or access control
   * rules.
   *
   * @param  attributeType  The attribute type for the attribute.
   * @param  attributeList  The attribute list for the provided
   *                        attribute type.
   */
  public void setAttribute(AttributeType attributeType,
                           List<Attribute> attributeList);



  /**
   * Removes the specified attribute from the entry to add.  Note that
   * pre-operation processing is invoked after access control and
   * schema validation, so plugins should be careful to only make
   * changes that will not violate either schema or access control
   * rules.
   *
   * @param  attributeType  The attribute tyep for the attribute to
   *                        remove.
   */
  public void removeAttribute(AttributeType attributeType);



  /**
   * Retrieves the entry to be added to the server.  The contents of
   * the returned entry must not be altered by the caller.
   *
   * @return  The entry to be added to the server.
   */
  public Entry getEntryToAdd();
}

