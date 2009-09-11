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

import java.util.Comparator;
import org.opends.dsml.protocol.DsmlAttr;

public class DsmlAttrCompare implements Comparator {

    public int compare(Object o1, Object o2) {
        DsmlAttr attr1 = (DsmlAttr) o1;
        DsmlAttr attr2 = (DsmlAttr) o2;
        return (attr1.getName().compareTo(attr2.getName()));
    }
}
