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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.richcontrollerwalker.bdds.IPersistent;
import tau.smlab.syntech.richcontrollerwalker.util.Mod;

public interface IVarConfiguration extends IPersistent {
	int DEFAULT_ID = 0;
	String PREFIX = "VAR_CONFIG";
	String EMPTY_MESSAGE = "<TOO MANY PRESENTATION FILTERS>";

	@Override
	public default String getPrefix() {
		return PREFIX;
	}

	@Override
	public default int getId() {
		return DEFAULT_ID;
	}

	@Override
	public default BDD getBdd() {
		throw new UnsupportedOperationException();
	}

	@Override
	default void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	default boolean isValid() {
		throw new UnsupportedOperationException();
	}

	Set<String> getHidden();

	List<String> getVariablesByModule(Mod mod);

	void setHidden(Collection<String> vars);

	void setExpression(String expression);

	void removeAll();

	void setVariables(Mod mod, Collection<String> vars);
	
	void resetVariableOrder();
	
	boolean getHideFixed();
	void setHideFixed(boolean value);
	
	boolean getHideDontCares();
	void setHideDontCares(boolean value);
}
