/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/proctor/resource/legal-notices/Proctor.LICENSE
 * or https://proctor.dev.java.net/Proctor.LICENSE.
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
 *      Portions Copyright 2009 Sun Microsystems, Inc.
 */

package com.ibm.staf.service.opends.tester;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.xml.bind.JAXBElement;
import java.util.Collection;
import java.util.Collections;
import org.opends.dsml.protocol.DsmlAttr;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

public class Checker {

    private static final String JAXB_GENERATED_PACKAGE_NAME = "org.opends.dsml.protocol";

    // Key=JAXB classes, Value=List of methods to get attribute
    private static final Map<Class, List<Method>> CACHE = new HashMap<Class, List<Method>>();

    // Helpfull to keep track of what argument is currently processed
    private static final Stack<Method> STACK = new Stack<Method>();

    // A list of attribute that are prevented to be checked
    private static final Set<String> DO_NOT_CHECK = new HashSet<String>();

    static {
        DO_NOT_CHECK.add("ResultCode.getDescr"); // ResultCode.getCode is enough
        DO_NOT_CHECK.add("LDAPResult.getErrorMessage"); // LDAPResult.getResultCode is enough
        DO_NOT_CHECK.add("ErrorResponse.getMessage");
    }
    private static DsmlAttrCompare dsmlAttrCompare = new DsmlAttrCompare();

    static {
      // needed otherwise the SearchResultEntry like in test moddn998.res fail
      // in the equals() method on DN.decode((String) o1....
      DirectoryServer.bootstrapClient();
    }

    private static List<Method> getAttributes(Class clazz) {
        List<Method> result = CACHE.get(clazz);
        if (result == null) {
            result = new ArrayList<Method>();

            for (Method method : clazz.getMethods()) {
                String methodName = method.getName();
                Class returnType = method.getReturnType();
                if (!method.getDeclaringClass().getName().startsWith(JAXB_GENERATED_PACKAGE_NAME)) {
                    continue;
                }
                if (((methodName.startsWith("get") && (methodName.length() > 3) && (!returnType.equals(void.class))) || (methodName.startsWith("is") && (methodName.length() > 2) && (returnType.equals(boolean.class)))) && (method.getParameterTypes().length == 0)) {
                    result.add(method);
                }
            }
            CACHE.put(clazz, result);
        }
        return result;
    }

    private static String buildMessage() {

        // return "";

        StringBuilder sb = new StringBuilder(STACK.peek().toGenericString()).append(" in JAXB ");
        int size = STACK.size();
        for (int i = 0; i < size; i++) {
            sb.append(i == 0 ? "" : ".").append(STACK.get(i).getName()).append("()");
        }
        return sb.toString();

    }

