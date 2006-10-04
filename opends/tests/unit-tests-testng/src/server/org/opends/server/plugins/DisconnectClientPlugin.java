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

import org.opends.server.api.ClientConnection;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PostOperationPluginResult;
import org.opends.server.api.plugin.PostResponsePluginResult;
import org.opends.server.api.plugin.PreOperationPluginResult;
import org.opends.server.api.plugin.PreParsePluginResult;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Control;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.operation.*;



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
  public void initializePlugin(Set<PluginType> pluginTypes,
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
  public PreParsePluginResult
       doPreParse(PreParseAbandonOperation abandonOperation)
  {
    if (disconnectInternal(abandonOperation.getClientConnection(),
                           abandonOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult doPreParse(PreParseAddOperation addOperation)
  {
    if (disconnectInternal(addOperation.getClientConnection(),
                           addOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult doPreParse(PreParseBindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation.getClientConnection(),
                           bindOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseCompareOperation compareOperation)
  {
    if (disconnectInternal(compareOperation.getClientConnection(),
                           compareOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseDeleteOperation deleteOperation)
  {
    if (disconnectInternal(deleteOperation.getClientConnection(),
                           deleteOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseExtendedOperation extendedOperation)
  {
    if (disconnectInternal(extendedOperation.getClientConnection(),
                           extendedOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseModifyOperation modifyOperation)
  {
    if (disconnectInternal(modifyOperation.getClientConnection(),
                           modifyOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation.getClientConnection(),
                           modifyDNOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseSearchOperation searchOperation)
  {
    if (disconnectInternal(searchOperation.getClientConnection(),
                           searchOperation.getRequestControls(), "PreParse"))
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
  public PreParsePluginResult
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    if (disconnectInternal(unbindOperation.getClientConnection(),
                           unbindOperation.getRequestControls(), "PreParse"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationAddOperation addOperation)
  {
    if (disconnectInternal(addOperation.getClientConnection(),
                           addOperation.getRequestControls(), "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation.getClientConnection(),
                           bindOperation.getRequestControls(), "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationCompareOperation compareOperation)
  {
    if (disconnectInternal(compareOperation.getClientConnection(),
                           compareOperation.getRequestControls(),
                           "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationDeleteOperation deleteOperation)
  {
    if (disconnectInternal(deleteOperation.getClientConnection(),
                           deleteOperation.getRequestControls(),
                           "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationExtendedOperation extendedOperation)
  {
    if (disconnectInternal(extendedOperation.getClientConnection(),
                           extendedOperation.getRequestControls(),
                           "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    if (disconnectInternal(modifyOperation.getClientConnection(),
                           modifyOperation.getRequestControls(),
                           "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation.getClientConnection(),
                           modifyDNOperation.getRequestControls(),
                           "PreOperation"))
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
  public PreOperationPluginResult
       doPreOperation(PreOperationSearchOperation searchOperation)
  {
    if (disconnectInternal(searchOperation.getClientConnection(),
                           searchOperation.getRequestControls(),
                           "PreOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationAbandonOperation abandonOperation)
  {
    if (disconnectInternal(abandonOperation.getClientConnection(),
                           abandonOperation.getRequestControls(),
                           "PreOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationAddOperation addOperation)
  {
    if (disconnectInternal(addOperation.getClientConnection(),
                           addOperation.getRequestControls(), "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationBindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation.getClientConnection(),
                           bindOperation.getRequestControls(), "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationCompareOperation compareOperation)
  {
    if (disconnectInternal(compareOperation.getClientConnection(),
                           compareOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    if (disconnectInternal(deleteOperation.getClientConnection(),
                           deleteOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationExtendedOperation extendedOperation)
  {
    if (disconnectInternal(extendedOperation.getClientConnection(),
                           extendedOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    if (disconnectInternal(modifyOperation.getClientConnection(),
                           modifyOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation.getClientConnection(),
                           modifyDNOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationSearchOperation searchOperation)
  {
    if (disconnectInternal(searchOperation.getClientConnection(),
                           searchOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostOperationPluginResult
       doPostOperation(PostOperationUnbindOperation unbindOperation)
  {
    if (disconnectInternal(unbindOperation.getClientConnection(),
                           unbindOperation.getRequestControls(),
                           "PostOperation"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseAddOperation addOperation)
  {
    if (disconnectInternal(addOperation.getClientConnection(),
                           addOperation.getRequestControls(), "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseBindOperation bindOperation)
  {
    if (disconnectInternal(bindOperation.getClientConnection(),
                           bindOperation.getRequestControls(), "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseCompareOperation compareOperation)
  {
    if (disconnectInternal(compareOperation.getClientConnection(),
                           compareOperation.getRequestControls(),
                           "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseDeleteOperation deleteOperation)
  {
    if (disconnectInternal(deleteOperation.getClientConnection(),
                           deleteOperation.getRequestControls(),
                           "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseExtendedOperation extendedOperation)
  {
    if (disconnectInternal(extendedOperation.getClientConnection(),
                           extendedOperation.getRequestControls(),
                           "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseModifyOperation modifyOperation)
  {
    if (disconnectInternal(modifyOperation.getClientConnection(),
                           modifyOperation.getRequestControls(),
                           "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseModifyDNOperation modifyDNOperation)
  {
    if (disconnectInternal(modifyDNOperation.getClientConnection(),
                           modifyDNOperation.getRequestControls(),
                           "PostResponse"))
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
  public PostResponsePluginResult
       doPostResponse(PostResponseSearchOperation searchOperation)
  {
    if (disconnectInternal(searchOperation.getClientConnection(),
                           searchOperation.getRequestControls(),
                           "PostResponse"))
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
  private boolean disconnectInternal(ClientConnection clientConnection,
                                     List<Control> requestControls,
                                     String section)
  {
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (c.getOID().equals(OID_DISCONNECT_REQUEST))
        {
          if (c.getValue().stringValue().equalsIgnoreCase(section))
          {
            clientConnection.disconnect(DisconnectReason.CLOSED_BY_PLUGIN, true,
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

