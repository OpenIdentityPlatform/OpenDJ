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
package org.opends.server.types.operation;



import org.opends.server.types.ByteString;


/**
 * This class defines a set of methods that are available for use by
 * post-operation plugins for extended operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PostOperationExtendedOperation
       extends PostOperationOperation
{
  /**
   * Retrieves the OID for the request associated with this extended
   * operation.
   *
   * @return  The OID for the request associated with this extended
   *          operation.
   */
  public String getRequestOID();



  /**
   * Retrieves the value for the request associated with this extended
   * operation.
   *
   * @return  The value for the request associated with this extended
   *          operation.
   */
  public ByteString getRequestValue();



  /**
   * Retrieves the OID to include in the response to the client.
   *
   * @return  The OID to include in the response to the client.
   */
  public String getResponseOID();



  /**
   * Specifies the OID to include in the response to the client.
   *
   * @param  responseOID  The OID to include in the response to the
   *                      client.
   */
  public void setResponseOID(String responseOID);



  /**
   * Retrieves the value to include in the response to the client.
   *
   * @return  The value to include in the response to the client.
   */
  public ByteString getResponseValue();



  /**
   * Specifies the value to include in the response to the client.
   *
   * @param  responseValue  The value to include in the response to
   *                        the client.
   */
  public void setResponseValue(ByteString responseValue);
}

