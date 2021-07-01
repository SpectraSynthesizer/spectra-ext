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

package tau.smlab.syntech.richcontrollerwalker.ui.action;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractDisplayExpHelper implements IDisplayExpHelper {

	protected abstract String outerDelimiter();

	protected abstract String innerDelimiter();

	protected abstract boolean hasEnclosing();

	protected IEnclosing getEnclosing() {
		throw new UnsupportedOperationException();
	}

	private String wrapEnclosing(String exp) {
		if (!hasEnclosing()) {
			return exp;
		}
		return getEnclosing().wrap(exp);
	}

	private String stripEnclosing(String exp) {
		if (!hasEnclosing()) {
			return exp.strip();
		}
		return getEnclosing().strip(exp.strip());
	}

	private String[] innerSplit(String exp) {
		return exp.split(innerDelimiter());
	}

	private String[] outerSplit(String exp) {
		return exp.split(outerDelimiter());
	}

	private String innerJoin(String var, String val) {
		return String.join(innerDelimiter(), var, val);
	}

	private String outerJoin(List<String> pairs) {
		return String.join(outerDelimiter(), pairs);
	}

	private String expFromPairs(List<IPair> pairs) {
		List<String> pairStrList = new ArrayList<>();
		for (IPair p : pairs) {
			pairStrList.add(innerJoin(p.var(), p.val()));
		}
		return wrapEnclosing(outerJoin(pairStrList).strip());
	}

	@Override
	public String add(String original, String var, String val) {
		List<IPair> pairs = getPairs(original);
		IPair newPair = new Pair(var, val);
		IPair foundPair = getPairByvar(pairs, var);
		if (foundPair == null) {
			pairs.add(newPair);
		} else {
			int index = pairs.indexOf(foundPair);
			pairs.remove(foundPair);
			pairs.add(index, newPair);
		}
		return expFromPairs(pairs);
	}

	@Override
	public String remove(String original, String var) {
		List<IPair> pairs = getPairs(original);
		IPair foundPair = getPairByvar(pairs, var);
		if (foundPair == null) {
			return original;
		}
		pairs.remove(foundPair);
		return expFromPairs(pairs);
	}
	
	private IPair getPairByvar(List<IPair> pairs, String var) {
		for (Iterator<IPair> iter = pairs.iterator(); iter.hasNext();) {
			IPair p = iter.next();	
			if (p.var().equals(var)) {
				return p;
			}
		}
		return null;
	}

	@Override
	public List<IPair> getPairs(String exp) {
		String[] pairStrArray = outerSplit(stripEnclosing(exp));
		List<IPair> pairs = new ArrayList<>();
		for (String p : pairStrArray) {
			String[] pair = innerSplit(p);
			if (pair.length == 2) {
				pairs.add(new Pair(pair[0], pair[1]));
			}
		}
		return pairs;
	}

	@Override
	public List<String> getVars(String exp) {
		List<IPair> pairs = getPairs(exp);
		List<String> vars = new ArrayList<>();
		for (IPair p : pairs) {
			vars.add(p.var());
		}
		return vars;
	}

	public static class Pair implements IPair {
		private final String var;
		private final String val;

		public Pair(String var, String val) {
			this.var = var;
			this.val = val;
		}

		@Override
		public String var() {
			return var;
		}

		@Override
		public String val() {
			return val;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((val == null) ? 0 : val.hashCode());
			result = prime * result + ((var == null) ? 0 : var.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Pair other = (Pair) obj;
			if (val == null) {
				if (other.val != null)
					return false;
			} else if (!val.equals(other.val))
				return false;
			if (var == null) {
				if (other.var != null)
					return false;
			} else if (!var.equals(other.var))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Pair [var=" + var + ", val=" + val + "]";
		}
		
		

	}

	public static class Enclosing implements IEnclosing {
		private final String opener;
		private final String closer;

		public Enclosing(String opener, String closer) {
			this.opener = opener;
			this.closer = closer;
		}

		@Override
		public String opener() {
			return opener;
		}

		@Override
		public String closer() {
			return closer;
		}
	}

}
