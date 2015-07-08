/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.*;
import java.util.zip.DataFormatException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ServerStatus;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.util.Utils;

/**
 * This message is used by DS to confirm a RS he wants to connect to him (open
 * a session):
 * Handshake sequence between DS and RS is like this:
 * DS --- ServerStartMsg ---> RS
 * DS <--- ReplServerStartMsg --- RS
 * DS --- StartSessionMsg ---> RS
 * DS <--- TopologyMsg --- RS
 *
 * This message contains:
 * - status: the status we are entering the topology with
 * - referrals URLs: the referrals URLs we allow peer DSs to use to refer to
 * our domain when needed.
 */
public class StartSessionMsg extends ReplicationMsg
{
  /** The list of referrals URLs to the sending DS. */
  private final List<String> referralsURLs = new ArrayList<>();
  /** The initial status the DS starts with. */
  private ServerStatus status = ServerStatus.INVALID_STATUS;
  /** Assured replication enabled on DS or not. */
  private boolean assuredFlag;
  /** DS assured mode (relevant if assured replication enabled). */
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  /** DS safe data level (relevant if assured mode is safe data). */
  private byte safeDataLevel = 1;

  private Set<String> eclIncludes = new HashSet<>();
  private Set<String> eclIncludesForDeletes = new HashSet<>();

  /**
   * Creates a new StartSessionMsg message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @param version The protocol version to use to decode the msg.
   * @throws java.util.zip.DataFormatException If the byte array does not
   * contain a valid encoded form of the message.
   */
  StartSessionMsg(byte[] in, short version) throws DataFormatException
  {
    if (version <= ProtocolVersion.REPLICATION_PROTOCOL_V3)
    {
      decode_V23(in);
    }
    else
    {
      decode_V45(in, version);
    }
  }

