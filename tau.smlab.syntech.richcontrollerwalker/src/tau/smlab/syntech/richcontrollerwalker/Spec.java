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

package tau.smlab.syntech.richcontrollerwalker;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;

import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinput.model.Variable;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gameinputtrans.translator.DefaultTranslators;
import tau.smlab.syntech.gameinputtrans.translator.Translator;
import tau.smlab.syntech.jtlv.Env;
import tau.smlab.syntech.jtlv.env.module.ModuleBDDField;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;

public class Spec {
	private static final String ENERGY_VAL = "energyVal";
	private final IFile file;
	private final long energyUpperBound;
	private final String workingDir;
	private final String filePath;
	private final String modelName;

	Spec(IFile specFile) {
		this.file = Objects.requireNonNull(specFile);
		energyUpperBound = computeEnergyUpperBound();
		workingDir = specFile.getLocation().uptoSegment(specFile.getLocation().segmentCount() - 1).toString();
		filePath = computeFilePath();
		modelName = computeModelName();
	}

	IFile getFile() {
		return file;
	}

	long getEnergyUpperBound() {
		return energyUpperBound;
	}

	String getWorkingDir() {
		return workingDir;
	}

	String getFilePath() {
		return filePath;
	}

	String getModelName() {
		return modelName;
	}

	IPath getFullPath() {
		return file.getFullPath();
	}

	InputStream getContents() throws CoreException {
		return file.getContents();
	}

	Map<QualifiedName, String> getPersistentProperties() {
		try {
			return file.getPersistentProperties();
		} catch (Exception e) {
			e.printStackTrace();
			return new HashMap<>();
		}	
	}

	void setPersistentProperty(final QualifiedName propertyQualifier, final String value) throws CoreException {
		file.setPersistentProperty(propertyQualifier, value);
	}

	private String computeModelName() {
		return SpectraInputProvider.getSpectraModel(file.getFullPath().toString()).getName();
	}

	private String computeFilePath() {
		return file.getLocation().uptoSegment(file.getLocation().segmentCount() - 1).addTrailingSeparator().toString();
	}

	private long computeEnergyUpperBound() {
		return energyInEnvButNotSpec() ? getEnergyVar().getDomain().size().longValue() - 1 : -1;
	}

	private boolean energyInEnvButNotSpec() {
		return !specContainsEnergyValVar() && getEnergyVar() != null;
	}

	private boolean doesVarEqualEnergyVal(Variable var) {
		return var.getName().equals(ENERGY_VAL);
	}

	private ModuleBDDField getEnergyVar() {
		return Env.getVar(ENERGY_VAL);
	}

	private boolean specContainsEnergyValVar() {
		GameInput gi;
		try {
			gi = SpectraInputProvider.getGameInput(file.getFullPath().toString());
		} catch (ErrorsInSpectraException | SpectraTranslationException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("spec file did allow parsing of energy val.");
		}
		// Translate
		List<Translator> transList = new ArrayList<Translator>();
		transList.add(new ExpressionHelper.ExpressionTranslator());
		transList.addAll(DefaultTranslators.getDefaultTranslators());
		TranslationProvider.translate(gi, transList);

		for (Variable var : gi.getSys().getVars()) {
			if (doesVarEqualEnergyVal(var)) {
				return true;
			}
		}
		for (Variable var : gi.getEnv().getVars()) {
			if (doesVarEqualEnergyVal(var)) {
				return true;
			}
		}
		return false;
	}
}
