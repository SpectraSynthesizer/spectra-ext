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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDVarSet;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public class BddUtil {
	private static Vars vars;

	public static void setNewVars(Map<String, String[]> sysVarsMap, Map<String, String[]> envVarsMap)
			throws IOException {
		vars = new Vars(sysVarsMap, envVarsMap);
	}

	public static BDD getVarsByModule(Mod mod) {
		return mod.isEnv() ? vars.getEnvVars() : vars.getSysVars();
	}

	public static Map<String, String[]> getVarsMap(Mod mod) {
		return Objects.requireNonNull(mod).equals(Mod.ENV) ? vars.envMap : vars.sysMap;
	}

	public static Map<String, IVar> getAllVarsMap() {
		return vars.allMap;
	}

	public static List<String> getVarNamesListByModule(Mod mod) {
		return new ArrayList<>(
				Objects.requireNonNull(mod).equals(Mod.ENV) ? vars.envMap.keySet() : vars.sysMap.keySet());
	}

	public static Set<String> getAllVarNames() {
		return Collections.unmodifiableSet(vars.allMap.keySet());
	}

	public static void clearIBddCollection(Collection<? extends IBdd> c) {
		if (Objects.nonNull(c)) {
			for (IBdd iBdd : c) {
				iBdd.clear();
			}
			c.clear();
		}
	}

	public static String bddToStr(final BDD bdd) {
		return bdd.toStringWithDomains(Env.stringer);
	}

	public static void freeBdd(BDD bdd) {
		if (bdd != null && !bdd.isFree()) {
			bdd.free();
		}
	}

	public static boolean isValidVar(String varName) {
		return getAllVarsMap().containsKey(Objects.requireNonNull(varName));
	}
	
	public static BDDVarSet getVarSet(Collection<IVar> vars) {
		BDDVarSet set = Env.getEmptySet();
		for (IVar v : vars) {
			set.unionWith(Env.getVar(v.name()).getDomain().set());
		}
		
		return set;
	}
	
	public static IVar getVarByName(String name) {
		return Objects.requireNonNull(vars.allMap.get(Objects.requireNonNull(name)));
	}

	public static final class Vars {
		private final Map<String, String[]> sysMap;
		private final Map<String, String[]> envMap;
		private final Map<String, IVar> allMap = new HashMap<String, IVar>();
		private final BDD envVars;
		private final BDD sysVars;

		public Vars(Map<String, String[]> sysMap, Map<String, String[]> envMap) throws IOException {
			this.sysMap = sysMap;
			this.envMap = envMap;
			envVars = getModuleBDDVarSet(this.envMap.keySet());
			sysVars = getModuleBDDVarSet(this.sysMap.keySet());
			populateAllVarMap();
		}

		private void populateAllVarMap() {
			for (Entry<String, String[]> e : sysMap.entrySet()) {
				allMap.put(e.getKey(), new VarSummary(e.getKey(), e.getValue(), Mod.SYS));
			}
			for (Entry<String, String[]> e : envMap.entrySet()) {
				allMap.put(e.getKey(), new VarSummary(e.getKey(), e.getValue(), Mod.ENV));
			}
		}

		private BDD getSysVars() {
			return sysVars;
		}

		private BDD getEnvVars() {
			return envVars;
		}

		/**
		 * Create a BDD from a group of string variables
		 * 
		 * @param moduleVarsNames variables of the BDD
		 * @return BDD representation of the variables
		 */
		private BDD getModuleBDDVarSet(Set<String> moduleVarsNames) {
			BDD moduleVars = Env.TRUE();
			for (String moduleVarName : moduleVarsNames) {
				moduleVars.andWith(Env.getVar(moduleVarName).getDomain().set().toBDD());
			}
			return moduleVars;
		}
	}

}
