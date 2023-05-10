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

package tau.smlab.syntech.ctd;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import net.sf.javabdd.BDD;

/**
 * A class representing a coverage search.
 * Useful for recursively trying different options.
 */
public class SearchState {

	public static DefaultMutableTreeNode copyNode(DefaultMutableTreeNode originNode) {
		DefaultMutableTreeNode copy = new DefaultMutableTreeNode(
				originNode.getUserObject() == null ? null : ((BDD) originNode.getUserObject()).id());

		if (originNode.isLeaf()) {
			return copy;
		} else {
			int cc = originNode.getChildCount();
			for (int i = 0; i < cc; i++) {
				copy.add(copyNode((DefaultMutableTreeNode) originNode.getChildAt(i)));
			}
			return copy;
		}
	}

	public SearchState(AbstractSet<TupleBDD> uncoveredSet, TreeModel statesTree) {
		this.uncoveredSet = uncoveredSet;
		this.statesTree = (DefaultTreeModel) statesTree;
		this.statesLeft = 0;
	}

	public SearchState(AbstractSet<TupleBDD> uncoveredSet, TreeModel statesTree,
			double statesLeft) {
		this.uncoveredSet = uncoveredSet;
		this.statesTree = (DefaultTreeModel) statesTree;
		this.statesLeft = statesLeft;
	}

	public static List<Tuple> copyTestTuples(ArrayList<Tuple> tuples) {
		List<Tuple> newList = new ArrayList<Tuple>(tuples.size());
		for (Tuple tuple : tuples) {
			newList.add((Tuple) tuple.clone());
		}

		return newList;
	}

	public static AbstractSet<TupleBDD> copyUncoveredSet(AbstractSet<TupleBDD> set) {
		if (set instanceof TreeSet) {
		    return copyUncoveredSet((TreeSet<TupleBDD>)set);
		}
		if (set instanceof LinkedHashSet<?>) {
		    return copyUncoveredSet((LinkedHashSet<TupleBDD>)set);
		}
		
		throw new UnsupportedOperationException("Unknown set type");
	}
	
	public static TreeSet<TupleBDD> copyUncoveredSet(TreeSet<TupleBDD> set) {
		TreeSet<TupleBDD> res = new TreeSet<TupleBDD>(new TupleBDDCmp());

		// TODO might be inefficient and take nlogn depending on impl.
		for (TupleBDD tupleBDD : set) {
			res.add(tupleBDD.clone());
		}

		return res;
	}
	
	public static LinkedHashSet<TupleBDD> copyUncoveredSet(LinkedHashSet<TupleBDD> set) {
		LinkedHashSet<TupleBDD> res = new LinkedHashSet<TupleBDD>();

		// TODO might be inefficient and take nlogn depending on impl.
		for (TupleBDD tupleBDD : set) {
			res.add(tupleBDD.clone());
		}

		return res;
	}

	public SearchState getCopy() {
		return new SearchState(copyUncoveredSet(this.uncoveredSet),
				new DefaultTreeModel(copyNode((DefaultMutableTreeNode) this.statesTree.getRoot())), this.statesLeft);
	}

	public static void freeNode(DefaultMutableTreeNode node) {
		if (node.getUserObject() != null) {
			((BDD) node.getUserObject()).free();
		}

		if (!node.isLeaf()) {
			int cc = node.getChildCount();
			for (int i = 0; i < cc; i++) {
				freeNode((DefaultMutableTreeNode) node.getChildAt(i));
			}
		}
	}

	public void free() {
		for (TupleBDD t : this.uncoveredSet) {
			t.free();
		}
		this.uncoveredSet = null;

		this.statesTree = null;
	}
	
	public void updateStatesLeft() {
		this.statesLeft = 0;
		Iterator<TupleBDD> iter = this.uncoveredSet.iterator();
		while (iter.hasNext()) {
			TupleBDD tupleBDD = iter.next();
			this.statesLeft += tupleBDD.varSatCount();
		}
	}

	public AbstractSet<TupleBDD> uncoveredSet;
	public DefaultTreeModel statesTree;
	public double statesLeft;
}
