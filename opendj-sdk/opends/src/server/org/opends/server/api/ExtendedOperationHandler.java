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



import java.util.HashSet;
import java.util.Set;

import org.opends.server.config.ConfigException;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.InitializationException;
import org.opends.server.admin.std.server.ExtendedOperationHandlerCfg;


/**
 * This class defines the set of methods and structures that must be
 * implemented by a Directory Server module that implements the
 * functionality required for one or more types of extended
 * operations.
 *
 * @param <T> The configuration class that will be provided to
 *            initialize the handler.
 */
public abstract class
     ExtendedOperationHandler<T extends ExtendedOperationHandlerCfg>
{


  /**
   * The default set of supported control OIDs for this extended
   * operation.
   */
  protected Set<String> supportedControlOIDs = new HashSet<String>(0);

  /**
   * The default set of supported feature OIDs for this extended
   * operation.
   */
  protected Set<String> supportedFeatureOIDs = new HashSet<String>(0);



  /**
   * Initializes this extended operation handler based on the
   * information in the provided configuration entry.  It should also
   * register itself with the Directory Server for the particular
   * kinds of extended operations that it will process.
   *
   * @param  config  The extended operation handler configuration that
   *                 contains the information to use to initialize
   *                 this extended operation handler.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs
   *                                   during initialization that is
   *                                   not related to the server
   *                                   configuration.
   */
  public abstract void
       initializeExtendedOperationHandler(T config)
         throws ConfigException, InitializationException;



  /**
   * Performs any finalization that may be necessary for this extended
   * operation handler.  By default, no finalization is performed.
   */
  public void finalizeExtendedOperationHandler()
  {
    // No implementation is required by default.
  }



  /**
   * Processes the provided extended operation.
   *
   * @param  operation  The extended operation to be processed.
   */
  public abstract void processExtendedOperation(ExtendedOperation
                                                     operation);



  /**
   * Retrieves the OIDs of the controls that may be supported by this
   * extended operation handler.  It should be overridden by any
   * extended operation handler which provides special support for one
   * or more controls.
   *
   * @return  The OIDs of the controls that may be supported by this
   *          extended operation handler.
   */
  public Set<String> getSupportedControls()
  {
    return supportedControlOIDs;
  }



  /**
   * Indicates whether this extended operation handler supports the
   * specified control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  {@code true} if this extended operation handler does
   *          support the requested control, or {@code false} if not.
   */
  public final boolean supportsControl(String controlOID)
  {
    return getSupportedControls().contains(controlOID);
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this
   * extended operation handler.
   *
   * @return  The OIDs of the features that may be supported by this
   *          extended operation handler.
   */
  public Set<String> getSupportedFeatures()
  {
    return supportedFeatureOIDs;
  }



  /**
   * Indicates whether this extended operation handler supports the
   * specified feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  {@code true} if this extended operation handler does
   *          support the requested feature, or {@code false} if not.
   */
  public final boolean supportsFeature(String featureOID)
  {
    return getSupportedFeatures().contains(featureOID);
  }

  /**
   * If the extended operation handler defines any supported controls
   * and/or features, then register them with the server.
   *
   */
  protected void registerControlsAndFeatures()
  {
    Set<String> controlOIDs = getSupportedControls();
    if (controlOIDs != null)
    {
      for (String oid : controlOIDs)
      {
        DirectoryServer.registerSupportedControl(oid);
      }
    }

    Set<String> featureOIDs = getSupportedFeatures();
    if (featureOIDs != null)
    {
      for (String oid : featureOIDs)
      {
        DirectoryServer.registerSupportedFeature(oid);
      }
    }
  }


  /**
   * If the extended operation handler defines any supported controls
   * and/or features, then deregister them with the server.
   */
  protected void deregisterControlsAndFeatures()
  {
    Set<String> controlOIDs = getSupportedControls();
    if (controlOIDs != null)
    {
      for (String oid : controlOIDs)
      {
        DirectoryServer.deregisterSupportedControl(oid);
      }
    }

    Set<String> featureOIDs = getSupportedFeatures();
    if (featureOIDs != null)
    {
      for (String oid : featureOIDs)
      {
        DirectoryServer.deregisterSupportedFeature(oid);
      }
    }
  }

}

