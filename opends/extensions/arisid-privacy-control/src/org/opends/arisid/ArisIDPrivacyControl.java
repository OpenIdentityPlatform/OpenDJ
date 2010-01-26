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
 *      Copyright 2010 Sun Microsystems, Inc.
 */
package org.opends.arisid;



import static org.opends.server.protocols.asn1.ASN1Constants.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.opends.messages.Message;
import org.opends.server.controls.ControlDecoder;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.*;
import org.openliberty.arisid.*;
import org.openliberty.arisid.log.ILogger;
import org.openliberty.arisid.log.LogHandler;
import org.openliberty.arisid.policy.IPolicy;
import org.openliberty.arisid.policy.PolicyHandler;
import org.openliberty.arisid.protocol.ldap.IPrivacyControl;
import org.w3c.dom.Element;



/**
 * IGF ArisID Privacy Control implementation.
 */
public class ArisIDPrivacyControl extends Control implements
    IPrivacyControl
{

  /**
   * ControlDecoder implentation to decode this control from a
   * ByteString.
   */
  private final static class Decoder implements
      ControlDecoder<ArisIDPrivacyControl>
  {
    /**
     * Decodes and constructs a Java object representing the
     * encodedValue.
     * <p>
     * ASN.1 encoded value as per Privacy Control Specifiction:
     * http://www.openliberty.org/wiki/index.php/Profile_LDAP#
     * Extended_PolicySequence_Variation
     */
    public ArisIDPrivacyControl decode(boolean isCritical,
        ByteString value) throws DirectoryException
    {
      if (value == null)
      {
        final Message message = Message
            .raw("Control contains no value");
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      String ixnName = "";
      String appName = "";
      String appUri = "";
      final HashMap<String, IPolicy> polMap = new HashMap<String, IPolicy>();
      final ASN1Reader reader = ASN1.getReader(value);

      try
      {
        reader.readStartSequence();
        {
          appName = reader.readOctetStringAsString();
          appUri = reader.readOctetStringAsString();
          ixnName = reader.readOctetStringAsString();

          reader.readStartSequence();
          {
            // Get the count of policies coming
            final long policyCount = reader.readInteger();
            if (policyCount > 1)
            {
              for (int i = 0; i < policyCount; i++)
              {
                reader.readStartSequence();
                {
                  final String pname = reader.readOctetStringAsString();
                  final String pStr = reader.readOctetStringAsString();
                  final Element node = phandler
                      .parseStringToElement(pStr);
                  IPolicy pol = null;
                  try
                  {
                    pol = phandler.parseDomPolicy(node);
                  }
                  catch (final Exception e)
                  {
                    logger.error("Error parsing policy: "
                        + e.getMessage(), e);
                  }
                  polMap.put(pname, pol);
                }
                reader.readEndSequence();
              }
            }
          }
          reader.readEndSequence();
        }
        reader.readEndSequence();
      }
      catch (final Exception e1)
      {
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
            Message.raw("Unable to decode privacy control: "
                + e1.getMessage()), e1);
      }

      return new ArisIDPrivacyControl(isCritical, ixnName, appName,
          appUri, polMap);
    }



    /**
     * {@inheritDoc}
     */
    public String getOID()
    {
      return IPrivacyControl.OID_IGF_CONTROL;
    }
  }



  private static final long serialVersionUID = -2668010326604049964L;

  private static final ILogger logger = LogHandler
      .getLogger(ArisIDPrivacyControl.class);

  private static PolicyHandler phandler = PolicyHandler.getInstance();

  private String _ixnName = "";

  private String _appName = "";

  private String _appUri = "";

  private ArisIdService _asvc = null;

  private CarmlDoc _doc = null;

  private HashMap<String, IPolicy> _polMap = new HashMap<String, IPolicy>();

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ArisIDPrivacyControl> DECODER = new Decoder();



  /**
   * Creates a new non-critical IGF Privacy Control using the provided
   * Interaction.
   *
   * @param ixn
   *          The interaction.
   * @throws IGFException
   *           If an error occurred processing the Interaction.
   */
  public ArisIDPrivacyControl(IInteraction ixn) throws IGFException
  {
    super(IPrivacyControl.OID_IGF_CONTROL, false);
    _processCarmlDoc(ixn);
  }



  /**
   * Creates a new IGF Privacy Control using the provided Interaction
   * and criticality.
   *
   * @param ixn
   *          The interaction.
   * @param critical
   *          The criticality.
   * @throws IGFException
   *           If an error occurred processing the Interaction.
   */
  public ArisIDPrivacyControl(IInteraction ixn, boolean critical)
      throws IGFException
  {
    super(IPrivacyControl.OID_IGF_CONTROL, critical);
    _processCarmlDoc(ixn);
  }



  private ArisIDPrivacyControl(boolean isCritical, String _ixnName,
      String _appName, String _appUri, HashMap<String, IPolicy> _polMap)
  {
    super(IPrivacyControl.OID_IGF_CONTROL, isCritical);
    this._ixnName = _ixnName;
    this._appName = _appName;
    this._appUri = _appUri;
    this._polMap = _polMap;
  }



  public String getAppName()
  {
    return this._appName;
  }



  /**
   * @deprecated Use {@link #getCarmlURI()} instead
   */
  @Deprecated
  public URI getAppURI()
  {
    return getCarmlURI();
  }



  /**
   * A convenience method to obtain the CarmlDoc object referenced by
   * this control. For performance reasons, the CarmlDoc object is not
   * instantiated unless {@link #loadCarmlDoc(URI)} is called first.
   *
   * @return A CarmlDoc object containing the Controls referenced
   *         CarmlDoc or null if the document hasn't been loaded.
   */
  public CarmlDoc getCarmlDoc()
  {

    return this._doc;
  }



  public URI getCarmlURI()
  {
    try
    {
      return new URI(this._appUri);
    }
    catch (final URISyntaxException e)
    {
      logger
          .warn("Invalid CARML URI syntax exception occurred for value: "
              + this._appUri);
      return null;
    }
  }



  /*
   * (non-Javadoc)
   * @see
   * org.openliberty.arisid.protocol.ldap.IPrivacyControl#getConstraintMap
   * ()
   */
  public Map<String, IPolicy> getConstraintMap()
  {
    return this._polMap;
  }



  /*
   * (non-Javadoc)
   * @seeorg.openliberty.arisid.protocol.ldap.IPrivacyControl#
   * getDynamicConstraints (java.lang.String)
   */
  public IPolicy getDynamicConstraints(String nameId)
  {
    return this._polMap.get(nameId);
  }



  /**
   * {@inheritDoc}
   */
  public byte[] getEncodedValue()
  {
    try
    {
      final ByteStringBuilder builder = new ByteStringBuilder();
      final ASN1Writer writer = ASN1.getWriter(builder);
      writeValue(writer);
      writer.close();
      return builder.toByteArray();
    }
    catch (final IOException e)
    {
      // Should never occur.
      throw new RuntimeException(e);
    }
  }



  /*
   * (non-Javadoc)
   * @see org.openliberty.arisid.protocol.ldap.IPrivacyControl#getID()
   */
  public String getID()
  {
    return OID_IGF_CONTROL;
  }



  /*
   * (non-Javadoc)
   * @see
   * org.openliberty.arisid.protocol.ldap.IPrivacyControl#getInteractionName
   * ()
   */
  public String getInteractionName()
  {
    return this._ixnName;
  }



  /**
   * Returns the named transaction constraint.
   *
   * @param nameId
   *          The transaction constraint name.
   * @return The named transaction constraint.
   */
  public IPolicy getTransactionConstraints(String nameId)
  {
    return this._polMap.get(nameId);
  }



  /**
   * This is a convenience method intended for servers/proxies that need
   * to instantiate a CarmlDoc object. The localUri is the URI of a
   * local copy of the controls referenced Carml Document.
   *
   * @param localUri
   *          A URI to a copy of the CARML document matching the
   *          {@link #getAppName()} of this control. If localUri is
   *          null, the method will use the stored URI to load the
   *          document.
   * @throws URISyntaxException
   * @throws IllegalAccessException
   * @throws IGFException
   * @throws InstantiationException
   * @throws AttrSvcInitializedException
   * @throws FileNotFoundException
   */
  public void loadCarmlDoc(URI localUri) throws URISyntaxException,
      FileNotFoundException, AttrSvcInitializedException,
      InstantiationException, IGFException, IllegalAccessException
  {
    URI loadUri = localUri;
    if (loadUri == null)
    {
      loadUri = new URI(this._appUri);
    }

    // TODO I wonder if this should come from a static hash to avoid
    // re-parsing?
    this._asvc = ArisIdServiceFactory.parseCarmlOnly(loadUri);
    if (this._asvc != null)
    {
      this._doc = this._asvc.getCarmlDoc();
    }
  }



  /*
   * (non-Javadoc)
   * @seeorg.openliberty.arisid.protocol.ldap.IPrivacyControl#
   * setDynamicConstraints (java.util.Map)
   */
  public void setDynamicConstraints(
      Map<String, IPolicy> dynamicConstraints)
  {
    if (dynamicConstraints != null)
    {
      this._polMap.putAll(dynamicConstraints);
    }
  }



  /*
   * (non-Javadoc)
   * @seeorg.openliberty.arisid.protocol.ldap.IPrivacyControl#
   * setTranasactionConstraints(java.lang.String,
   * org.openliberty.arisid.schema.IPolicy)
   */
  public void setDynamicConstraints(String nameId,
      IPolicy txnConstraints)
  {
    this._polMap.put(nameId, txnConstraints);
  }



  private void _processCarmlDoc(IInteraction ixn) throws IGFException
  {
    // set defaults of empty string
    this._appName = "";
    this._ixnName = "";
    this._appUri = "";

    if (ixn == null)
    {
      return;
    }

    final ArisIdService asvc = ixn.getAttributeService();
    if (asvc == null)
    {
      return;
    }

    final CarmlDoc doc = asvc.getCarmlDoc();
    this._appName = doc.getApplicationNameId();
    if (this._appName == null)
    {
      this._appName = "";
    }
    this._appUri = doc.getCarmlURI().toString();
    if (this._appUri == null)
    {
      this._appUri = "";
    }
    this._ixnName = ixn.getNameId();
    if (this._ixnName == null)
    {
      this._ixnName = "";
    }

  }



  /**
   * Encode ASN.1 value. Encoding is as per spec:
   * http://www.openliberty.org/wiki
   * /index.php/Profile_LDAP#Extended_PolicySequence_Variation
   */
  protected void writeValue(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);
    {
      writer.writeOctetString(_appName);
      writer.writeOctetString(_appUri);
      writer.writeOctetString(_ixnName);
      writer.writeStartSequence();
      {
        writer.writeInteger(_polMap.size());
        for (final Map.Entry<String, IPolicy> entry : _polMap
            .entrySet())
        {
          writer.writeStartSequence();
          {
            final String nameId = entry.getKey();
            final IPolicy pol = this.getTransactionConstraints(nameId);
            final String strPol = phandler.policyToString(pol);

            writer.writeOctetString(entry.getKey());
            writer.writeOctetString(strPol);
          }
          writer.writeEndSequence();
        }
      }
      writer.writeEndSequence();
    }
    writer.writeEndSequence();
  }

}
