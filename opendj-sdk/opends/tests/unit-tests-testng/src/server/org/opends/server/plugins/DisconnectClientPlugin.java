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
import java.io.IOException;

import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.*;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import static org.opends.server.protocols.asn1.ASN1Constants.UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.loggers.ErrorLogger;
import org.opends.messages.Message;


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
       extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * The OID for the disconnect request control, which is used to flag
   * operations that should cause the client connection to be terminated.
   */
  public static final String OID_DISCONNECT_REQUEST =
       "1.3.6.1.4.1.26027.1.999.2";


  /**
   * The control used by this plugin.
   */
  public static class DisconnectClientControl extends Control
  {
    /**
     * ControlDecoder implentation to decode this control from a ByteString.
     */
    private final static class Decoder
        implements ControlDecoder<DisconnectClientControl>
    {
      /**
       * {@inheritDoc}
       */
      public DisconnectClientControl decode(boolean isCritical,
                                            ByteString value)
          throws DirectoryException
      {
        return new DisconnectClientControl(isCritical, value.toString());
      }

      public String getOID()
      {
        return OID_DISCONNECT_REQUEST;
      }

    }

    /**
     * The Control Decoder that can be used to decode this control.
     */
    public static final ControlDecoder<DisconnectClientControl> DECODER =
      new Decoder();


    private String section;

    /**
     * Constructs a new change number control.
     *
     * @param  isCritical   Indicates whether support for this control should be
     *                      considered a critical part of the server processing.
     * @param section  The section to use for the disconnect.
     */
    public DisconnectClientControl(boolean isCritical, String section)
    {
      super(OID_DISCONNECT_REQUEST, isCritical);
      this.section = section;
    }

    /**
     * Writes this control's value to an ASN.1 writer. The value (if any)
     * must be written as an ASN1OctetString.
     *
     * @param writer The ASN.1 writer to use.
     * @throws java.io.IOException If a problem occurs while writing to the stream.
     */
    @Override
    protected void writeValue(ASN1Writer writer) throws IOException {
      writer.writeOctetString(section);
    }

    /**
     * Retrieves the delay duration.
     *
     * @return The delay duration.
     */
    public String getSection()
    {
      return section;
    }
  }

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
                               PluginCfg configuration)
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
          throw new ConfigException(Message.raw("Invalid plugin type " + t +
                                    " for the disconnect plugin."));
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(
      PreParseAbandonOperation abandonOperation)
  {
    disconnectInternal(abandonOperation, "PreParse");
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseAddOperation addOperation)
      throws CanceledOperationException {
    if (disconnectInternal(addOperation, "PreParse"))
    {
      addOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse doPreParse(PreParseBindOperation bindOperation)
  {
    disconnectInternal(bindOperation, "PreParse");
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseCompareOperation compareOperation)
       throws CanceledOperationException {
    if (disconnectInternal(compareOperation, "PreParse"))
    {
      compareOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseDeleteOperation deleteOperation)
       throws CanceledOperationException {
    if (disconnectInternal(deleteOperation, "PreParse"))
    {
      deleteOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseExtendedOperation extendedOperation)
       throws CanceledOperationException {
    if (disconnectInternal(extendedOperation, "PreParse"))
    {
      extendedOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyOperation modifyOperation)
       throws CanceledOperationException {
    if (disconnectInternal(modifyOperation, "PreParse"))
    {
      modifyOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseModifyDNOperation modifyDNOperation)
       throws CanceledOperationException {
    if (disconnectInternal(modifyDNOperation, "PreParse"))
    {
      modifyDNOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseSearchOperation searchOperation)
       throws CanceledOperationException {
    if (disconnectInternal(searchOperation, "PreParse"))
    {
      searchOperation.checkIfCanceled(false);
    }
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreParse
       doPreParse(PreParseUnbindOperation unbindOperation)
  {
    disconnectInternal(unbindOperation, "PreParse");
    return PluginResult.PreParse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationAddOperation addOperation)
       throws CanceledOperationException {
    if (disconnectInternal(addOperation, "PreOperation"))
    {
      addOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationBindOperation bindOperation)
  {
    disconnectInternal(bindOperation, "PreOperation");
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationCompareOperation compareOperation)
       throws CanceledOperationException {
    if (disconnectInternal(compareOperation, "PreOperation"))
    {
      compareOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationDeleteOperation deleteOperation)
       throws CanceledOperationException {
    if (disconnectInternal(deleteOperation, "PreOperation"))
    {
      deleteOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationExtendedOperation extendedOperation)
       throws CanceledOperationException {
    if (disconnectInternal(extendedOperation, "PreOperation"))
    {
      extendedOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
       throws CanceledOperationException {
    if (disconnectInternal(modifyOperation, "PreOperation"))
    {
      modifyOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
       throws CanceledOperationException {
    if (disconnectInternal(modifyDNOperation, "PreOperation"))
    {
      modifyDNOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationSearchOperation searchOperation)
       throws CanceledOperationException {
    if (disconnectInternal(searchOperation, "PreOperation"))
    {
      searchOperation.checkIfCanceled(false);
    }
    return PluginResult.PreOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationAbandonOperation abandonOperation)
  {
    disconnectInternal(abandonOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationAddOperation addOperation)
  {
    disconnectInternal(addOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationBindOperation bindOperation)
  {
    disconnectInternal(bindOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationCompareOperation compareOperation)
  {
    disconnectInternal(compareOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    disconnectInternal(deleteOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationExtendedOperation extendedOperation)
  {
    disconnectInternal(extendedOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    disconnectInternal(modifyOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    disconnectInternal(modifyDNOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationSearchOperation searchOperation)
  {
    disconnectInternal(searchOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostOperation
       doPostOperation(PostOperationUnbindOperation unbindOperation)
  {
    disconnectInternal(unbindOperation, "PostOperation");
    return PluginResult.PostOperation.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseAddOperation addOperation)
  {
    disconnectInternal(addOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseBindOperation bindOperation)
  {
    disconnectInternal(bindOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseCompareOperation compareOperation)
  {
    disconnectInternal(compareOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseDeleteOperation deleteOperation)
  {
    disconnectInternal(deleteOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseExtendedOperation extendedOperation)
  {
    disconnectInternal(extendedOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseModifyOperation modifyOperation)
  {
    disconnectInternal(modifyOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseModifyDNOperation modifyDNOperation)
  {
    disconnectInternal(modifyDNOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PostResponse
       doPostResponse(PostResponseSearchOperation searchOperation)
  {
    disconnectInternal(searchOperation, "PostResponse");
    return PluginResult.PostResponse.continueOperationProcessing();
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
  private boolean disconnectInternal(PluginOperation operation,
                                     String section)
  {
    try
    {
      DisconnectClientControl control =
          operation.getRequestControl(DisconnectClientControl.DECODER);

      if (control != null && control.getSection().equalsIgnoreCase(section))
      {
        operation.disconnectClient(DisconnectReason.CLOSED_BY_PLUGIN, true,
            Message.raw("Closed by disconnect client plugin (section " +
                section + ")"));

        return true;
      }
    }
    catch (Exception e)
    {
      ErrorLogger.logError(Message.raw("Unable to decode the disconnect client control:  " +
              e));
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
    return new DisconnectClientControl(false, section);
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

    controlList.add(new DisconnectClientControl(false, section));

    return controlList;
  }
}

