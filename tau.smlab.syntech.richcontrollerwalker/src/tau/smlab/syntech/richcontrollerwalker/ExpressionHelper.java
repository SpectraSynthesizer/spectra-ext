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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;

import net.sf.javabdd.BDD;
import tau.smlab.syntech.bddgenerator.BDDGenerator;
import tau.smlab.syntech.gameinput.model.Constraint;
import tau.smlab.syntech.gameinput.model.GameInput;
import tau.smlab.syntech.gameinputtrans.TranslationProvider;
import tau.smlab.syntech.gameinputtrans.translator.DefaultTranslators;
import tau.smlab.syntech.gameinputtrans.translator.Translator;
import tau.smlab.syntech.spectragameinput.SpectraInputProvider;

public final class ExpressionHelper {
	private static final String EXP_VALIDATOR_PREFIX = "EXP";
	private static int expressionCounter = 0;
	private static Spec spec;

	// Suppress default constructor for noninstantiability
	private ExpressionHelper() {
		throw new AssertionError();
	}

	public static Spec getSpec() {
		return spec;
	}

	static void setSpec(Spec newSpec) {
		spec = newSpec;
	}

	public static void setPersistentProperty(final QualifiedName propertyQualifier, final String value) {
		try {
			spec.setPersistentProperty(propertyQualifier, value);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	public static Map<Integer, String> getPersistentPropsByPrefix(final String prefix) {
		Map<QualifiedName, String> presistMap = getSpec().getPersistentProperties();
		Map<Integer, String> loadedProps = new HashMap<>();
		for (QualifiedName qualName : presistMap.keySet()) {
			String qualifier = qualName.getQualifier();
			try {
				if (qualifier.startsWith(prefix)) {
					Integer id = Integer.decode(qualifier.split(prefix)[1]);
					loadedProps.put(id, presistMap.get(qualName));
				}
			} catch (Exception e) {
				// Remove problematic property
				ExpressionHelper.setPersistentProperty(qualName, null);
			}
		}
		return loadedProps;
	}

	static final class ExpressionTranslator implements Translator {

		@Override
		public void translate(GameInput input) {
			List<Constraint> sysConstraints = input.getSys().getConstraints();
			Iterator<Constraint> sysConstraintsItr = sysConstraints.iterator();

			// Remove Guarantees
			while (sysConstraintsItr.hasNext()) {
				// Remove non-breakpoint guarantees
				String garName = sysConstraintsItr.next().getName();
				if ((garName != null) && garName.startsWith(EXP_VALIDATOR_PREFIX)) {
					continue;
				}
				sysConstraintsItr.remove();
			}

			input.getSys().setConstraints(sysConstraints);

			// Remove Assumptions
			input.getEnv().getConstraints().clear();
		}
	}

	private static void advanceCounter() {
		expressionCounter++;
	}

	/**
	 * 
	 * @param expression
	 * @return
	 * @throws IOException
	 * @throws CoreException
	 */
	public static BDD getBddFromExpression(String expression) throws IOException, CoreException {
		Objects.requireNonNull(expression);
		GameInput gi;

		String expName = EXP_VALIDATOR_PREFIX + Integer.toString(expressionCounter);
		advanceCounter();
		byte[] updatedSpec = getUpdatedSpecContent(expression, expName);

		try {
			// Parse
			gi = SpectraInputProvider.getGameInput(spec.getFile(), createDummySpecPath(), updatedSpec);
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
	 * @param spec
	 * @param expression
	 * @param expName
	 * @param engUpperBound
	 * @return
	 * @throws IOException
	 * @throws CoreException
	 */
	private static byte[] getUpdatedSpecContent(String expression, String expName) throws IOException, CoreException {
		byte[] specContent = IOUtils.toByteArray(spec.getContents());
		byte[] engVarDeclContent = null;
		if (spec.getEnergyUpperBound() >= 0) {
			engVarDeclContent = createEnergyVarDecl(spec.getEnergyUpperBound()).getBytes();
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
	 * @param spec
	 * @return
	 */
	private static String createDummySpecPath() {
		String watchFileName = spec.getFullPath().removeFileExtension().lastSegment().concat(EXP_VALIDATOR_PREFIX)
				.concat(Integer.toString(expressionCounter)).concat(".spectra");
		String watchFilePath = spec.getFullPath().uptoSegment(spec.getFullPath().segmentCount() - 1).toString()
				.concat("/").concat(watchFileName);
		return watchFilePath;
	}

	/**
	 * Creates a parsable expression from string
	 * 
	 * @param watch watch expression
	 * @return parsable expression
	 */
	private static String createExpressionAsGar(String exp, String expName) {
		return String.format("\n\ngar %s:\nG %s;", expName, exp);
	}

	/**
	 * Creates a parsable expression from string
	 * 
	 * @param watch watch expression
	 * @return parsable expression
	 */
	private static String createEnergyVarDecl(long engUpperBound) {
		return "\n\nsys Int(0.." + engUpperBound + ") energyVal;";
	}

}
