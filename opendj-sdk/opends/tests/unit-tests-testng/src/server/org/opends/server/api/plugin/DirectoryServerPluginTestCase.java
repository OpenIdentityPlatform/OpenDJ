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
package org.opends.server.api.plugin;



import java.util.HashSet;

import org.testng.annotations.Test;

import org.opends.server.plugins.NullPlugin;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.operation.*;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the Directory Server plugin API.
 */
public class DirectoryServerPluginTestCase
       extends PluginAPITestCase
{
  /**
   * Tests the <CODE>getPluginEntryDN</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPluginEntryDN()
         throws Exception
  {
    NullPlugin nullPlugin = new NullPlugin();
    DN pluginEntryDN = DN.decode("cn=Null Plugin,cn=Plugins,cn=config");

    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    for (PluginType t : PluginType.values())
    {
      pluginTypes.add(t);
    }

    nullPlugin.initializeInternal(pluginEntryDN, pluginTypes);
    assertEquals(nullPlugin.getPluginEntryDN(), pluginEntryDN);
  }



  /**
   * Tests the <CODE>getPluginTypes</CODE> method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPluginTypes()
         throws Exception
  {
    NullPlugin nullPlugin = new NullPlugin();
    DN pluginEntryDN = DN.decode("cn=Null Plugin,cn=Plugins,cn=config");

    HashSet<PluginType> pluginTypes = new HashSet<PluginType>();
    for (PluginType t : PluginType.values())
    {
      pluginTypes.add(t);
    }

    nullPlugin.initializeInternal(pluginEntryDN, pluginTypes);
    assertEquals(nullPlugin.getPluginTypes(), pluginTypes);
  }



  /**
   * Invokes the default plugin finalizer.
   */
  @Test()
  public void testDefaultFinalizer()
  {
    new NullPlugin().finalizePlugin();
  }



  /**
   * Ensures that the default <CODE>doStartup</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoStartup()
  {
    new NullPlugin().doStartup();
  }



  /**
   * Ensures that the default <CODE>doShutdown</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoShutdown()
  {
    new NullPlugin().doShutdown();
  }



  /**
   * Ensures that the default <CODE>doPostConnect</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostConnect()
  {
    new NullPlugin().doPostConnect(null);
  }



  /**
   * Ensures that the default <CODE>doPostDisconnect</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostDisconnect()
  {
    new NullPlugin().doPostDisconnect(null, DisconnectReason.CLOSED_BY_PLUGIN,
                                      -1, null);
  }



  /**
   * Ensures that the default <CODE>doLDIFImport</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoLDIFImport()
  {
    new NullPlugin().doLDIFImport(null, null);
  }



  /**
   * Ensures that the default <CODE>doLDIFExport</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoLDIFExport()
  {
    new NullPlugin().doLDIFExport(null, null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for abandon operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseAbandon()
  {
    new NullPlugin().doPreParse((PreParseAbandonOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for add operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseAdd()
  {
    new NullPlugin().doPreParse((PreParseAddOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for bind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseBind()
  {
    new NullPlugin().doPreParse((PreParseBindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for compare operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseCompare()
  {
    new NullPlugin().doPreParse((PreParseCompareOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for delete operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseDelete()
  {
    new NullPlugin().doPreParse((PreParseDeleteOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for extended operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseExtended()
  {
    new NullPlugin().doPreParse((PreParseExtendedOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for modify operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseModify()
  {
    new NullPlugin().doPreParse((PreParseModifyOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for modify DN operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseModifyDN()
  {
    new NullPlugin().doPreParse((PreParseModifyDNOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for search operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseSearch()
  {
    new NullPlugin().doPreParse((PreParseSearchOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreParse</CODE> method throws an
   * exception for unbind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreParseUnbind()
  {
    new NullPlugin().doPreParse((PreParseUnbindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for add operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationAdd()
  {
    new NullPlugin().doPreOperation((PreOperationAddOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for bind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationBind()
  {
    new NullPlugin().doPreOperation((PreOperationBindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for compare operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationCompare()
  {
    new NullPlugin().doPreOperation((PreOperationCompareOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for delete operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationDelete()
  {
    new NullPlugin().doPreOperation((PreOperationDeleteOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for extended operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationExtended()
  {
    new NullPlugin().doPreOperation((PreOperationExtendedOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for modify operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationModify()
  {
    new NullPlugin().doPreOperation((PreOperationModifyOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for modify DN operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationModifyDN()
  {
    new NullPlugin().doPreOperation((PreOperationModifyDNOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPreOperation</CODE> method throws an
   * exception for search operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPreOperationSearch()
  {
    new NullPlugin().doPreOperation((PreOperationSearchOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for abandon operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationAbandon()
  {
    new NullPlugin().doPostOperation((PostOperationAbandonOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for add operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationAdd()
  {
    new NullPlugin().doPostOperation((PostOperationAddOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for bind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationBind()
  {
    new NullPlugin().doPostOperation((PostOperationBindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for compare operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationCompare()
  {
    new NullPlugin().doPostOperation((PostOperationCompareOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for delete operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationDelete()
  {
    new NullPlugin().doPostOperation((PostOperationDeleteOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for extended operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationExtended()
  {
    new NullPlugin().doPostOperation((PostOperationExtendedOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for modify operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationModify()
  {
    new NullPlugin().doPostOperation((PostOperationModifyOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for modify DN operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationModifyDN()
  {
    new NullPlugin().doPostOperation((PostOperationModifyDNOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for search operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationSearch()
  {
    new NullPlugin().doPostOperation((PostOperationSearchOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostOperation</CODE> method throws an
   * exception for unbind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostOperationUnbind()
  {
    new NullPlugin().doPostOperation((PostOperationUnbindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for add operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseAdd()
  {
    new NullPlugin().doPostResponse((PostResponseAddOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for bind operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseBind()
  {
    new NullPlugin().doPostResponse((PostResponseBindOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for compare operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseCompare()
  {
    new NullPlugin().doPostResponse((PostResponseCompareOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for delete operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseDelete()
  {
    new NullPlugin().doPostResponse((PostResponseDeleteOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for extended operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseExtended()
  {
    new NullPlugin().doPostResponse((PostResponseExtendedOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for modify operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseModify()
  {
    new NullPlugin().doPostResponse((PostResponseModifyOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for modify DN operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseModifyDN()
  {
    new NullPlugin().doPostResponse((PostResponseModifyDNOperation) null);
  }



  /**
   * Ensures that the default <CODE>doPostResponse</CODE> method throws an
   * exception for search operations.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testDoPostResponseSearch()
  {
    new NullPlugin().doPostResponse((PostResponseSearchOperation) null);
  }



  /**
   * Ensures that the default <CODE>processSearchEntry</CODE> method throws an
   * exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testProcessSearchEntry()
  {
    new NullPlugin().processSearchEntry(null, null);
  }



  /**
   * Ensures that the default <CODE>processSearchReference</CODE> method throws
   * an exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testProcessSearchReference()
  {
    new NullPlugin().processSearchReference(null, null);
  }



  /**
   * Ensures that the default <CODE>processIntermediateResponse</CODE> method
   * throws an exception.
   */
  @Test(expectedExceptions = { UnsupportedOperationException.class })
  public void testProcessIntermediateResponse()
  {
    new NullPlugin().processIntermediateResponse(null);
  }
}

