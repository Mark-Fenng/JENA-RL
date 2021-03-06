/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.sparql.expr.aggregate;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.jena.graph.Node ;
import org.apache.jena.sparql.engine.binding.Binding ;
import org.apache.jena.sparql.expr.Expr ;
import org.apache.jena.sparql.expr.ExprEvalException ;
import org.apache.jena.sparql.expr.ExprList ;
import org.apache.jena.sparql.expr.NodeValue ;
import org.apache.jena.sparql.function.FunctionEnv ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

public class AggModeDistinct extends AggregatorBase
{
    // ---- Mode (DISTINCT expr)
    private static Logger log = LoggerFactory.getLogger("ModeDistinct") ;

    public AggModeDistinct(Expr expr) { super("Mode", true, expr) ; } 
    @Override
    public Aggregator copy(ExprList expr) { return new AggModeDistinct(expr.get(0)) ; }

    private static final NodeValue noValuesToMode = NodeValue.nvZERO ; 

    @Override
    public Accumulator createAccumulator()
    { 
        return new AccModeDistinct(getExpr()) ;
    }

    @Override
    public Node getValueEmpty()     { return NodeValue.toNode(noValuesToMode) ; } 

    @Override
    public int hashCode()   {
        return HC_AggModeDistinct ^ getExprList().hashCode() ;
    }

    @Override
    public boolean equals(Aggregator other, boolean bySyntax) {
        if ( other == null ) return false ;
        if ( this == other ) return true ;
        if ( ! ( other instanceof AggModeDistinct ) ) return false ;
        AggModeDistinct a = (AggModeDistinct)other ;
        return exprList.equals(a.exprList, bySyntax) ;
    }

    
    // ---- Accumulator
    class AccModeDistinct extends AccumulatorExpr
    {
        // Non-empty case but still can be nothing because the expression may be undefined.
        private NodeValue total = noValuesToMode ;
        private int count = 0 ;
        ArrayList<NodeValue> collection=new ArrayList<NodeValue>(); 
        
        public AccModeDistinct(Expr expr) { super(expr, true) ; }

        @Override
        protected void accumulate(NodeValue nv, Binding binding, FunctionEnv functionEnv)
        { 
			log.debug("mode {}", nv);

            if ( nv.isNumber() )
            {
                count++ ;
                collection.add(nv);
            }
            else
                throw new ExprEvalException("mode: not a number: "+nv) ;

            log.debug("mode count {}", count);
        }

        @Override
        public NodeValue getAccValue()
        {
            double mode= Double.NaN;
            if ( count == 0 ) return noValuesToMode ;
            if ( super.errorCount != 0 )
                return null ;
            
            int indexsize = collection.size();
            double[] arrDouble = new double[indexsize];
            for(int i=0; i<indexsize; i++){
            	arrDouble[i] = collection.get(i).getDouble();	
            }

    	    HashMap<Double,Integer> amode = new HashMap<Double,Integer>();
    	    int max  = 0;

    	    for(int i = 0; i < arrDouble.length; i++) {
    	        if (amode.get(arrDouble[i]) != null) {
    	            int count = amode.get(arrDouble[i]);
    	            count++;
    	            amode.put(arrDouble[i], count);

    	            if(count > max) {
    	                max  = count;
    	                mode = arrDouble[i];
    	            }
    	        }
    	        else {
    	            amode.put(arrDouble[i],1);
    	            if(mode==0) {
    	            	mode = arrDouble[i];
    	            }
    	        }
    	    }

            return NodeValue.makeDecimal(mode);
        }
        
        @Override
        protected void accumulateError(Binding binding, FunctionEnv functionEnv)
        {}
    }
}
