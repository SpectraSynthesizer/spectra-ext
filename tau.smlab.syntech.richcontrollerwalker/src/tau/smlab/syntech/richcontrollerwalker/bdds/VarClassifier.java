/*
Copyright (c) since 2015, Tel Aviv University and Software Modeling Lab

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Tel Aviv University and Software Modeling Lab nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Tel Aviv University and Software Modeling Lab 
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE 
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 
*/

package tau.smlab.syntech.richcontrollerwalker.bdds;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDFactory;

public final class VarClassifier {

	private static Optional<Integer> getConstBit(BDD successors, BDD var) {
		if (successors.and(var).equals(successors)) {
			return Optional.of(1);
		}
		if (successors.and(var.not()).equals(successors)) {
			return Optional.of(0);
		}

		return Optional.empty();
	}

	private static Optional<Integer> getConstValue(BDD successors, BDDDomain varDomain) {
		BigInteger result = BigInteger.ZERO;
		BDDFactory factory = varDomain.getFactory();
		for (int bitVar : varDomain.vars()) {
			Optional<Integer> bitOpt = getConstBit(successors, factory.ithVar(bitVar));
			if (bitOpt.isEmpty()) {
				return Optional.empty();
			}

			int bit = bitOpt.get();
			result.shiftLeft(1);
			if (bit != 0) {
				result = result.setBit(0);
			}
		}

		return Optional.of(result.intValueExact());
	}

	/**
	 * Find all the fixed variables in the bdd with their matching const value.
	 * These are values that have only one possible assignment for which the BDD is
	 * satisfied.
	 * 
	 * @return A map containing the fixed value for each of the fixed variables.
	 */
	public static Map<IVar, IValue> findFixedVars(BDD successors) {
		BDDDomain[] usedVars = successors.support().getDomains();
		Map<String, IVar> allVars = BddUtil.getAllVarsMap();

		Map<IVar, IValue> fixedVars = new HashMap<>();

		for (BDDDomain d : usedVars) {
			Optional<Integer> constVal = getConstValue(successors, d);
			if (constVal.isEmpty()) {
				continue;
			}

			IVar var = allVars.get(d.getName());
			String realVal = var.domain().get(constVal.get());
			fixedVars.put(var, new ValueSummary(realVal));
		}

		return fixedVars;
	}

	private static boolean isVarUsed(BDDDomain[] vars, IVar var) {
		return Stream.of(vars).anyMatch(v -> v.getName().equals(var.name()));
	}

	/**
	 * Find all the "Dont care" variables for the given successors BDD. Don't care
	 * variables are variables whose value doesn't affect the truth value of the
	 * BDD.
	 * 
	 * @return A list of the dont-care variables for the BDD.
	 */
	public static List<IVar> findDontCareVars(BDD successors) {
		BDDDomain[] usedVars = successors.support().getDomains();
		Map<String, IVar> allVars = BddUtil.getAllVarsMap();

		List<IVar> dontCares = new ArrayList<>();

		for (IVar var : allVars.values()) {
			if (!isVarUsed(usedVars, var)) {
				dontCares.add(var);
			}
		}

		return dontCares;
	}

}
