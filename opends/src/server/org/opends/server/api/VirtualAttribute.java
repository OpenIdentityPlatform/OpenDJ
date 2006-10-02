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
package org.opends.server.api;



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.AttributeType;
import org.opends.server.types.ByteString;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SearchResultEntry;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required for one or more virtual attributes.
 */
public abstract class VirtualAttribute
{
  /**
   * Initializes this virtual attribute based on the information in
   * the provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the
   *                      information to use to initialize this
   *                      virtual attribute.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeVirtualAttribute(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Retrieves the name of this virtual attribute.
   *
   * @return  The name of this virtual attribute.
   */
  public abstract String getName();



  /**
   * Indicates whether the provided entry matches the given
   * attribute-value assertion.
   *
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  attributeType   The attribute type for which to make the
   *                         determination.
   * @param  assertionValue  The value for which to make the
   *                         determination.
   *
   * @return  <CODE>true</CODE> if the given entry matches the
   *          provided assertion details, or <CODE>false</CODE> if
   *          not.
   */
  public abstract boolean entryMatches(Entry entry,
                               AttributeType attributeType,
                               ByteString assertionValue);



  /**
   * Updates the provided entry if appropriate to include one or more
   * values for this virtual attribute.  The entry may be left
   * unaltered if it is not one that should contain this virtual
   * attribute.  This method may alter real attributes if appropriate
   * as well.
   *
   * @param  entry            The entry to be updated if necessary.
   * @param  searchOperation  The search operation with which the
   *                          entry is associated.
   */
  public abstract void updateEntry(SearchResultEntry entry,
                                   SearchOperation searchOperation);



  /**
   * Indicates whether this attribute may be included in search
   * filters as part of the criteria for locating entries.
   *
   * @return  <CODE>true</CODE> if this attribute may be included in
   *          search filters, or <CODE>false</CODE> if not.
   */
  public abstract boolean isSearchable();



  /**
   * Processes the provided search operation in which the search
   * criteria includes an operation targeted at this virtual
   * attribute.  This method will only be called if
   * <CODE>isSearchable</CODE> returns true and it is not possible to
   * construct a manageable candidate list by processing other
   * elements of the search criteria.
   *
   * @param  searchOperation  The search operation to be processed.
   */
  public abstract void processSearch(SearchOperation searchOperation);
}

