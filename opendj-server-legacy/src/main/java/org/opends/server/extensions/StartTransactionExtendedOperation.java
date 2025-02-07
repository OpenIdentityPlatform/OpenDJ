/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2025 3A Systems, LLC
 */
package org.opends.server.extensions;

import com.forgerock.opendj.ldap.extensions.StartTransactionExtendedRequest;
import com.forgerock.opendj.ldap.extensions.StartTransactionExtendedResult;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.StartTransactionExtendedOperationHandlerCfg;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.types.InitializationException;

public class StartTransactionExtendedOperation extends ExtendedOperationHandler<StartTransactionExtendedOperationHandlerCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  public StartTransactionExtendedOperation()
  {
    super();
  }

  @Override
  public void initializeExtendedOperationHandler(StartTransactionExtendedOperationHandlerCfg config) throws ConfigException, InitializationException
  {
    super.initializeExtendedOperationHandler(config);
  }

  @Override
  public void processExtendedOperation(ExtendedOperation operation)
  {
    final StartTransactionExtendedResult res=StartTransactionExtendedResult
            .newResult(ResultCode.SUCCESS)
            .setTransactionID(operation.getClientConnection().startTransaction().getTransactionId());

    operation.setResponseOID(res.getOID());
    operation.setResponseValue(res.getValue());
    operation.setResultCode(res.getResultCode());
  }

  @Override
  public String getExtendedOperationOID()
  {
    return StartTransactionExtendedRequest.START_TRANSACTION_REQUEST_OID;
  }

  @Override
  public String getExtendedOperationName()
  {
    return "Start Transaction";
  }
}
