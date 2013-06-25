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
package org.opends.server.core;

import java.util.List;
import java.util.Map;

import org.opends.server.types.*;

/**
 * This interface defines an operation that may be used to add a new entry to
 * the Directory Server.
 */
public interface AddOperation extends Operation
{

  /**
   * Retrieves the DN of the entry to add in a raw, unparsed form as it was
   * included in the request.  This may or may not actually contain a valid DN,
   * since no validation will have been performed on it.
   *
   * @return  The DN of the entry in a raw, unparsed form.
   */
  public abstract ByteString getRawEntryDN();

  /**
   * Specifies the raw entry DN for the entry to add.  This should only be
   * called by pre-parse plugins to alter the DN before it has been processed.
   * If the entry DN needs to be altered later in the process, then it should
   * be done using the <CODE>getEntryDN</CODE> and <CODE>setEntryDN</CODE>
   * methods.
   *
   * @param  rawEntryDN  The raw entry DN for the entry to add.
   */
  public abstract void setRawEntryDN(ByteString rawEntryDN);

  /**
   * Retrieves the DN of the entry to add.  This method should not be called
   * by pre-parse plugins because the parsed DN will not be available at that
   * time.
   *
   * @return  The DN of the entry to add, or <CODE>null</CODE> if it has not yet
   *          been parsed from the raw DN.
   */
  public abstract DN getEntryDN();

  /**
   * Retrieves the set of attributes in their raw, unparsed form as read from
   * the client request.  Some of these attributes may be invalid as no
   * validation will have been performed on them.  The returned list must not be
   * altered by the caller.
   *
   * @return  The set of attributes in their raw, unparsed form as read from the
   *          client request.
   */
  public abstract List<RawAttribute> getRawAttributes();

  /**
   * Adds the provided attribute to the set of raw attributes for this add
   * operation.  This should only be called by pre-parse plugins.
   *
   * @param  rawAttribute  The attribute to add to the set of raw attributes for
   *                       this add operation.
   */
  public abstract void addRawAttribute(RawAttribute rawAttribute);

  /**
   * Replaces the set of raw attributes for this add operation.  This should
   * only be called by pre-parse plugins.
   *
   * @param  rawAttributes  The set of raw attributes for this add operation.
   */
  public abstract void setRawAttributes(List<RawAttribute> rawAttributes);

  /**
   * Retrieves the set of processed user attributes for the entry to add.  This
   * should not be called by pre-parse plugins because this information will not
   * yet be available.  The contents of the returned map may be altered by the
   * caller.
   *
   * @return  The set of processed user attributes for the entry to add, or
   *          <CODE>null</CODE> if that information is not yet available.
   */
  public abstract Map<AttributeType, List<Attribute>> getUserAttributes();

  /**
   * Sets the specified attribute in the entry to add, overwriting any existing
   * attribute of the specified type if necessary.  This should only be called
   * from pre-operation plugins.  Note that pre-operation plugin processing is
   * invoked after access control and schema validation, so plugins should be
   * careful to only make changes that will not violate either schema or access
   * control rules.
   *
   * @param  attributeType  The attribute type for the attribute.
   * @param  attributeList  The attribute list for the provided attribute type.
   */
  public abstract void setAttribute(AttributeType attributeType,
      List<Attribute> attributeList);

  /**
   * Removes the specified attribute from the entry to add. This should only be
   * called from pre-operation plugins.  Note that pre-operation processing is
   * invoked after access control and schema validation, so plugins should be
   * careful to only make changes that will not violate either schema or access
   * control rules.
   *
   * @param  attributeType  The attribute tyep for the attribute to remove.
   */
  public abstract void removeAttribute(AttributeType attributeType);

  /**
   * Retrieves the change number that has been assigned to this operation.
   *
   * @return  The change number that has been assigned to this operation, or -1
   *          if none has been assigned yet or if there is no applicable
   *          synchronization mechanism in place that uses change numbers.
   */
  public abstract long getChangeNumber();

  /**
   * Specifies the change number that has been assigned to this operation by the
   * synchronization mechanism.
   *
   * @param  changeNumber  The change number that has been assigned to this
   *                       operation by the synchronization mechanism.
   */
  public abstract void setChangeNumber(long changeNumber);

  /**
   * Retrieves the set of processed objectclasses for the entry to add.  This
   * should not be called by pre-parse plugins because this information will not
   * yet be available.  The contents of the returned map may not be altered by
   * the caller.
   *
   * @return  The set of processed objectclasses for the entry to add, or
   *          <CODE>null</CODE> if that information is not yet available.
   */
  public abstract Map<ObjectClass,String> getObjectClasses();

  /**
   * Adds the provided objectclass to the entry to add.  This should only be
   * called from pre-operation plugins.  Note that pre-operation plugin
   * processing is invoked after access control and schema validation, so
   * plugins should be careful to only make changes that will not violate either
   * schema or access control rules.
   *
   * @param  objectClass  The objectclass to add to the entry.
   * @param  name         The name to use for the objectclass.
   */
  public abstract void addObjectClass(ObjectClass objectClass, String name);

  /**
   * Removes the provided objectclass from the entry to add.  This should only
   * be called from pre-operation plugins.  Note that pre-operation plugin
   * processing is invoked after access control and schema validation, so
   * plugins should be careful to only make changes that will not violate either
   * schema or access control rules.
   *
   * @param  objectClass  The objectclass to remove from the entry.
   */
  public abstract void removeObjectClass(ObjectClass objectClass);

  /**
   * Retrieves the set of processed operational attributes for the entry to add.
   * This should not be called by pre-parse plugins because this information
   * will not yet be available.  The contents of the returned map may be altered
   * by the caller.
   *
   * @return  The set of processed operational attributes for the entry to add,
   *          or <CODE>null</CODE> if that information is not yet available.
   */
  public abstract Map<AttributeType,List<Attribute>> getOperationalAttributes();

  /**
   * Retrieves the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @return  The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public abstract DN getProxiedAuthorizationDN();

  /**
   * Set the proxied authorization DN for this operation if proxied
   * authorization has been requested.
   *
   * @param proxiedAuthorizationDN
   *          The proxied authorization DN for this operation if proxied
   *          authorization has been requested, or {@code null} if proxied
   *          authorization has not been requested.
   */
  public abstract void setProxiedAuthorizationDN(DN proxiedAuthorizationDN);

}
