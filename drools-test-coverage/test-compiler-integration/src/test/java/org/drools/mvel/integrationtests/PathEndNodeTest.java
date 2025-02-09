/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.mvel.integrationtests;

import java.util.Collection;

import org.drools.core.base.ClassObjectType;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.rule.EntryPointId;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class PathEndNodeTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public PathEndNodeTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void testSubNetworkSharing() throws Exception {
        String str =
                "package org.test \n" +
                "rule xxx \n" +
                "when \n" +
                "  $s : String()\n" +
                "  not( Integer() and Long() )\n" +
                "then \n" +
                "end  \n" +
                "rule yyy \n" +
                "when \n" +
                "  $s : String()\n" +
                "  not( Integer() and Long() )\n" +
                "then \n" +
                "end  \n";

        InternalKnowledgeBase kbase = (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        EntryPointNode epn = kbase.getRete().getEntryPointNode( EntryPointId.DEFAULT );
        ObjectTypeNode otn = epn.getObjectTypeNodes().get( new ClassObjectType( Long.class) );
        BetaNode beta1 = (BetaNode) otn.getObjectSinkPropagator().getSinks()[0];
        RightInputAdapterNode rian = (RightInputAdapterNode) beta1.getSinkPropagator().getSinks()[0];
        BetaNode beta2 = (BetaNode) rian.getObjectSinkPropagator().getSinks()[0];
        LeftTupleSink[] sinks = beta2.getSinkPropagator().getSinks();
        RuleTerminalNode rtn1 = (RuleTerminalNode) sinks[0];
        RuleTerminalNode rtn2 = (RuleTerminalNode) sinks[1];

        assertEquals(3, rian.getPathEndNodes().length);
        assertTrue( asList(rian.getPathEndNodes()).containsAll( asList(rtn1, rtn2, rian) ) );

        kbase.removeRule( "org.test", "xxx" );

        assertEquals(2, rian.getPathEndNodes().length);
        RuleTerminalNode remainingRTN = rtn1.getRule().getName().equals( "yyy" ) ? rtn1 : rtn2;
        assertTrue( asList(rian.getPathEndNodes()).containsAll( asList(remainingRTN, rian) ) );
    }
}
