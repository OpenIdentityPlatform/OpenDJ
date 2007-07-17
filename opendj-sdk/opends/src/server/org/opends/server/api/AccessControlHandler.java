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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import java.util.List;

import org.opends.server.admin.std.server.AccessControlHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server access control handler.  All
 * methods in this class should take the entire request into account
 * when making the determination, including any request controls that
 * might have been provided.
 *
 * @param  <T>  The type of access control configuration handled by
 *              this access control provider implementation.
 */
public abstract class AccessControlHandler
                      <T extends AccessControlHandlerCfg>
{
  /**
   * Initializes the access control handler implementation based on
   * the information in the provided configuration entry.
   *
   * @param  configuration  The configuration object that contains the
   *                        information to use to initialize this
   *                        access control handler.
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
  public abstract void initializeAccessControlHandler(T configuration)

         throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this access control handler.  It should be possible to call this
   * method on an uninitialized access control handler instance in
   * order to determine whether the handler would be able to use the
   * provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The access control handler
   *                              configuration for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this access control handler, or {@code false} if
   *          not.
   */
  public boolean isConfigurationAcceptable(
                      AccessControlHandlerCfg configuration,
                      List<String> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by access control handler
    // implementations that wish to perform more detailed validation.
    return true;
  }



  /**
   * Performs any necessary finalization for the access control
   * handler implementation. This will be called just after the
   * handler has been deregistered with the server but before it has
   * been unloaded.
   */
  public abstract void finalizeAccessControlHandler();



  /**
   * Indicates whether the provided add operation is allowed based on
   * the access control configuration.  This method should not alter
   * the provided add operation in any way.
   *
   * @param  addOperation  The operation for which to make the
   *                       determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendAddOperation
                                         addOperation);



  /**
   * Indicates whether the provided bind operation is allowed based on
   * the access control configuration.  This method should not alter
   * the provided bind operation in any way.
   *
   * @param  bindOperation  The operation for which to make the
   *                        determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendBindOperation
                                         bindOperation);



  /**
   * Indicates whether the provided compare operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided compare operation in any way.
   *
   * @param  compareOperation  The operation for which to make the
   *                           determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendCompareOperation
      compareOperation);



  /**
   * Indicates whether the provided delete operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided delete operation in any way.
   *
   * @param  deleteOperation  The operation for which to make the
   *                          determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendDeleteOperation
                                         deleteOperation);



  /**
   * Indicates whether the provided extended operation is allowed
   * based on the access control configuration.  This method should
   * not alter the provided extended operation in any way.
   *
   * @param  extendedOperation  The operation for which to make the
   *                            determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(ExtendedOperation
                                         extendedOperation);



  /**
   * Indicates whether the provided modify operation is allowed based
   * on the access control configuration.  This method should not
   * alter the provided modify operation in any way.
   *
   * @param  modifyOperation  The operation for which to make the
   *                          determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendModifyOperation
                                         modifyOperation);



  /**
   * Indicates whether the provided modify DN operation is allowed
   * based on the access control configuration.  This method should
   * not alter the provided modify DN operation in any way.
   *
   * @param  modifyDNOperation  The operation for which to make the
   *                            determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendModifyDNOperation
                                         modifyDNOperation);



  /**
   * Indicates whether the provided search operation is allowed based
   * on the access control configuration.  This method may only alter
   * the provided search operation in order to add an opaque block of
   * data to it that will be made available for use in determining
   * whether matching search result entries or search result
   * references may be allowed.
   *
   * @param  searchOperation  The operation for which to make the
   *                          determination.
   *
   * @return  {@code true} if the operation should be allowed by the
   *          access control configuration, or {@code false} if not.
   */
  public abstract boolean isAllowed(LocalBackendSearchOperation
                                         searchOperation);



  /**
   * Indicates whether the provided search result entry may be sent to
   * the client. Implementations <b>must not under any
   * circumstances</b> modify the search entry in any way.
   *
   * @param  searchOperation  The search operation with which the
   *                          provided entry is associated.
   * @param  searchEntry      The search result entry for which to
   *                          make the determination.
   *
   * @return  {@code true} if the access control configuration allows
   *          the entry to be returned to the client, or {@code false}
   *          if not.
   */
  public abstract boolean maySend(SearchOperation searchOperation,
                                  SearchResultEntry searchEntry);



  /**
   * Filter the contents of the provided entry such that it no longer
   * contains any attributes or values that the client is not
   * permitted to access.
   *
   * @param searchOperation The search operation with which the
   *                        provided entry is associated.
   * @param searchEntry     The search result entry to be filtered.
   *
   * @return  Returns the entry with filtered attributes and values
   *          removed.
   */
  public abstract SearchResultEntry
                       filterEntry(SearchOperation searchOperation,
                                   SearchResultEntry searchEntry);



  /**
   * Indicates whether the provided search result reference may be
   * sent to the client.
   *
   * @param  searchOperation  The search operation with which the
   *                          provided reference is associated.
   * @param  searchReference  The search result reference for which to
   *                          make the determination.
   *
   * @return  {@code true} if the access control configuration allows
   *          the reference to be returned to the client, or
   *          {@code false} if not.
   */
  public abstract boolean maySend(SearchOperation searchOperation,
                               SearchResultReference searchReference);



  /**
   * Indicates whether a proxied authorization control is allowed
   * based on the current operation and the new authorization entry.
   *
   * @param  operation              The operation with which the
   *                                proxied authorization control is
   *                                associated.
   * @param  newAuthorizationEntry  The new authorization entry
   *                                related to the proxied
   *                                authorization control
   *                                authorization ID.
   *
   * @return  {@code true} if the operation should be allowed to use
   *          the proxied authorization control, or {@code false} if
   *          not.
   */
  public abstract boolean isProxiedAuthAllowed(Operation operation,
                               Entry newAuthorizationEntry);



  /**
   * Indicates whether a getEffectiveRights control is allowed
   * based on the current operation and the control contents.
   *
   * @param  operation  The operation with which the
   *                    getEffectiveRights control is associated.
   *                    This is always a SearchOperation.
   * @param  control    The control class containing the decoded
   *                    getEffectiveRights control contents.
   *
   * @return  {@code true} if the use of the getEffectiveRights
   *          control should be allowed, or {@code false} if not.
   */
  public abstract boolean isGetEffectiveRightsAllowed(
                               SearchOperation operation,
                               Control control);
}

