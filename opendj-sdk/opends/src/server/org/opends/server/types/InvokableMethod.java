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
package org.opends.server.types;



import javax.management.Attribute;
import javax.management.MBeanException;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

import org.opends.server.api.InvokableComponent;
import org.opends.server.config.ConfigAttribute;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;



/**
 * This class defines a data structure that holds information about a
 * method that may be invoked for an invokable component.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public class InvokableMethod
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // Indicates whether this method retrieves information about the
  // associated component.
  private boolean retrievesComponentInfo;

  // Indicates whether this method updates information about the
  // associated component.
  private boolean updatesComponentInfo;

  // The set of arguments for this method.
  private ConfigAttribute[] arguments;

  // The description for this method.
  private String description;

  // The name for this method.
  private String name;

  // The return type for this method.
  private String returnType;



  /**
   * Creates a new invokable method with the provided information.
   *
   * @param  name                    The name for this invokable
   *                                 method.
   * @param  description             The description for this
   *                                 invokable method.
   * @param  arguments               The object types for this
   *                                 method's arguments.
   * @param  returnType              The object type for this method's
   *                                 return value.
   * @param  retrievesComponentInfo  Indicates whether this method
   *                                 retrieves information about the
   *                                 associated component.
   * @param  updatesComponentInfo    Indicates whether this method
   *                                 updates information about the
   *                                 associated component.
   */
  public InvokableMethod(String name, String description,
                         ConfigAttribute[] arguments,
                         String returnType,
                         boolean retrievesComponentInfo,
                         boolean updatesComponentInfo)
  {
    this.name                     = name;
    this.description              = description;
    this.returnType               = returnType;
    this.retrievesComponentInfo   = retrievesComponentInfo;
    this.updatesComponentInfo     = updatesComponentInfo;

    if (arguments == null)
    {
      this.arguments = new ConfigAttribute[0];
    }
    else
    {
      this.arguments = arguments;
    }
  }



  /**
   * Retrieves the name of this invokable method.
   *
   * @return  The name of this invokable method.
   */
  public String getName()
  {
    return name;
  }



  /**
   * Retrieves a description of this invokable method.
   *
   * @return  A description of this invokable method.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Retrieves the set of arguments for this invokable method.
   *
   * @return  The set of arguments for this invokable method.
   */
  public ConfigAttribute[] getArguments()
  {
    return arguments;
  }



  /**
   * Retrieves the return type for this invokable method.
   *
   * @return  The return type for this invokable method.
   */
  public String getReturnType()
  {
    return returnType;
  }



  /**
   * Indicates whether this method retrieves information about the
   * associated component.
   *
   * @return  <CODE>true</CODE> if this method retrieves information
   *          about the associated component, or <CODE>false</CODE> if
   *          it does not.
   */
  public boolean retrievesComponentInfo()
  {
    return retrievesComponentInfo;
  }



  /**
   * Indicates whether this method updates information about the
   * associated component.
   *
   * @return  <CODE>true</CODE> if this method updates information
   *          about the associated component, or <CODE>false</CODE> if
   *          it does not.
   */
  public boolean updatesComponentInfo()
  {
    return updatesComponentInfo;
  }



  /**
   * Retrieves an <CODE>MBeanOperationInfo</CODE> object that
   * encapsulates the information in this invokable method.
   *
   * @return  An <CODE>MBeanOperationInfo</CODE> object that
   *          encapsulates the information in this invokable method.
   */
  public MBeanOperationInfo toOperationInfo()
  {
    MBeanParameterInfo[] signature =
         new MBeanParameterInfo[arguments.length];
    for (int i=0; i < arguments.length; i++)
    {
      signature[i] = arguments[i].toJMXParameterInfo();
    }


    int impact;
    if (retrievesComponentInfo)
    {
      if (updatesComponentInfo)
      {
        impact = MBeanOperationInfo.ACTION_INFO;
      }
      else
      {
        impact = MBeanOperationInfo.INFO;
      }
    }
    else if (updatesComponentInfo)
    {
      impact = MBeanOperationInfo.ACTION;
    }
    else
    {
      impact = MBeanOperationInfo.UNKNOWN;
    }


    return new MBeanOperationInfo(name, description, signature,
                                  returnType, impact);

  }



  /**
   * Indicates whether this invokable method has the provided
   * signature.
   *
   * @param  methodName     The method name to use in the
   *                        determination.
   * @param  argumentTypes  The argument object types to use in the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if this invokable method has the
   *          provided signature, or <CODE>false</CODE> if not.
   */
  public boolean hasSignature(String methodName,
                              String[] argumentTypes)
  {
    if (! methodName.equals(name))
    {
      return false;
    }

    if (argumentTypes.length != arguments.length)
    {
      return false;
    }

    for (int i=0; i < arguments.length; i++)
    {
      MBeanParameterInfo paramInfo =
           arguments[i].toJMXParameterInfo();
      if (! argumentTypes[i].equals(paramInfo.getType()))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Calls upon the provided component to invoke this method using the
   * given parameters.
   *
   * @param  component      The component to use to invoke this
   *                        method.
   * @param  parameters     The set of method arguments to use when
   *                        invoking this method.
   *
   * @return  The return value resulting from invoking the method, or
   *          <CODE>null</CODE> if it did not return a value.
   *
   * @throws  MBeanException  If a problem occurred while invoking the
   *                          method.
   */
  public Object invoke(InvokableComponent component,
                       Object[] parameters)
         throws MBeanException
  {
    try
    {
      ConfigAttribute[] methodArguments =
           new ConfigAttribute[arguments.length];
      for (int i=0; i < arguments.length; i++)
      {
        Attribute jmxAttr = new Attribute(arguments[i].getName(),
                                          parameters[i]);

        methodArguments[i] = arguments[i].duplicate();
        methodArguments[i].setValue(jmxAttr);
      }

      return component.invokeMethod(name, methodArguments);
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      throw new MBeanException(de, de.getMessage());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      throw new MBeanException(e);
    }
  }



  /**
   * Retrieves a string representation of this invokable method.  It
   * will be in the form of a method signature, like "returnType
   * name(arguments)".
   *
   * @return  a string representation of this invokable method.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();

    if (returnType == null)
    {
      buffer.append("void ");
    }
    else
    {
      buffer.append(returnType);
    }

    buffer.append(name);
    buffer.append('(');

    if ((arguments != null) && (arguments.length > 0))
    {
      buffer.append(arguments[0].getDataType());
      buffer.append(' ');
      buffer.append(arguments[0].getName());

      for (int i=1; i < arguments.length; i++)
      {
        buffer.append(", ");
        buffer.append(arguments[i].getDataType());
        buffer.append(' ');
        buffer.append(arguments[i].getName());
      }
    }

    buffer.append(')');

    return buffer.toString();
  }
}

