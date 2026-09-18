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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.plugin;

import static org.opends.server.protocols.internal.InternalClientConnection.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.Control;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;

/**
 * A ModifyMsg whose operation can not be prepared for its replay.
 * <p>
 * The operation is built - so the replay is past the point where a message is given up on -
 * and the list of request controls it carries can not be added to, so the ManageDsaIT
 * control the replay puts on every operation throws before
 * {@code OperationContext.getCSN(op)} is reached: the change fails, stays listed and
 * uncommitted, and is asked for again. Such a message can not travel the protocol:
 * {@code ModifyMsg.createOperation()} builds an operation whose controls can be added to,
 * so this one is handed to the domain rather than published. The twin of the fixture
 * {@code UpdateOperationTest} holds its barrier with.
 */
final class ModifyMsgWhoseOperationRefusesAControl extends ModifyMsg
{
  ModifyMsgWhoseOperationRefusesAControl(
      CSN csn, DN dn, List<Modification> mods, String entryUUID)
  {
    super(csn, dn, mods, entryUUID);
  }

  @Override
  public ModifyOperation createOperation(InternalClientConnection connection, DN newDN)
  {
    final ModifyOperation op = new ModifyOperationBasis(connection, nextOperationID(),
        nextMessageID(), Collections.<Control>emptyList(),
        ByteString.valueOfUtf8(getDN().toString()), new ArrayList<RawModification>());
    op.setAttachment(OperationContext.SYNCHROCONTEXT,
        new ModifyContext(getCSN(), getEntryUUID()));
    return op;
  }
}
