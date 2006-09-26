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
package org.opends.server.plugins;



import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PostResponsePluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AbandonOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.BindOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.Operation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.UnbindOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;



/**
 * This class defines a very simple plugin that terminates the client connection
 * at any point in the plugin processing if the client request contains an
 * appropriate control with a value string that matches the plugin type.  The
 * valid sections for this plugin include:
 * <BR>
 * <UL>
 *   <LI>PreParse -- available for all types of operations</LI>
 *   <LI>PreOperation -- available for all types of operations except abandon
 *       and unbind</LI>
 *   <LI>PostOperation -- available for all types of operations</LI>
 *   <LI>PostResponse -- available for all types of operations except abandon
 *       and unbind</LI>
 * </UL>
 */
public class DisconnectClientPlugin
       extends DirectoryServerPlugin
{
  /**
   * The OID for the disconnect request control, which is used to flag
   * operations that should cause the client connection to be terminated.
   */
  public static final String OID_DISCONNECT_REQUEST =
       "1.3.6.1.4.1.26027.1.999.2";



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public DisconnectClientPlugin()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePlugin(DirectoryServer directoryServer,
                               Set<PluginType> pluginTypes,
                               ConfigEntry configEntry)
         throws ConfigException
  {
    // This plugin may only be used as a pre-parse, pre-operation,
    // post-operation, or post-response plugin.
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
        case POST_OPERATION_ABANDON:
        case POST_OPERATION_ADD:
        case POST_OPERATION_BIND:
        case POST_OPERATION_COMPARE:
        case POST_OPERATION_DELETE:
        case POST_OPERATION_EXTENDED:
        case POST_OPERATION_MODIFY:
        case POST_OPERATION_MODIFY_DN:
        case POST_OPERATION_SEARCH:
        case POST_OPERATION_UNBIND:
        case POST_RESPONSE_ADD:
        case POST_RESPONSE_BIND:
        case POST_RESPONSE_COMPARE:
        case POST_RESPONSE_DELETE:
        case POST_RESPONSE_EXTENDED:
        case POST_RESPONSE_MODIFY:
        case POST_RESPONSE_MODIFY_DN:
        case POST_RESPONSE_SEARCH:
          // This is fine.
          break;
        default:
          throw new ConfigException(-1, "Invalid plugin type " + t +
                                    " for the disconnect plugin.");
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(AbandonOperation abandonOperation)
  {
    if (disconnectInternal(abandonOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(AddOperation addOperation)
  {
    if (disconnectInternal(addOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(BindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(CompareOperation compareOperation)
  {
    if (disconnectInternal(compareOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(DeleteOperation deleteOperation)
  {
    if (disconnectInternal(deleteOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(ExtendedOperation extendedOperation)
  {
    if (disconnectInternal(extendedOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(ModifyOperation modifyOperation)
  {
    if (disconnectInternal(modifyOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(ModifyDNOperation modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(SearchOperation searchOperation)
  {
    if (disconnectInternal(searchOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreParsePluginResult doPreParse(UnbindOperation unbindOperation)
  {
    if (disconnectInternal(unbindOperation, "PreParse"))
    {
      return new PreParsePluginResult(true, false, false);
    }
    else
    {
      return new PreParsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(AddOperation addOperation)
  {
    if (disconnectInternal(addOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(BindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(CompareOperation
                                                      compareOperation)
  {
    if (disconnectInternal(compareOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(DeleteOperation
                                                      deleteOperation)
  {
    if (disconnectInternal(deleteOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(ExtendedOperation
                                                      extendedOperation)
  {
    if (disconnectInternal(extendedOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(ModifyOperation
                                                      modifyOperation)
  {
    if (disconnectInternal(modifyOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(ModifyDNOperation
                                                      modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PreOperationPluginResult doPreOperation(SearchOperation
                                                      searchOperation)
  {
    if (disconnectInternal(searchOperation, "PreOperation"))
    {
      return new PreOperationPluginResult(true, false, false);
    }
    else
    {
      return new PreOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(AbandonOperation
                                                        abandonOperation)
  {
    if (disconnectInternal(abandonOperation, "PreOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(AddOperation addOperation)
  {
    if (disconnectInternal(addOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(BindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(CompareOperation
                                                        compareOperation)
  {
    if (disconnectInternal(compareOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(DeleteOperation
                                                        deleteOperation)
  {
    if (disconnectInternal(deleteOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(ExtendedOperation
                                                        extendedOperation)
  {
    if (disconnectInternal(extendedOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(ModifyOperation
                                                        modifyOperation)
  {
    if (disconnectInternal(modifyOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(ModifyDNOperation
                                                        modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(SearchOperation
                                                        searchOperation)
  {
    if (disconnectInternal(searchOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostOperationPluginResult doPostOperation(UnbindOperation
                                                        unbindOperation)
  {
    if (disconnectInternal(unbindOperation, "PostOperation"))
    {
      return new PostOperationPluginResult(true, false);
    }
    else
    {
      return new PostOperationPluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(AddOperation addOperation)
  {
    if (disconnectInternal(addOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(BindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(CompareOperation
                                                        compareOperation)
  {
    if (disconnectInternal(compareOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(DeleteOperation
                                                        deleteOperation)
  {
    if (disconnectInternal(deleteOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(ExtendedOperation
                                                        extendedOperation)
  {
    if (disconnectInternal(extendedOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(ModifyOperation
                                                        modifyOperation)
  {
    if (disconnectInternal(modifyOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(ModifyDNOperation
                                                        modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PostResponsePluginResult doPostResponse(SearchOperation
                                                        searchOperation)
  {
    if (disconnectInternal(searchOperation, "PostResponse"))
    {
      return new PostResponsePluginResult(true, false);
    }
    else
    {
      return new PostResponsePluginResult();
    }
  }



  /**
   * Looks for a disconnect request control in the operation and if one is found
   * with the correct section then terminate the client connection.
   *
   * @param  operation  The operation to be processed.
   * @param  section    The section to match in the control value.
   *
   * @return  <CODE>true</CODE> if the client connection was terminated, or
   *          <CODE>false</CODE> if it was not.
   */
  private boolean disconnectInternal(Operation operation, String section)
  {
    List<Control> requestControls = operation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_DISCONNECT_REQUEST))
        {
          if (c.getValue().stringValue().equalsIgnoreCase(section))
          {
            operation.getClientConnection().disconnect(
                 DisconnectReason.CLOSED_BY_PLUGIN, true,
                 "Closed by disconnect client plugin (section " + section + ")",
                 -1);

            return true;
          }
        }
      }
    }


    // If we've gotten here, then we shouldn't disconnect the client.
    return false;
  }



  /**
   * Creates a disconnect request control with the specified section.
   *
   * @param  section  The section to use for the disconnect.
   *
   * @return  The appropriate disconnect request control.
   */
  public static Control createDisconnectControl(String section)
  {
    return new Control(OID_DISCONNECT_REQUEST, false,
                       new ASN1OctetString(section));
  }



  /**
   * Retrieves a list containing a disconnect control with the specified
   * section.
   *
   * @param  section  The section to use for the disconnect.
   *
   * @return  A list containing the appropriate disconnect request control.
   */
  public static List<Control> createDisconnectControlList(String section)
  {
    ArrayList<Control> controlList = new ArrayList<Control>(1);

    controlList.add(new Control(OID_DISCONNECT_REQUEST, false,
                                new ASN1OctetString(section)));

    return controlList;
  }



  /**
   * Creates a disconnect request LDAP control with the specified section.
   *
   * @param  section  The section to use for the disconnect.
   *
   * @return  The appropriate disconnect request LDAP control.
   */
  public static LDAPControl createDisconnectLDAPControl(String section)
  {
    return new LDAPControl(OID_DISCONNECT_REQUEST, false,
                           new ASN1OctetString(section));
  }



  /**
   * Retrieves a list containing a disconnect request LDAP control with the
   * specified section.
   *
   * @param  section  The section to use for the disconnect.
   *
   * @return  A list containing the appropriate disconnect request LDAP control.
   */
  public static ArrayList<LDAPControl> createDisconnectLDAPControlList(
                                            String section)
  {
    ArrayList<LDAPControl> controlList = new ArrayList<LDAPControl>(1);

    controlList.add(new LDAPControl(OID_DISCONNECT_REQUEST, false,
                                    new ASN1OctetString(section)));

    return controlList;
  }
}