  /**
   * Creates a new  message with the given required parameters.
   * @param status Status we are starting with
   * @param referralsURLs Referrals URLs to be used by peer DSs
   * @param assuredFlag If assured mode is enabled or not
   * @param assuredMode Assured type
   * @param safeDataLevel Assured mode safe data level
   */
  public StartSessionMsg(ServerStatus status, Collection<String> referralsURLs,
    boolean assuredFlag, AssuredMode assuredMode, byte safeDataLevel)
  {
    this.referralsURLs.addAll(referralsURLs);
    this.status = status;
    this.assuredFlag = assuredFlag;
    this.assuredMode = assuredMode;
    this.safeDataLevel = safeDataLevel;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion <= ProtocolVersion.REPLICATION_PROTOCOL_V3)
    {
      return getBytes_V23();
    }
    else
    {
      return getBytes_V45(protocolVersion);
    }
  }

  private byte[] getBytes_V45(short version)
  {
    try
    {
      ByteStringBuilder byteBuilder = new ByteStringBuilder();
      ASN1Writer writer = ASN1.getWriter(byteBuilder);

      byteBuilder.append(MSG_TYPE_START_SESSION);
      byteBuilder.append(status.getValue());
      byteBuilder.append(assuredFlag ? (byte) 1 : (byte) 0);
      byteBuilder.append(assuredMode.getValue());
      byteBuilder.append(safeDataLevel);

      writer.writeStartSequence();
      for (String url : referralsURLs)
      {
        writer.writeOctetString(url);
      }
      writer.writeEndSequence();

      writer.writeStartSequence();
      for (String attrDef : eclIncludes)
      {
        writer.writeOctetString(attrDef);
      }
      writer.writeEndSequence();

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
      {
        writer.writeStartSequence();
        for (String attrDef : eclIncludesForDeletes)
        {
          writer.writeOctetString(attrDef);
        }
        writer.writeEndSequence();
      }

      return byteBuilder.toByteArray();
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  private byte[] getBytes_V23()
  {
    /*
     * The message is stored in the form:
     * <message type><status><assured flag><assured mode><safe data level>
     * <list of referrals urls>
     * (each referral url terminates with 0)
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_START_SESSION);
    builder.appendByte(status.getValue());
    builder.appendBoolean(assuredFlag);
    builder.appendByte(assuredMode.getValue());
    builder.appendByte(safeDataLevel);

    if (referralsURLs.size() >= 1)
    {
      for (String url : referralsURLs)
      {
        builder.appendString(url);
      }
    }
    return builder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  private void decode_V45(byte[] in, short version) throws DataFormatException
  {
    ByteSequenceReader reader = ByteString.wrap(in).asReader();
    try
    {
      if (reader.get() != MSG_TYPE_START_SESSION)
      {
        throw new DataFormatException("input is not a valid "
            + getClass().getCanonicalName());
      }

      /*
      status = ServerStatus.valueOf(asn1Reader.readOctetString().byteAt(0));
      assuredFlag = (asn1Reader.readOctetString().byteAt(0) == 1);
      assuredMode=AssuredMode.valueOf((asn1Reader.readOctetString().byteAt(0)));
      safeDataLevel = asn1Reader.readOctetString().byteAt(0);
      */
      status = ServerStatus.valueOf(reader.get());
      assuredFlag = (reader.get() == 1);
      assuredMode = AssuredMode.valueOf(reader.get());
      safeDataLevel = reader.get();

      ASN1Reader asn1Reader = ASN1.getReader(reader);

      asn1Reader.readStartSequence();
      while(asn1Reader.hasNextElement())
      {
        String s = asn1Reader.readOctetStringAsString();
        this.referralsURLs.add(s);
      }
      asn1Reader.readEndSequence();

      asn1Reader.readStartSequence();
      while(asn1Reader.hasNextElement())
      {
        String s = asn1Reader.readOctetStringAsString();
        this.eclIncludes.add(s);
      }
      asn1Reader.readEndSequence();

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V5)
      {
        asn1Reader.readStartSequence();
        while (asn1Reader.hasNextElement())
        {
          this.eclIncludesForDeletes.add(asn1Reader.readOctetStringAsString());
        }
        asn1Reader.readEndSequence();
      }
      else
      {
        // Default to using the same set of attributes for deletes.
        this.eclIncludesForDeletes.addAll(eclIncludes);
      }
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  private void decode_V23(byte[] in) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <message type><status><assured flag><assured mode><safe data level>
     * <list of referrals urls>
     * (each referral url terminates with 0)
     */
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_START_SESSION)
    {
      throw new DataFormatException(
          "Input is not a valid " + getClass().getCanonicalName());
    }

    status = ServerStatus.valueOf(scanner.nextByte());
    assuredFlag = scanner.nextBoolean();
    assuredMode = AssuredMode.valueOf(scanner.nextByte());
    safeDataLevel = scanner.nextByte();

    while (!scanner.isEmpty())
    {
      referralsURLs.add(scanner.nextString());
    }
  }

  /**
   * Get the list of referrals URLs.
   *
   * @return The list of referrals URLs.
   */
  public List<String> getReferralsURLs()
  {
    return referralsURLs;
  }

  /**
   * Get the status from this message.
   * @return The status.
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    String urls = Utils.joinAsString(" | ", referralsURLs);
    return "StartSessionMsg content:\nstatus: " + status +
      "\nassuredFlag: " + assuredFlag +
      "\nassuredMode: " + assuredMode +
      "\nsafeDataLevel: " + safeDataLevel +
      "\nreferralsURLs: " + urls +
      "\nEclIncludes " + eclIncludes +
      "\nEclIncludeForDeletes: " + eclIncludesForDeletes;
  }

  /**
   * Returns true if assured mode is enabled.
   * @return true if assured mode is enabled.
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Get the assured mode.
   * @return the assured mode.
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Get the safe data level.
   * @return the safe data level.
   */
  public byte getSafeDataLevel()
  {
    return safeDataLevel;
  }

  /**
   * Set the attributes configured on a server to be included in the ECL.
   *
   * @param includeAttributes
   *          attributes to be included with all change records.
   * @param includeAttributesForDeletes
   *          additional attributes to be included with delete change records.
   */
  public void setEclIncludes(
      Set<String> includeAttributes,
      Set<String> includeAttributesForDeletes)
  {
    if (includeAttributes != null)
    {
      eclIncludes = includeAttributes;
    }

    if (includeAttributesForDeletes != null)
    {
      eclIncludesForDeletes = includeAttributesForDeletes;
    }
  }

  /**
   * Get the attributes to include in each change for the ECL.
   *
   * @return The attributes to include in each change for the ECL.
   */
  public Set<String> getEclIncludes()
  {
    return eclIncludes;
  }



  /**
   * Get the attributes to include in each delete change for the ECL.
   *
   * @return The attributes to include in each delete change for the ECL.
   */
  public Set<String> getEclIncludesForDeletes()
  {
    return eclIncludesForDeletes;
  }

}
