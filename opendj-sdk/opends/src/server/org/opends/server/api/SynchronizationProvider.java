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



import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.SynchronizationProviderResult;




/**
 * This class defines the set of methods and structures that are
 * available for use in a Directory Server synchronization provider.
 * A synchronization provider ensures that changes in one instance of
 * the Directory Server are properly communicated to other instances,
 * and potentially to other kinds of applications, so that they can be
 * updated accordingly.
 */
public abstract class SynchronizationProvider
{



  /**
   * Performs any initialization that might be necessary for this
   * synchronization provider.
   *
   * @param  configEntry  The entry containing the configuration
   *                      information for this synchronization
   *                      provider.
   *
   * @throws  ConfigException  If the provided entry does not contain
   *                           a valid configuration for this
   *                           synchronization provider.
   *
   * @throws  InitializationException  If a problem occurs while
   *                                   initializing the
   *                                   synchronization provider that
   *                                   is not related to the server
   *                                   configuration.
   */
  public abstract void initializeSynchronizationProvider(
                            ConfigEntry configEntry)
         throws ConfigException, InitializationException;



  /**
   * Performs any necessary finalization for this synchronization
   * provider.  This will be called just after the provider has been
   * deregistered with the server but before it has been unloaded.
   */
  public void finalizeSynchronizationProvider()
  {
    // No implementation is required by default.
  }



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
              handleConflictResolution(AddOperation addOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult(true);
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided add operation is performed.  This
   * method will be invoked immediately before processing the add
   * operation in the backend.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
                       doPreOperation(AddOperation addOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided add operation is performed.  This
   * method will be invoked immediately after processing the add
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  addOperation  The add operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(AddOperation addOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
       handleConflictResolution(DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult(true);
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided delete operation is performed.  This
   * method will be invoked immediately before processing the delete
   * operation in the backend.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
              doPreOperation(DeleteOperation deleteOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided delete operation is performed.  This
   * method will be invoked immediately after processing the delete
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  deleteOperation  The delete operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
                            DeleteOperation deleteOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
              handleConflictResolution(ModifyOperation
                                            modifyOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult(true);
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided modify operation is performed.  This
   * method will be invoked immediately before processing the modify
   * operation in the backend.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
                       doPreOperation(ModifyOperation modifyOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided modify operation is performed.  This
   * method will be invoked immediately after processing the modify
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  modifyOperation  The modify operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
                            ModifyOperation modifyOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing for the
   * operation that may be needed early on to deal with any potential
   * conflict resolution or updates to historical data.  This method
   * will be invoked immediately after a lock is acquired on the
   * target entry.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public SynchronizationProviderResult
              handleConflictResolution(ModifyDNOperation
                                            modifyDNOperation)
         throws DirectoryException
  {
    // No processing is required by default.
    return new SynchronizationProviderResult(true);
  }



  /**
   * Performs any necessary synchronization processing that may be
   * needed before the provided modify DN operation is performed.
   * This method will be invoked immediately before processing the
   * modify DN operation in the backend.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @return  Information about the result of the synchronization
   *          provider processing.  Note that if the provider
   *          indicates that processing should end for the operation,
   *          it must set the result code for the operation and should
   *          also set the response message.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract SynchronizationProviderResult
                       doPreOperation(ModifyDNOperation
                                           modifyDNOperation)
         throws DirectoryException;



  /**
   * Performs any necessary synchronization processing that may be
   * needed after the provided modify DN operation is performed.  This
   * method will be invoked immediately after processing the modify DN
   * operation in the backend and releasing the lock on the target
   * entry.
   *
   * @param  modifyDNOperation  The modify DN operation to be
   *                            processed.
   *
   * @throws  DirectoryException  If a problem occurs during
   *                              synchronization processing.
   */
  public abstract void doPostOperation(
                            ModifyDNOperation modifyDNOperation)
         throws DirectoryException;
}

