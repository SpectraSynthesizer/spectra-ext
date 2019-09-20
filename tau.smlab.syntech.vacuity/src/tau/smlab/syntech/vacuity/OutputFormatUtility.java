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

package tau.smlab.syntech.vacuity;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.emf.ecore.EObject;

import tau.smlab.syntech.gamemodel.BehaviorInfo;
import tau.smlab.syntech.gamemodel.GameModel;
import tau.smlab.syntech.spectragameinput.translator.Tracer;

/**
 * A class for formatted output for console and tests of vacuity.
 * 
 * @author shalom
 *
 */
public class OutputFormatUtility {

	public static String linesOfSet(List<Integer> list) {
		String result = "[";
		for (int i=0; i< list.size(); i++) {
			result += getline(list.get(i));
			if (i<list.size()-1) {
				result += ", ";
			}
		}
		result += "]";
		return result;
	}

	public static int getline(Integer b) {
		EObject obj=Tracer.getTarget(b);
		int line = -1;
		if (obj!=null) { // protect from non traceable
			line = NodeModelUtils.getNode(obj).getStartLine();
		}
		return line;
	}

	public static void writeImpMapCommaSep(Map<Integer, List<Integer>> map, long timeToFirst, long timeToAll, GameModel gm, PrintStream out) {
		out.print(", " + timeToFirst);
		out.print(", " + timeToAll);
		out.print(", " + map.size());
		int trivial = 0;
		for (Integer bhi : map.keySet()) {
			
			// compute triviality without core
			List<BehaviorInfo> where = new ArrayList<BehaviorInfo>();
			where.addAll(gm.getEnvBehaviorInfo());
			where.addAll(gm.getSysBehaviorInfo());

			// find the behavior from the relevant module according to the TraceInfo
			BehaviorInfo b = null;
			for (BehaviorInfo bi : where) {
				if (bi.traceId == bhi) {
					assert(b==null);
					b = bi;
				}
			}
	
			assert(b!=null);

			if (Vacuity.isTrivial(gm, b)) {
				trivial++;								
			}
				
		}
		out.print(", " + trivial);

	}

	public static void writeUnreachMapCommaSep(Map<BehaviorInfo, List<Integer>> map, long timeToFirst, long timeToAll, PrintStream out) {
		out.print(", " + timeToFirst);
		out.print(", " + timeToAll);
		out.print(", " + map.size());
	}
	
	public static void writeImpVacList(VacuityType type, Map<Integer, List<Integer>> map, boolean consistent, PrintStream out) {
		out.print(type + " ");
		if (!consistent) {
			out.println(VacuityType.inconsistencyFlag + " ");
		} else {
			out.print(map.size() + " ");
			for (Integer bhi : map.keySet()) {
				out.print(getline(bhi) + " ");
			}
			out.println();
		}
	}
	
	public static void writeUnreachVacList(VacuityType type, Map<BehaviorInfo, List<Integer>> map, boolean consistent, PrintStream out) {
		out.print(type + " ");
		if (!consistent) {
			out.println(VacuityType.inconsistencyFlag + " ");
		} else {
			out.print(map.size() + " ");
			for (BehaviorInfo bhi : map.keySet()) {
				out.print(bhi.safety.notWithDoms().toString() + " ");
			}
			out.println();
		}
	}
	
	public static void header(boolean withTrivial, PrintStream out) {
		out.print(",time to first, time to all, all vacuities" + (withTrivial ? ", trivial vacuities" : ""));
	}

}
