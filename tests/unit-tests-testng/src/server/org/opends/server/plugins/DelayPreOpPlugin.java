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



import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

import org.opends.server.admin.std.server.PluginCfg;
import org.opends.server.api.plugin.DirectoryServerPlugin;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1;
import static org.opends.server.protocols.asn1.ASN1Constants.UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;
import org.opends.server.controls.ControlDecoder;
import org.opends.messages.Message;


/**
 * This class defines a very simple pre-operation plugin that sleeps for up to
 * five seconds for add, compare, delete, extended, modify, modify DN, and
 * search operations (and therefore not for abandon, bind, and unbind
 * operations, since those operations cannot be cancelled).  While it is
 * sleeping, it also checks for a request to cancel the associated operation and
 * will respond to it accordingly.
 */
public class DelayPreOpPlugin
       extends DirectoryServerPlugin<PluginCfg>
{
  /**
   * The OID for the delay request control, which is used to flag operations
   * that should be delayed.
   */
  public static final String OID_DELAY_REQUEST = "1.3.6.1.4.1.26027.1.999.1";

  /**
   * The control used by this plugin.
   */
  public static class DelayRequestControl extends Control
  {

    /**
     * ControlDecoder implentation to decode this control from a ByteString.
     */
    private final static class Decoder
        implements ControlDecoder<DelayRequestControl>
    {
      /**
       * {@inheritDoc}
       */
      public DelayRequestControl decode(boolean isCritical, ByteString value)
          throws DirectoryException
      {
        ASN1Reader reader = ASN1.getReader(value);

        try
        {
          long delay = reader.readInteger();

          return new DelayRequestControl(isCritical, delay);
        }
        catch (Exception e)
        {
          // TODO: Need a better message
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, null, e);
        }
      }

      public String getOID()
      {
        return OID_DELAY_REQUEST;
      }

    }

    /**
     * The Control Decoder that can be used to decode this control.
     */
    public static final ControlDecoder<DelayRequestControl> DECODER =
      new Decoder();


    private long delayDuration;

    /**
     * Constructs a new change number control.
     *
     * @param  isCritical   Indicates whether support for this control should be
     *                      considered a critical part of the server processing.
     * @param delayDuration The requested delay duration.
     */
    public DelayRequestControl(boolean isCritical, long delayDuration)
    {
      super(OID_DELAY_REQUEST, isCritical);
      this.delayDuration = delayDuration;
    }

    /**
     * Writes this control's value to an ASN.1 writer. The value (if any)
     * must be written as an ASN1OctetString.
     *
     * @param writer The ASN.1 writer to use.
     * @throws IOException If a problem occurs while writing to the stream.
     */
    @Override
    protected void writeValue(ASN1Writer writer) throws IOException {
      writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);
      writer.writeInteger(delayDuration);
      writer.writeEndSequence();
    }

    /**
     * Retrieves the delay duration.
     *
     * @return The delay duration.
     */
    public long getDelayDuration()
    {
      return delayDuration;
    }
  }



  /**
   * Creates a new instance of this Directory Server plugin.  Every
   * plugin must implement a default constructor (it is the only one
   * that will be used to create plugins defined in the
   * configuration), and every plugin constructor must call
   * <CODE>super()</CODE> as its first element.
   */
  public DelayPreOpPlugin()
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
    // This plugin may only be used as a pre-operation plugin.
    for (PluginType t : pluginTypes)
    {
      switch (t)
      {
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
                                    " for delay pre-op plugin."));
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationAddOperation addOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(addOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
         doPreOperation(PreOperationBindOperation bindOperation)
  {
    try
    {
      return doPreOperationInternal(bindOperation);
    }
    catch(CanceledOperationException coe)
    {
      // Bind ops can't be canceled. Just ignore.
      return PluginResult.PreOperation.continueOperationProcessing();
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
         doPreOperation(PreOperationCompareOperation compareOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(compareOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationDeleteOperation deleteOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(deleteOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationExtendedOperation extendedOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(extendedOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyOperation modifyOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(modifyOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationModifyDNOperation modifyDNOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(modifyDNOperation);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public PluginResult.PreOperation
       doPreOperation(PreOperationSearchOperation searchOperation)
      throws CanceledOperationException {
    return doPreOperationInternal(searchOperation);
  }



  /**
   * Looks for a delay request control in the operation, and if one is found
   * then sleep in 10 millisecond increments up to the length of time specified
   * in the control value.  If the operation receives a cancel request during
   * this time, then the control will stop sleeping immediately.
   *
   * @param  operation  The operation to be processed.
   *
   * @return  The result of the plugin processing.
   */
  private PluginResult.PreOperation
       doPreOperationInternal(PreOperationOperation operation)
      throws CanceledOperationException
  {
    DelayRequestControl control;
    try
    {
      control = operation.getRequestControl(DelayRequestControl.DECODER);
    }
    catch (Exception e)
    {
      return PluginResult.PreOperation.stopProcessing(
          ResultCode.PROTOCOL_ERROR,
          Message.raw("Unable to decode the delay request control:  " +
              e));
    }

    if(control != null)
    {
      long delayDuration = control.getDelayDuration();
      if (delayDuration <= 0)
      {
        return PluginResult.PreOperation.continueOperationProcessing();
      }

      long stopSleepTime = System.currentTimeMillis() + delayDuration;
      while (System.currentTimeMillis() < stopSleepTime)
      {
        operation.checkIfCanceled(false);

        try
        {
          Thread.sleep(10);
        } catch (Exception e) {}
      }
    }

    return PluginResult.PreOperation.continueOperationProcessing();
  }




  /**
   * Creates a delay request control with the specified delay.
   *
   * @param  delay  The length of time in milliseconds to sleep.
   *
   * @return  The appropriate delay request control.
   */
  public static Control createDelayControl(long delay)
  {
    return new DelayRequestControl(false, delay);
  }



  /**
   * Retrieves a list containing a delay request LDAP control with the specified
   * delay.
   *
   * @param  delay  The length of time in milliseconds to sleep.
   *
   * @return  A list containing the appropriate delay request LDAP control.
   */
  public static List<Control> createDelayControlList(long delay)
  {
    ArrayList<Control> controlList = new ArrayList<Control>(1);

    controlList.add(new DelayRequestControl(false, delay));

    return controlList;
  }
}

