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
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.*;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.opends.server.types.ResultCode;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.*;
import org.opends.messages.Message;


/**
 * This class defines a very simple plugin that causes request processing to end
 * immediately and send a specific result code to the client.  It will be
 * triggered by a control contained in the client request, and may be invoked
 * during either pre-parse or pre-operation processing.  Short circuits can
 * also be registered for operations regardless of controls.
 */
public class ShortCircuitPlugin
       extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * The OID for the short circuit request control, which is used to flag
   * operations that should cause the operation processing to end immediately.
   */
  public static final String OID_SHORT_CIRCUIT_REQUEST =
       "1.3.6.1.4.1.26027.1.999.3";



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public ShortCircuitPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(Set<PluginType> pluginTypes,
                               PluginCfg configuration)
         throws ConfigException
  {
    // This plugin may only be used as a pre-parse or pre-operation plugin.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
        case PRE_PARSE_ABANDON:
        case PRE_PARSE_ADD:
        case PRE_PARSE_BIND:
        case PRE_PARSE_COMPARE:
        case PRE_PARSE_DELETE:
        case PRE_PARSE_EXTENDED:
        case PRE_PARSE_MODIFY:
        case PRE_PARSE_MODIFY_DN:
        case PRE_PARSE_SEARCH:
        case PRE_PARSE_UNBIND:
        case PRE_OPERATION_ADD:
        case PRE_OPERATION_BIND:
        case PRE_OPERATION_COMPARE:
        case PRE_OPERATION_DELETE:
        case PRE_OPERATION_EXTENDED:
        case PRE_OPERATION_MODIFY:
        case PRE_OPERATION_MODIFY_DN:
        case PRE_OPERATION_SEARCH:
          // This is fine.
          break;
        default:
          throw new ConfigException(Message.raw("Invalid plugin type " + t +
                                    " for the short circuit plugin."));
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
         doPreParse(PreParseAbandonOperation abandonOperation)
  {
    int resultCode = shortCircuitInternal(abandonOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseAddOperation addOperation)
  {
    int resultCode = shortCircuitInternal(addOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseBindOperation bindOperation)
  {
    int resultCode = shortCircuitInternal(bindOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseCompareOperation compareOperation)
  {
    int resultCode = shortCircuitInternal(compareOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseDeleteOperation deleteOperation)
  {
    int resultCode = shortCircuitInternal(deleteOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseExtendedOperation extendedOperation)
  {
    int resultCode = shortCircuitInternal(extendedOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyOperation modifyOperation)
  {
    int resultCode = shortCircuitInternal(modifyOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    int resultCode = shortCircuitInternal(modifyDNOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseSearchOperation searchOperation)
  {
    int resultCode = shortCircuitInternal(searchOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    int resultCode = shortCircuitInternal(unbindOperation, "PreParse");
    if (resultCode >= 0)
    {
      return PluginResult.PreParse.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-parse"));
    }
    else
    {
      return PluginResult.PreParse.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationAddOperation addOperation)
  {
    int resultCode = shortCircuitInternal(addOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    int resultCode = shortCircuitInternal(bindOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationCompareOperation compareOperation)
  {
    int resultCode = shortCircuitInternal(compareOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    int resultCode = shortCircuitInternal(deleteOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationExtendedOperation extendedOperation)
  {
    int resultCode = shortCircuitInternal(extendedOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    int resultCode = shortCircuitInternal(modifyOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    int resultCode = shortCircuitInternal(modifyDNOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationSearchOperation searchOperation)
  {
    int resultCode = shortCircuitInternal(searchOperation, "PreOperation");
    if (resultCode >= 0)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.valueOf(resultCode),
          Message.raw("Short-circuit in pre-operation"));
    }
    else
    {
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * Looks for a short-circuit request control in the operation and if one is
   * found with the correct section then generate the appropriate result.
   *
   * @param  operation  The operation to be processed.
   * @param  section    The section to match in the control value.
   *
   * @return  The result code that should be immediately sent to the client, or
   *          -1 if operation processing should continue as normal.
   */
  private int shortCircuitInternal(PluginOperation operation, String section)
  {
    List<Control> requestControls = operation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_SHORT_CIRCUIT_REQUEST))
        {
          try
          {
            ASN1Sequence sequence =
                 ASN1Sequence.decodeAsSequence(c.getValue().value());
            ArrayList<ASN1Element> elements = sequence.elements();
            int resultCode = elements.get(0).decodeAsEnumerated().intValue();
            String controlSection =
                        elements.get(1).decodeAsOctetString().stringValue();
            if (section.equalsIgnoreCase(controlSection))
            {
              return resultCode;
            }
          }
          catch (Exception e)
          {
            System.err.println("***** ERROR:  Could not decode short circuit " +
                               "control value:  " + e);
            e.printStackTrace();
            return -1;
          }
        }
      }
    }

    // Check for registered short circuits.
    Integer resultCode = shortCircuits.get(
         operation.getOperationType().toString() + "/" + section.toLowerCase());
    if (resultCode != null)
    {
      return resultCode;
    }

    // If we've gotten here, then we shouldn't short-circuit the operation
    // processing.
    return -1;
  }



  /**
   * Creates a short circuit request control with the specified result code and
   * section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  The appropriate short circuit request control.
   */
  public static Control createShortCircuitControl(int resultCode,
                                                  String section)
  {
    ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
    elements.add(new ASN1Enumerated(resultCode));
    elements.add(new ASN1OctetString(section));

    ASN1OctetString controlValue =
         new ASN1OctetString(new ASN1Sequence(elements).encode());

    return new Control(OID_SHORT_CIRCUIT_REQUEST, false, controlValue);
  }



  /**
   * Retrieves a list containing a short circuit control with the specified
   * result code and section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  A list containing the appropriate short circuit request control.
   */
  public static List<Control> createShortCircuitControlList(int resultCode,
                                                            String section)
  {
    ArrayList<Control> controlList = new ArrayList<Control>(1);
    controlList.add(createShortCircuitControl(resultCode, section));
    return controlList;
  }



  /**
   * Creates a short circuit LDAP request control with the specified result code
   * and section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  The appropriate short circuit LDAP request control.
   */
  public static LDAPControl createShortCircuitLDAPControl(int resultCode,
                                                          String section)
  {
    return new LDAPControl(createShortCircuitControl(resultCode, section));
  }



  /**
   * Retrieves a list containing a short circuit LDAP control with the specified
   * result code and section.
   *
   * @param  resultCode  The result code to return to the client.
   * @param  section     The section to use to determine when to short circuit.
   *
   * @return  A list containing the appropriate short circuit LDAP request
   *          control.
   */
  public static ArrayList<LDAPControl> createShortCircuitLDAPControlList(
                                            int resultCode, String section)
  {
    ArrayList<LDAPControl> controlList = new ArrayList<LDAPControl>(1);
    controlList.add(createShortCircuitLDAPControl(resultCode, section));
    return controlList;
  }

  /**
   * Registered short circuits for operations regardless of controls.
   */
  private static Map<String,Integer> shortCircuits =
       new ConcurrentHashMap<String, Integer>();

  /**
   * Register a short circuit for the given operation type and plugin point.
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   * @param resultCode The result code to be returned for the short circuit.
   */
  public static void registerShortCircuit(OperationType operation,
                                          String section, int resultCode)
  {
    shortCircuits.put(operation.toString() + "/" + section.toLowerCase(),
                      resultCode);
  }

  /**
   * Deregister a short circuit for the given operation type and plugin point.
   * @param operation The type of operation the short circuit applies to.
   * @param section The plugin point the short circuit applies to.
   */
  public static void deregisterShortCircuit(OperationType operation,
                                            String section)
  {
    shortCircuits.remove(operation.toString() + "/" + section.toLowerCase());
  }
}

