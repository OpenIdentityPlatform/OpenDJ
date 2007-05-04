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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.api.plugin;



import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import org.opends.server.plugins.NullPlugin;
import org.opends.server.types.DisconnectReason;
import org.opends.server.types.DN;
import org.opends.server.types.operation.*;
import org.opends.server.TestCaseUtils;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the Directory Server plugin API.
 */
public class DirectoryServerPluginTestCase
       extends PluginAPITestCase
{
  @BeforeClass
  public void initServer() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that no new abstract methods have been introduced which
   * could impact backwards compatibility.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAbstractMethods()
         throws Exception
  {
    LinkedList<LinkedList<String>> expectedAbstractMethods =
      new LinkedList<LinkedList<String>>();

    LinkedList<String> sigList = new LinkedList<String>();
    sigList.add("initializePlugin");
    sigList.add("void");
    sigList.add("java.util.Set");
    sigList.add("org.opends.server.admin.std.server.PluginCfg");
    sigList.add("org.opends.server.config.ConfigException");
    sigList.add("org.opends.server.types.InitializationException");
    expectedAbstractMethods.add(sigList);


    LinkedList<LinkedList<String>> newAbstractMethods =
         new LinkedList<LinkedList<String>>();
    Class pluginClass = DirectoryServerPlugin.class;
    for (Method m : pluginClass.getMethods())
    {
      if (Modifier.isAbstract(m.getModifiers()))
      {
        LinkedList<String> foundList = new LinkedList<String>();
        foundList.add(m.getName());
        foundList.add(m.getReturnType().getName());
        for (Class c : m.getParameterTypes())
        {
          foundList.add(c.getName());
        }
        for (Class c : m.getExceptionTypes())
        {
          foundList.add(c.getName());
        }

        Iterator<LinkedList<String>> iterator =
             expectedAbstractMethods.iterator();
        boolean found = false;
        while (iterator.hasNext())
        {
          sigList = iterator.next();
          if (foundList.equals(sigList))
          {
            found = true;
            iterator.remove();
            break;
          }
        }

        if (! found)
        {
          newAbstractMethods.add(foundList);
        }
      }
    }

    if (! newAbstractMethods.isEmpty())
    {
      System.err.println("It appears that one or more new abstract methods " +
                         "have been added to the plugin API:");
      for (LinkedList<String> methodList : newAbstractMethods)
      {
        for (String s : methodList)
        {
          System.err.print(s + " ");
        }
        System.err.println();
      }

      System.err.println();
    }

    if (! expectedAbstractMethods.isEmpty())
    {
      System.err.println("It appears that one or more abstract methods have " +
                         "been removed from the plugin API:");
      for (LinkedList<String> methodList : expectedAbstractMethods)
      {
        for (String s : methodList)
        {
          System.err.print(s + " ");
        }
        System.err.println();
      }

      System.err.println();
    }

    if ((! newAbstractMethods.isEmpty()) ||
        (! expectedAbstractMethods.isEmpty()))
    {
      fail("It appears that set of abstract methods defined in the plugin " +
           "API have been altered.  This will only be allowed under " +
           "extremely limited circumstances, as it will impact backward " +
           "compatibility.");
    }
  }



  /**
   * Tests to ensure that none of the non-abstract public methods exposed by the
   * plugin interface as part of the public API have been removed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testNonAbstractPublicAPIMethods()
         throws Exception
  {
    LinkedList<LinkedList<String>> expectedPublicMethods =
      new LinkedList<LinkedList<String>>();

    LinkedList<String> sigList = new LinkedList<String>();
    sigList.add("finalizePlugin");
    sigList.add("void");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doStartup");
    sigList.add("org.opends.server.api.plugin.StartupPluginResult");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doShutdown");
    sigList.add("void");
    sigList.add("java.lang.String");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostConnect");
    sigList.add("org.opends.server.api.plugin.PostConnectPluginResult");
    sigList.add("org.opends.server.api.ClientConnection");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostDisconnect");
    sigList.add("org.opends.server.api.plugin.PostDisconnectPluginResult");
    sigList.add("org.opends.server.api.ClientConnection");
    sigList.add("org.opends.server.types.DisconnectReason");
    sigList.add("int");
    sigList.add("java.lang.String");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doLDIFImport");
    sigList.add("org.opends.server.api.plugin.LDIFPluginResult");
    sigList.add("org.opends.server.types.LDIFImportConfig");
    sigList.add("org.opends.server.types.Entry");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doLDIFExport");
    sigList.add("org.opends.server.api.plugin.LDIFPluginResult");
    sigList.add("org.opends.server.types.LDIFExportConfig");
    sigList.add("org.opends.server.types.Entry");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseAbandonOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseModifyOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseAddOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseBindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseCompareOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseDeleteOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseExtendedOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseUnbindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseModifyDNOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreParse");
    sigList.add("org.opends.server.api.plugin.PreParsePluginResult");
    sigList.add("org.opends.server.types.operation.PreParseSearchOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PreOperationExtendedOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PreOperationDeleteOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation.PreOperationBindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PreOperationSearchOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation.PreOperationAddOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation."+
                "PreOperationCompareOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PreOperationModifyOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPreOperation");
    sigList.add("org.opends.server.api.plugin.PreOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PreOperationModifyDNOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationCompareOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationModifyDNOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationExtendedOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationBindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationAbandonOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationUnbindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationModifyOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation.PostOperationAddOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationDeleteOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostOperation");
    sigList.add("org.opends.server.api.plugin.PostOperationPluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostOperationSearchOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseCompareOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseDeleteOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseSearchOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseExtendedOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseModifyOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "PostResponseModifyDNOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation.PostResponseAddOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("doPostResponse");
    sigList.add("org.opends.server.api.plugin.PostResponsePluginResult");
    sigList.add("org.opends.server.types.operation.PostResponseBindOperation");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("processSearchEntry");
    sigList.add("org.opends.server.api.plugin.SearchEntryPluginResult");
    sigList.add("org.opends.server.types.operation.SearchEntrySearchOperation");
    sigList.add("org.opends.server.types.SearchResultEntry");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("processSearchReference");
    sigList.add("org.opends.server.api.plugin.SearchReferencePluginResult");
    sigList.add("org.opends.server.types.operation." +
                "SearchReferenceSearchOperation");
    sigList.add("org.opends.server.types.SearchResultReference");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("processIntermediateResponse");
    sigList.add("org.opends.server.api.plugin." +
                "IntermediateResponsePluginResult");
    sigList.add("org.opends.server.types.IntermediateResponse");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("initializeInternal");
    sigList.add("void");
    sigList.add("org.opends.server.types.DN");
    sigList.add("java.util.Set");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("getPluginEntryDN");
    sigList.add("org.opends.server.types.DN");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("getPluginTypes");
    sigList.add("java.util.Set");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("getClass");
    sigList.add("java.lang.Class");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("equals");
    sigList.add("boolean");
    sigList.add("java.lang.Object");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("hashCode");
    sigList.add("int");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("toString");
    sigList.add("java.lang.String");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("wait");
    sigList.add("void");
    sigList.add("java.lang.InterruptedException");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("wait");
    sigList.add("void");
    sigList.add("long");
    sigList.add("java.lang.InterruptedException");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("wait");
    sigList.add("void");
    sigList.add("long");
    sigList.add("int");
    sigList.add("java.lang.InterruptedException");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("notify");
    sigList.add("void");
    expectedPublicMethods.add(sigList);

    sigList = new LinkedList<String>();
    sigList.add("notifyAll");
    sigList.add("void");
    expectedPublicMethods.add(sigList);


    LinkedList<LinkedList<String>> newPublicMethods =
         new LinkedList<LinkedList<String>>();
    Class pluginClass = DirectoryServerPlugin.class;
    for (Method m : pluginClass.getMethods())
    {
      if (m.getName().startsWith("ajc$"))
      {
        // This is a method added by AspectJ weaving.  We can ignore it.
        continue;
      }

      if (Modifier.isPublic(m.getModifiers()) &&
          (! Modifier.isAbstract(m.getModifiers())))
      {
        LinkedList<String> foundList = new LinkedList<String>();
        foundList.add(m.getName());
        foundList.add(m.getReturnType().getName());
        for (Class c : m.getParameterTypes())
        {
          foundList.add(c.getName());
        }
        for (Class c : m.getExceptionTypes())
        {
          foundList.add(c.getName());
        }

        Iterator<LinkedList<String>> iterator =
             expectedPublicMethods.iterator();
        boolean found = false;
        while (iterator.hasNext())
        {
          sigList = iterator.next();
          if (foundList.equals(sigList))
          {
            found = true;
            iterator.remove();
            break;
          }
        }

        if (! found)
        {
          newPublicMethods.add(foundList);
        }
      }
    }

    if (! expectedPublicMethods.isEmpty())
    {
      System.err.println("It appears that one or more public methods have " +
                         "been removed from the plugin API:");
      for (LinkedList<String> methodList : expectedPublicMethods)
      {
        for (String s : methodList)
        {
          System.err.print(s + " ");
        }
        System.err.println();
      }

      System.err.println();

      fail("It appears that set of methods defined in the plugin API has " +
           "been altered in a manner that could impact backward " +
           "compatibility.  This will only be allowed under extremely " +
           "limited circumstances.");
    }


    if (! newPublicMethods.isEmpty())
    {
      System.err.println("It appears that one or more new public methods " +
                         "have been added to the plugin API:");
      for (LinkedList<String> methodList : newPublicMethods)
      {
        for (String s : methodList)
        {
          System.err.print(s + " ");
        }
        System.err.println();
      }

      System.err.println();

      fail("It appears that one or more new public methods have been added " +
           "to the plugin API.  This is not actually an error, but if you " +
           "intend to make the new method(s) part of the official plugin API " +
           "then you must add its signature to the expectedPublicMethods " +
           "list above so that we can ensure that it is not removed or " +
           "altered in an incompatible way in the future.");
    }
  }



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
    new NullPlugin().doShutdown("testDoShutdown");
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