    public static boolean equals(Object o1, Object o2) {
        if (o1 == null && o2 == null) {
            return true;
        }
        if (o1 == null  || o2 == null) {
            throw new JAXBCheckerException("One of the two is null : " + buildMessage());
        }
        // both are not null
        Class c1 = o1.getClass();
        if (!o2.getClass().equals(c1)) {
            throw new JAXBCheckerException("Type mismatch (" + c1.getName() + " vs " + o2.getClass().getName() + ") : " + buildMessage());
        }

        if (c1.getName().startsWith(JAXB_GENERATED_PACKAGE_NAME)) {
            for (Method method : getAttributes(c1)) {
                String fullName = method.getDeclaringClass().getName() + "." + method.getName();
                String s = fullName.substring(JAXB_GENERATED_PACKAGE_NAME.length() + 1, fullName.length());
                if (DO_NOT_CHECK.contains(s)) {
                    continue;
                }
                Object r1, r2;
                try {
                    STACK.push(method);
                    r1 = method.invoke(o1);
                    r2 = method.invoke(o2);
                    if (!equals(r1, r2)) {
                        // if false an exception will be thown
                        STACK.pop();
                        return false;
                    }
                    STACK.pop();
                } catch (Exception e) {
                    throw (RuntimeException) e;
                }
            }
        } else if (o1 instanceof List) {
            // Transering into sets whenever ordering is required
            Collection l1;
            Collection l2;

            l1 = (List) o1;
            l2 = (List) o2;

            if (l1 != null &&
              l1.size() > 1 &&
              l1.toArray()[0] instanceof DsmlAttr) {
                Collections.sort((List) l1, dsmlAttrCompare);
                Collections.sort((List) l2, dsmlAttrCompare);
            } else if (l1 != null &&
              l1.size() > 1 &&
              l1.toArray()[0] instanceof String) {
                Collections.sort((List) l1);
                Collections.sort((List) l2);
            }

            /*
            if (((List) o2).size() != l2.size()) {
                for (Object o : l2) {
                    System.out.println("collection2 : " + o);
                }
                for (Object o : (List) o2) {
                    System.out.println("list2 : " + o);
                }
                throw (new RuntimeException("object2 size changed"));
            }
*/
            if (l1.size() != l2.size()) {

                for (Object o : l1) {
                    System.out.println("list1 : " + o);
                }
                for (Object o : l2) {
                    System.out.println("list2 : " + o);
                }
                throw new JAXBCheckerException("List size mismatch (received=" + l1.size() + ", expected=" + l2.size() + "): " + buildMessage());
            }

            for (int i = 0; i < l1.size(); i++) {
                // could be optimized :(
                if (!equals(l1.toArray()[i], l2.toArray()[i])) {
                    // if false an exception will be thown
                    return false;
                }
            }
        } else if (o1 instanceof JAXBElement) {
            return equals(((JAXBElement) o1).getValue(), ((JAXBElement) o2).getValue());
        } else if (o1 instanceof String) {
            // special case in case a DN must be checked
            // This is not a full check for DN but DN.decode(String) needs a DS to run
            if (STACK.size() > 0) {
                String s = STACK.peek().getName();
                if (s.equalsIgnoreCase("getDn") ||
                  s.equalsIgnoreCase("getMatchedDN")) {
                    try {
                        if (!DN.decode((String) o1).equals(DN.decode((String) o2))) {
                            throw new JAXBCheckerException("DN mismatch : " + o1 + "  vs " + o2 + " " + buildMessage());
                        }
                    } catch (DirectoryException ex) {
                        throw new JAXBCheckerException("DN mismatch : " + o1 + "  vs " + o2 + " " + buildMessage());
                    }
//                    String oo1 = ((String) o1).replace(" ", "");
//                    String oo2 = ((String) o2).replace(" ", "");
//                    if (!oo1.equalsIgnoreCase(oo2)) {
//                        throw new JAXBCheckerException("DN mismatch : " + o1 + "  vs " + o2 + " " + buildMessage());
//                    }
                } else if (s.equalsIgnoreCase("getName")) {
                    if (!((String) o1).equalsIgnoreCase((String) o2)) {
                        throw new JAXBCheckerException("Attribute mismatch : " + o1 + "  vs " + o2 + " " + buildMessage());
                    }
                } else {
                    if (!o1.equals(o2)) {
                        throw new JAXBCheckerException("String mismatch : " + o1 + "  vs " + o2 + " " + buildMessage());
                    }
                }
            }
        } else {
            if (!o1.equals(o2)) {
                if (STACK.size() > 0) {
                    String s = STACK.peek().getName();
                    if (s.equalsIgnoreCase("getCode")) {
                        throw new JAXBCheckerException("Error code mismatch : " + LDAPResultCode.toString((Integer) o1) + "[" + o1 + "] vs " + LDAPResultCode.toString((Integer) o2) + "[" + o2 + "]" + buildMessage());
                    } else {
                        throw new JAXBCheckerException("Object mismatch : [class = " + o1.getClass().getSimpleName() + "]" + buildMessage());
                    }
                }
            }
        }

        return true;
    }
} 
