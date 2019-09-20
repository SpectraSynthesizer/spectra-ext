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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.gameinput.model.Constraint;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinput.model.Variable;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gameinputtrans.translator.DefaultTranslators;
import tau.smlab.syntech.gameinputtrans.translator.Translator;
import tau.smlab.syntech.spectragameinput.ErrorsInSpectraException;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;
import tau.smlab.syntech.spectragameinput.SpectraTranslationException;

public class ExpressionHelper {

	private static final String ENERGY_VAL = "energyVal";
	static int expressionCounter = 0;

	/**
	 * 
	 * @param expression
	 * @param specFile
	 * @param engUpperBound
	 *            the energy upper bound. If equals to -1, the given spec has no
	 *            energy constraints
	 * @return
	 * @throws IOException
	 * @throws CoreException
	 */
	public static BDD addExpression(String expression, IFile specFile, long engUpperBound)
			throws IOException, CoreException {
		GameInput gi;

		String expName = ControllerConstants.EXP_VALIDATOR_PREFIX + Integer.toString(expressionCounter);
		expressionCounter++;
		byte[] updatedSpec = getUpdatedSpecContent(specFile, expression, expName, engUpperBound);

		try {
			// Parse
			gi = SpectraInputProvider.getGameInput(specFile, createDummySpecPath(specFile), updatedSpec);
			// Translate
			List<Translator> transList = new ArrayList<Translator>();
			transList.add(new ExpressionTranslator());
			transList.addAll(DefaultTranslators.getDefaultTranslators());
			TranslationProvider.translate(gi, transList);
		} catch (Exception e) {
			return null;
		}

		for (Constraint c : gi.getSys().getConstraints()) {
			if (expName.equals(c.getName())) {
				return BDDGenerator.createBdd(c.getSpec(), c.getTraceId());
			}
		}

		return null;
	}

	/**
	 * 
	 * @param specFile
	 * @return
	 * @throws IOException
	 * @throws CoreException
	 * @throws SpectraTranslationException
	 * @throws ErrorsInSpectraException
	 */
	public static boolean specContainsEnergyValVar(IFile specFile)
			throws IOException, CoreException, ErrorsInSpectraException, SpectraTranslationException {
		GameInput gi = SpectraInputProvider.getGameInput(specFile.getFullPath().toString());
		// Translate
		List<Translator> transList = new ArrayList<Translator>();
		transList.add(new ExpressionTranslator());
		transList.addAll(DefaultTranslators.getDefaultTranslators());
		TranslationProvider.translate(gi, transList);

		for (Variable var : gi.getSys().getVars()) {
			if (var.getName().equals(ENERGY_VAL)) {
				return true;
			}
		}

		for (Variable var : gi.getEnv().getVars()) {
			if (var.getName().equals(ENERGY_VAL)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 
	 * @param specFile
	 * @param expression
	 * @param expName
	 * @param engUpperBound
	 * @return
	 * @throws IOException
	 * @throws CoreException
	 */
	private static byte[] getUpdatedSpecContent(IFile specFile, String expression, String expName, long engUpperBound)
			throws IOException, CoreException {
		byte[] specContent = IOUtils.toByteArray(specFile.getContents());
		byte[] engVarDeclContent = null;
		if (engUpperBound >= 0) {
			engVarDeclContent = createEnergyVarDecl(engUpperBound).getBytes();
		}
		byte[] expContent = createExpressionAsGar(expression, expName).getBytes();

		ByteArrayOutputStream bytesStream = new ByteArrayOutputStream();
		bytesStream.write(specContent);
		if (engVarDeclContent != null) {
			bytesStream.write(engVarDeclContent);
		}
		bytesStream.write(expContent);
		return bytesStream.toByteArray();
	}

	/***
	 * 
	 * @param specFile
	 * @return
	 */
	private static String createDummySpecPath(IFile specFile) {
		String watchFileName = specFile.getFullPath().removeFileExtension().lastSegment()
				.concat(ControllerConstants.EXP_VALIDATOR_PREFIX).concat(Integer.toString(expressionCounter))
				.concat(".spectra");
		String watchFilePath = specFile.getFullPath().uptoSegment(specFile.getFullPath().segmentCount() - 1).toString()
				.concat("/").concat(watchFileName);
		return watchFilePath;
	}

	/**
	 * Creates a parsable expression from string
	 * 
	 * @param watch
	 *            watch expression
	 * @return parsable expression
	 */
	private static String createExpressionAsGar(String exp, String expName) {
		return String.format("\n\ngar %s:\nG %s;", expName, exp);
	}

	/**
	 * Creates a parsable expression from string
	 * 
	 * @param watch
	 *            watch expression
	 * @return parsable expression
	 */
	private static String createEnergyVarDecl(long engUpperBound) {
		return "\n\nsys Int(0.." + engUpperBound + ") energyVal;";
	}
}
