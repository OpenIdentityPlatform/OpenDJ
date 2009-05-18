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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.api;



import org.opends.messages.Message;

import java.util.List;

import org.opends.server.admin.std.server.AccessControlHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server access control handler. All
 * methods in this class should take the entire request into account
 * when making the determination, including any request controls that
 * might have been provided.
 *
 * @param <T>
 *          The type of access control configuration handled by this
 *          access control provider implementation.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class AccessControlHandler
                      <T extends AccessControlHandlerCfg>
{
  /**
   * Initializes the access control handler implementation based on
   * the information in the provided configuration entry.
   *
   * @param configuration
   *          The configuration object that contains the information
   *          to use to initialize this access control handler.
   * @throws ConfigException
   *           If an unrecoverable problem arises in the process of
   *           performing the initialization.
   * @throws InitializationException
   *           If a problem occurs during initialization that is not
   *           related to the server configuration.
   */
  public abstract void initializeAccessControlHandler(T configuration)

  throws ConfigException, InitializationException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this access control handler. It should be possible to call this
   * method on an uninitialized access control handler instance in
   * order to determine whether the handler would be able to use the
   * provided configuration. <BR>
   * <BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration to
   * the appropriate subclass type.
   *
   * @param configuration
   *          The access control handler configuration for which to
   *          make the determination.
   * @param unacceptableReasons
   *          A list that may be used to hold the reasons that the
   *          provided configuration is not acceptable.
   * @return {@code true} if the provided configuration is acceptable
   *         for this access control handler, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
      AccessControlHandlerCfg configuration,
      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation. It should be overridden by access control handler
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
   * the access control configuration. This method should not alter
   * the provided add operation in any way.
   *
   * @param addOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendAddOperation addOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided control is allowed based on the
   * access control configuration and the specified operation. This
   * method should not alter the provided operation in any way.
   *
   * @param dn
   *          A DN that can be used in the access determination.
   * @param op
   *          The operation to use in the determination.
   * @param control
   *          The control for which to make the determination.
   * @return {@code true} if the control should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      DN dn,
      Operation op,
      Control control)
    throws DirectoryException;



  /**
   * Indicates whether the provided bind operation is allowed based on
   * the access control configuration. This method should not alter
   * the provided bind operation in any way.
   *
   * @param bindOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendBindOperation bindOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided compare operation is allowed based
   * on the access control configuration. This method should not alter
   * the provided compare operation in any way.
   *
   * @param compareOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendCompareOperation compareOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided delete operation is allowed based
   * on the access control configuration. This method should not alter
   * the provided delete operation in any way.
   *
   * @param deleteOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendDeleteOperation deleteOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided extended operation is allowed
   * based on the access control configuration. This method should not
   * alter the provided extended operation in any way.
   *
   * @param extendedOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      ExtendedOperation extendedOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided modify operation is allowed based
   * on the access control configuration. This method should not alter
   * the provided modify operation in any way.
   *
   * @param modifyOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendModifyOperation modifyOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided modify DN operation is allowed
   * based on the access control configuration. This method should not
   * alter the provided modify DN operation in any way.
   *
   * @param modifyDNOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendModifyDNOperation modifyDNOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided search operation is allowed based
   * on the access control configuration. This method may only alter
   * the provided search operation in order to add an opaque block of
   * data to it that will be made available for use in determining
   * whether matching search result entries or search result
   * references may be allowed.
   *
   * @param searchOperation
   *          The operation for which to make the determination.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(
      LocalBackendSearchOperation searchOperation)
    throws DirectoryException;



  /**
   * Indicates whether the provided operation search filter is allowed
   * based on the access control configuration. This method should not
   * alter the provided operation in any way.
   *
   * @param operation
   *          The operation for which to make the determination.
   * @param entry
   *          The entry for which to make the determination.
   * @param filter
   *          The filter to check access on.
   * @return {@code true} if the operation should be allowed by the
   *         access control configuration, or {@code false} if not.
   * @throws DirectoryException
   *           If an error occurred while performing the access
   *           control check. For example, if an attribute could not
   *           be decoded. Care must be taken not to expose any
   *           potentially sensitive information in the exception.
   */
  public abstract boolean isAllowed(Operation operation, Entry entry,
    SearchFilter filter) throws DirectoryException;



  /**
   * Indicates whether the provided search result entry may be sent to
   * the client. Implementations <b>must not under any
   * circumstances</b> modify the search entry in any way.
   *
   * @param searchOperation
   *          The search operation with which the provided entry is
   *          associated.
   * @param searchEntry
   *          The search result entry for which to make the
   *          determination.
   * @return {@code true} if the access control configuration allows
   *         the entry to be returned to the client, or {@code false}
   *         if not.
   */
  public abstract boolean maySend(SearchOperation searchOperation,
      SearchResultEntry searchEntry);



  /**
   * Filter the contents of the provided entry such that it no longer
   * contains any attributes or values that the client is not
   * permitted to access.
   *
   * @param searchOperation
   *          The search operation with which the provided entry is
   *          associated.
   * @param searchEntry
   *          The search result entry to be filtered.
   * @return Returns the entry with filtered attributes and values
   *         removed.
   */
  public abstract SearchResultEntry filterEntry(
      SearchOperation searchOperation, SearchResultEntry searchEntry);



  /**
   * Filter the contents of the provided entry such that it no longer
   * contains any attributes or values that the client is not
   * permitted to access.
   *
   * @param operation
   *          The operation with which the provided entry is
   *          associated.
   * @param entry
   *          The entry to be filtered.
   * @return Returns the entry with filtered attributes and values
   *         removed.
   */
  public abstract SearchResultEntry filterEntry(
      Operation operation, Entry entry);



  /**
   * Indicates whether the provided search result reference may be
   * sent to the client based on the access control configuration.
   *
   * @param dn
   *          A DN that can be used in the access determination.
   * @param searchOperation
   *          The search operation with which the provided reference
   *          is associated.
   * @param searchReference
   *          The search result reference for which to make the
   *          determination.
   * @return {@code true} if the access control configuration allows
   *         the reference to be returned to the client, or {@code
   *         false} if not.
   */
  public abstract boolean maySend(DN dn,
                               SearchOperation searchOperation,
                               SearchResultReference searchReference);



  /**
   * Indicates if the specified proxy user entry can proxy, or act on
   * the behalf of the specified proxied user entry. The operation
   * parameter is used in the evaluation.
   *
   * @param proxyUser
   *          The entry to use as the proxy user.
   * @param proxiedUser
   *          The entry to be proxied by the proxy user.
   * @param operation
   *          The operation to use in the evaluation.
   * @return {@code true} if the access control configuration allows
   *         the proxy user to proxy the proxied user, or {@code
   *         false} if not.
   */
  public abstract boolean mayProxy(Entry proxyUser, Entry proxiedUser,
      Operation operation);

}
