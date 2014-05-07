package de.avgl.dmp.converter.morph;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringEscapeUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

import de.avgl.dmp.converter.DMPConverterException;
import de.avgl.dmp.init.util.DMPStatics;
import de.avgl.dmp.persistence.model.job.Component;
import de.avgl.dmp.persistence.model.job.Function;
import de.avgl.dmp.persistence.model.job.Mapping;
import de.avgl.dmp.persistence.model.job.Task;
import de.avgl.dmp.persistence.model.job.Transformation;
import de.avgl.dmp.persistence.model.schema.MappingAttributePathInstance;

/**
 * Creates a metamorph script from a given {@link Task}.
 * 
 * @author phorn
 * @author niederl
 * @author tgaengler
 */
public class MorphScriptBuilder {

	private static final org.apache.log4j.Logger	LOG									= org.apache.log4j.Logger.getLogger(MorphScriptBuilder.class);

	private static final String						MAPPING_PREFIX						= "mapping";

	private static final DocumentBuilderFactory		DOC_FACTORY							= DocumentBuilderFactory.newInstance();

	private static final String						SCHEMA_PATH							= "schemata/metamorph.xsd";

	private static final TransformerFactory			TRANSFORMER_FACTORY;

	private static final String						TRANSFORMER_FACTORY_CLASS			= "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";

	private static final String						INPUT_VARIABLE_IDENTIFIER			= "inputString";

	private static final String						OUTPUT_VARIABLE_PREFIX_IDENTIFIER	= "__TRANSFORMATION_OUTPUT_VARIABLE__";

	private static final String						FILTER_VARIABLE_POSTFIX				= ".filtered";

	private static final String						OCCURRENCE_VARIABLE_POSTFIX			= ".occurrence";

	static {
		System.setProperty("javax.xml.transform.TransformerFactory", MorphScriptBuilder.TRANSFORMER_FACTORY_CLASS);
		TRANSFORMER_FACTORY = TransformerFactory.newInstance();
		MorphScriptBuilder.TRANSFORMER_FACTORY.setAttribute("indent-number", 4);

		final URL resource = Resources.getResource(MorphScriptBuilder.SCHEMA_PATH);
		final CharSource inputStreamInputSupplier = Resources.asCharSource(resource, Charsets.UTF_8);

		try (final Reader schemaStream = inputStreamInputSupplier.openStream()) {

			// final StreamSource SCHEMA_SOURCE = new StreamSource(schemaStream);
			final SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Schema schema = null;

			try {

				// TODO: dummy schema right now, since it couldn't parse the metamorph schema for some reason
				schema = sf.newSchema();
			} catch (final SAXException e) {

				e.printStackTrace();
			}

			if (schema == null) {

				MorphScriptBuilder.LOG.error("couldn't parse schema");
			}

			MorphScriptBuilder.DOC_FACTORY.setSchema(schema);

		} catch (final IOException e1) {
			MorphScriptBuilder.LOG.error("couldn't read schema resource", e1);
		}
	}

	private Document								doc;

	private Element varDefinition(final String key, final String value) {
		final Element var = doc.createElement("var");
		var.setAttribute("name", key);
		var.setAttribute("value", value);

		return var;
	}

	public String render(final boolean indent, final Charset encoding) {
		final String defaultEncoding = encoding.name();
		final Transformer transformer;
		try {
			transformer = MorphScriptBuilder.TRANSFORMER_FACTORY.newTransformer();
		} catch (final TransformerConfigurationException e) {
			e.printStackTrace();
			return null;
		}

		transformer.setOutputProperty(OutputKeys.INDENT, indent ? "yes" : "no");

		transformer.setOutputProperty(OutputKeys.ENCODING, defaultEncoding);

		final ByteArrayOutputStream stream = new ByteArrayOutputStream();

		final StreamResult result;
		try {
			result = new StreamResult(new OutputStreamWriter(stream, defaultEncoding));
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}

		try {
			transformer.transform(new DOMSource(doc), result);
		} catch (final TransformerException e) {
			e.printStackTrace();
			return null;
		}

		try {
			return stream.toString(defaultEncoding);
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String render(final boolean indent) {
		return render(indent, Charset.forName("UTF-8"));
	}

	@Override
	public String toString() {
		return render(true);
	}

	public File toFile() throws IOException {
		final String str = render(false);

		final File file = File.createTempFile("avgl_dmp", ".tmp");

		final BufferedWriter bw = new BufferedWriter(new FileWriter(file));
		bw.write(str);
		bw.close();

		return file;
	}

	public MorphScriptBuilder apply(final Task task) throws DMPConverterException {

		final DocumentBuilder docBuilder;
		try {
			docBuilder = MorphScriptBuilder.DOC_FACTORY.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new DMPConverterException(e.getMessage());
		}

		doc = docBuilder.newDocument();
		doc.setXmlVersion("1.1");

		final Element rootElement = doc.createElement("metamorph");
		rootElement.setAttribute("xmlns", "http://www.culturegraph.org/metamorph");
		rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		rootElement.setAttribute("xsi:schemaLocation", "http://www.culturegraph.org/metamorph metamorph.xsd");
		rootElement.setAttribute("entityMarker", DMPStatics.ATTRIBUTE_DELIMITER.toString());
		rootElement.setAttribute("version", "1");
		doc.appendChild(rootElement);

		final Element meta = doc.createElement("meta");
		rootElement.appendChild(meta);

		final Element metaName = doc.createElement("name");
		meta.appendChild(metaName);

		final Element rules = doc.createElement("rules");
		rootElement.appendChild(rules);

		final List<String> metas = Lists.newArrayList();

		for (final Mapping mapping : task.getJob().getMappings()) {
			metas.add(MorphScriptBuilder.MAPPING_PREFIX + mapping.getId());

			createTransformation(rules, mapping);

		}

		metaName.setTextContent(Joiner.on(", ").join(metas));

		return this;
	}

	private void createTransformation(final Element rules, final Mapping mapping) {

		// first handle the parameter mapping from the attribute paths of the mapping to the transformation component

		final Component transformationComponent = mapping.getTransformation();

		if (transformationComponent == null) {

			MorphScriptBuilder.LOG.debug("transformation component for mapping '" + mapping.getId() + "' was empty");

			// just delegate input attribute path to output attribute path

			mapInputAttributePathToOutputAttributePath(mapping, rules);

			return;
		}

		if (transformationComponent.getParameterMappings() == null || transformationComponent.getParameterMappings().isEmpty()) {

			MorphScriptBuilder.LOG.debug("parameter mappings for transformation component shouldn't be empty, mapping: '" + mapping.getId() + "'");

			// delegate input attribute path to output attribute path + add possible transformations (components)

			mapInputAttributePathToOutputAttributePath(mapping, rules);
			processTransformationComponentFunction(transformationComponent, mapping, null, rules);

			return;
		}

		// get all input attribute paths and create datas for them

		final Set<MappingAttributePathInstance> inputAttributePathInstances = mapping.getInputAttributePaths();

		final Map<String, List<String>> inputAttributePaths = Maps.newLinkedHashMap();

		for (final Iterator<MappingAttributePathInstance> iterator = inputAttributePathInstances.iterator(); iterator.hasNext();) {

			final MappingAttributePathInstance mappingAttributePathInstance = iterator.next();

			final String inputAttributePathString = mappingAttributePathInstance.getAttributePath().toAttributePath();

			final List<String> variablesFromInputAttributePaths = getParameterMappingKeys(inputAttributePathString, transformationComponent);

			final Integer ordinal = mappingAttributePathInstance.getOrdinal();

			final String filterExpressionStringUnescaped = getFilterExpression(mappingAttributePathInstance);

			final List<Element> inputAttributePathsToVars = addInputAttributePathVars(variablesFromInputAttributePaths, inputAttributePathString,
					rules, inputAttributePaths, filterExpressionStringUnescaped, ordinal);

		}

		final String outputAttributePath = mapping.getOutputAttributePath().getAttributePath().toAttributePath();

		final List<String> variablesFromOutputAttributePath = getParameterMappingKeys(outputAttributePath, transformationComponent);

		final Element dataOutput = addOutputAttributePathMapping(variablesFromOutputAttributePath, outputAttributePath, rules);

		processTransformationComponentFunction(transformationComponent, mapping, inputAttributePaths, rules);
	}

	private void createParameters(final Map<String, String> parameterMappings, final Element component) {

		// TODO: parse parameter values that can be simple string values, JSON objects or JSON arrays (?)
		// => for now we expect only simple string values

		if (parameterMappings != null) {

			for (final Entry<String, String> parameterMapping : parameterMappings.entrySet()) {

				if (parameterMapping.getKey() != null) {

					if (parameterMapping.getKey().equals(MorphScriptBuilder.INPUT_VARIABLE_IDENTIFIER)) {

						continue;
					}

					if (parameterMapping.getValue() != null) {

						final Attr param = doc.createAttribute(parameterMapping.getKey());
						param.setValue(parameterMapping.getValue());
						component.setAttributeNode(param);
					}
				}
			}
		}
	}

	private Element createDataTag(final Component singleInputComponent, final String dataNameAttribute, final String dataSourceAttribute) {

		final Element data = doc.createElement("data");

		data.setAttribute("source", "@" + dataSourceAttribute);

		data.setAttribute("name", "@" + dataNameAttribute);

		final Element comp = doc.createElement(singleInputComponent.getFunction().getName());

		createParameters(singleInputComponent.getParameterMappings(), comp);

		data.appendChild(comp);

		return data;
	}

	private Element createCollectionTag(final Component multipleInputComponent, final String collectionNameAttribute,
			final Set<String> collectionSourceAttributes) {

		final Element collection;

		if (multipleInputComponent.getFunction().getName().equals("concat")) {

			final Map<String, String> parameters = multipleInputComponent.getParameterMappings();

			String valueString = "";

			String delimiterString = ", ";

			if (parameters.get("prefix") != null) {
				valueString = parameters.get("prefix").toString();
			}

			if (parameters.get("delimiter") != null) {
				delimiterString = parameters.get("delimiter").toString();
			}

			collection = doc.createElement("combine");

			collection.setAttribute("name", "@" + collectionNameAttribute);

			collection.setAttribute("reset", "true");

			final Iterator<String> iter = collectionSourceAttributes.iterator();

			int i = 0;

			while (iter.hasNext()) {

				final String sourceAttribute = iter.next();

				valueString += "${" + sourceAttribute + "}";

				if ((i++ + 1) < collectionSourceAttributes.size()) {
					valueString += delimiterString;
				}

				final Element collectionData = doc.createElement("data");

				collectionData.setAttribute("source", "@" + sourceAttribute);

				collectionData.setAttribute("name", sourceAttribute);

				collection.appendChild(collectionData);

			}

			if (parameters.get("postfix") != null) {
				valueString += parameters.get("postfix").toString();
			}

			collection.setAttribute("value", valueString);

		} else {

			collection = doc.createElement(multipleInputComponent.getFunction().getName());

			createParameters(multipleInputComponent.getParameterMappings(), collection);

			collection.setAttribute("name", "@" + collectionNameAttribute);

			for (final String sourceAttribute : collectionSourceAttributes) {

				final Element collectionData = doc.createElement("data");

				collectionData.setAttribute("source", "@" + sourceAttribute);

				collection.appendChild(collectionData);
			}
		}
		return collection;
	}

	private List<String> getParameterMappingKeys(final String attributePathString, final Component transformationComponent) {

		List<String> parameterMappingKeys = null;

		final Map<String, String> transformationParameterMapping = transformationComponent.getParameterMappings();

		for (final Entry<String, String> parameterMapping : transformationParameterMapping.entrySet()) {

			if (StringEscapeUtils.unescapeXml(parameterMapping.getValue()).equals(attributePathString)) {

				if (parameterMappingKeys == null) {

					parameterMappingKeys = Lists.newArrayList();
				}

				parameterMappingKeys.add(parameterMapping.getKey());
			}
		}

		return parameterMappingKeys;
	}

	private List<Element> addInputAttributePathVars(final List<String> variables, final String inputAttributePathString, final Element rules,
			final Map<String, List<String>> inputAttributePaths, final String filterExpressionString, final Integer ordinal) {

		if (variables == null || variables.isEmpty()) {

			return null;
		}

		List<Element> vars = null;

		final String inputAttributePathStringXMLEscaped = StringEscapeUtils.escapeXml(inputAttributePathString);

		for (String variable : variables) {

			if (variable.startsWith(MorphScriptBuilder.OUTPUT_VARIABLE_PREFIX_IDENTIFIER)) {

				continue;
			}

			final String manipulatedVariable;

			if (checkOrdinal(ordinal)) {

				manipulatedVariable = addOrdinalFilter(ordinal, variable, rules);
			} else {

				manipulatedVariable = variable;
			}

			final Map<String, String> filterExpressionMap = extractFilterExpressions(filterExpressionString);

			if (filterExpressionMap == null || filterExpressionMap.isEmpty()) {

				final Element data = doc.createElement("data");
				data.setAttribute("source", inputAttributePathStringXMLEscaped);

				data.setAttribute("name", "@" + variable);

				rules.appendChild(data);
			} else {

				addFilter(filterExpressionString, inputAttributePathStringXMLEscaped, manipulatedVariable, filterExpressionMap, rules);
			}

			if (vars == null) {

				vars = Lists.newArrayList();
			}

			inputAttributePaths.put(inputAttributePathStringXMLEscaped, variables);
		}

		return vars;
	}

	private Element addOutputAttributePathMapping(final List<String> variables, final String outputAttributePathString, final Element rules) {

		if (variables == null || variables.isEmpty()) {

			return null;
		}

		// .ESCAPE_XML11.with(NumericEntityEscaper.between(0x7f, Integer.MAX_VALUE)).translate( <- also doesn't work
		final String outputAttributePathStringXMLEscaped = StringEscapeUtils.escapeXml(outputAttributePathString);

		// TODO: maybe add mapping to default output variable identifier, if output attribute path is not part of the parameter
		// mappings of the transformation component
		// maybe for later: separate parameter mapppings into input parameter mappings and output parameter mappings

		for (final String variable : variables) {

			if (!variable.startsWith(MorphScriptBuilder.OUTPUT_VARIABLE_PREFIX_IDENTIFIER)) {

				continue;
			}

			final Element dataOutput = doc.createElement("data");
			dataOutput.setAttribute("source", "@" + variable);
			dataOutput.setAttribute("name", outputAttributePathStringXMLEscaped);
			rules.appendChild(dataOutput);
		}

		return null;
	}

	private void mapInputAttributePathToOutputAttributePath(final Mapping mapping, final Element rules) {

		final Set<MappingAttributePathInstance> inputMappingAttributePathInstances = mapping.getInputAttributePaths();

		if (inputMappingAttributePathInstances == null || inputMappingAttributePathInstances.isEmpty()) {

			return;
		}

		final MappingAttributePathInstance outputMappingAttributePathInstance = mapping.getOutputAttributePath();

		if (outputMappingAttributePathInstance == null) {

			return;
		}

		final MappingAttributePathInstance inputMappingAttributePathInstance = inputMappingAttributePathInstances.iterator().next();
		final String inputAttributePathStringXMLEscaped = StringEscapeUtils.escapeXml(inputMappingAttributePathInstance.getAttributePath()
				.toAttributePath());
		final String filterExpression = getFilterExpression(inputMappingAttributePathInstance);
		final Integer ordinal = inputMappingAttributePathInstance.getOrdinal();

		final String inputVariable;
		final boolean isOrdinalValid = checkOrdinal(ordinal);
		final Map<String, String> filterExpressionMap = extractFilterExpressions(filterExpression);

		String var1000 = "var1000";
		boolean takeVariable = false;

		if(isOrdinalValid) {

			var1000 = addOrdinalFilter(ordinal, var1000, rules);
			takeVariable = true;
		}

		if(filterExpressionMap != null && !filterExpressionMap.isEmpty()) {

			addFilter(filterExpression, inputAttributePathStringXMLEscaped, var1000, filterExpressionMap, rules);
			takeVariable = true;
		}

		if(!takeVariable) {

			inputVariable = inputAttributePathStringXMLEscaped;
		} else {

			inputVariable = "@" + var1000;
		}

		final Element data = doc.createElement("data");
		data.setAttribute("source", inputVariable);

		data.setAttribute("name", StringEscapeUtils.escapeXml(outputMappingAttributePathInstance.getAttributePath().toAttributePath()));

		rules.appendChild(data);
	}

	private void processTransformationComponentFunction(final Component transformationComponent, final Mapping mapping,
			final Map<String, List<String>> inputAttributePathVariablesMap, final Element rules) {

		final String transformationOutputVariableIdentifier = determineTransformationOutputVariable(transformationComponent);
		final String finalTransformationOutputVariableIdentifier = transformationOutputVariableIdentifier == null ? MorphScriptBuilder.OUTPUT_VARIABLE_PREFIX_IDENTIFIER
				: transformationOutputVariableIdentifier;

		final Function transformationFunction = transformationComponent.getFunction();

		if (transformationFunction == null) {

			MorphScriptBuilder.LOG.debug("transformation component's function for mapping '" + mapping.getId() + "' was empty");

			// nothing to do - mapping from input attribute path to output attribute path should be fine already

			return;
		}

		switch (transformationFunction.getFunctionType()) {

			case Function:

				// TODO: process simple function

				MorphScriptBuilder.LOG.error("transformation component's function for mapping '" + mapping.getId()
						+ "' was a real FUNCTION. this is not supported right now.");

				break;

			case Transformation:

				// TODO: process simple input -> output mapping (?)

				final Transformation transformation = (Transformation) transformationFunction;

				final Set<Component> components = transformation.getComponents();

				if (components == null) {

					MorphScriptBuilder.LOG.debug("transformation component's transformation's components for mapping '" + mapping.getId()
							+ "' are empty");
					return;
				}

				for (final Component component : components) {

					processComponent(component, inputAttributePathVariablesMap, finalTransformationOutputVariableIdentifier, rules);
				}

				break;
		}
	}

	private void processComponent(final Component component, final Map<String, List<String>> inputAttributePathVariablesMap,
			final String transformationOutputVariableIdentifier, final Element rules) {

		String[] inputStrings = {};

		final Map<String, String> componentParameterMapping = component.getParameterMappings();

		if (componentParameterMapping != null) {

			for (final Entry<String, String> parameterMapping : componentParameterMapping.entrySet()) {

				if (parameterMapping.getKey().equals(MorphScriptBuilder.INPUT_VARIABLE_IDENTIFIER)) {

					inputStrings = parameterMapping.getValue().split(",");

					break;
				}
			}
		}

		// this is a list of input variable names related to current component, which should be unique and ordered
		final Set<String> inputVariables = new LinkedHashSet<String>();

		for (final String inputString : inputStrings) {

			inputVariables.add(inputString);
		}

		// if no inputString is set, take input component name
		if (component.getInputComponents() != null && !component.getInputComponents().isEmpty()) {

			for (final Component inputComponent : component.getInputComponents()) {

				inputVariables.add(inputComponent.getName());
			}
		} 

		if (inputVariables.isEmpty()) {

			// couldn't identify an input variable or an input attribute path

			return;
		}

		if (inputVariables.size() > 1) {

			String collectionNameAttribute = null;

			if (component.getOutputComponents() == null || component.getOutputComponents().isEmpty()) {

				// the end has been reached

				// collectionNameAttribute = getKeyParameterMapping(outputAttributePath, transformationComponent);
				collectionNameAttribute = transformationOutputVariableIdentifier;
			} else {

				collectionNameAttribute = component.getName();
			}

			final Element collection = createCollectionTag(component, collectionNameAttribute, inputVariables);

			rules.appendChild(collection);

			return;
		}

		String dataNameAttribute = null;

		if (component.getOutputComponents() == null || component.getOutputComponents().isEmpty()) {

			// dataNameAttribute = getKeyParameterMapping(outputAttributePath, transformationComponent);
			dataNameAttribute = transformationOutputVariableIdentifier;
		} else {

			dataNameAttribute = component.getName();
		}

		final Element data = createDataTag(component, dataNameAttribute, inputVariables.iterator().next());

		rules.appendChild(data);
	}

	private String determineTransformationOutputVariable(final Component transformationComponent) {

		if (transformationComponent == null) {

			MorphScriptBuilder.LOG.error("transformation component is null, couldn't identify transformation output variable identifier");

			return null;
		}

		final Map<String, String> parameterMappings = transformationComponent.getParameterMappings();

		if (parameterMappings == null) {

			MorphScriptBuilder.LOG
					.error("transformation component parameter mappings are null, couldn't identify transformation output variable identifier");

			return null;
		}

		if (parameterMappings.isEmpty()) {

			MorphScriptBuilder.LOG
					.error("transformation component parameter mappings are empty, couldn't identify transformation output variable identifier");

			return null;
		}

		for (final String key : parameterMappings.keySet()) {

			if (key.startsWith(MorphScriptBuilder.OUTPUT_VARIABLE_PREFIX_IDENTIFIER)) {

				// found output variable identifier

				return key;
			}
		}

		MorphScriptBuilder.LOG.error("couldn't find transformation output variable identifier");

		return null;
	}

	private String getFilterExpression(final MappingAttributePathInstance mappingAttributePathInstance) {

		if (mappingAttributePathInstance.getFilter() != null) {

			final String filterExpressionString = mappingAttributePathInstance.getFilter().getExpression();

			if (filterExpressionString != null && !filterExpressionString.isEmpty()) {

				return StringEscapeUtils.unescapeXml(filterExpressionString);
			}
		}

		return null;
	}

	private boolean checkOrdinal(final Integer ordinal) {

		if (ordinal != null && ordinal > 0) {

			return true;
		}

		return false;
	}

	private String addOrdinalFilter(final Integer ordinal, final String variable, final Element rules) {

		final Element occurrenceData = doc.createElement("data");

		occurrenceData.setAttribute("name", "@" + variable);

		final String manipulatedVariable = variable + MorphScriptBuilder.OCCURRENCE_VARIABLE_POSTFIX;

		occurrenceData.setAttribute("source", "@" + manipulatedVariable);

		final Element occurrenceFunction = doc.createElement("occurrence");

		occurrenceFunction.setAttribute("only", String.valueOf(ordinal));

		occurrenceData.appendChild(occurrenceFunction);

		rules.appendChild(occurrenceData);

		return manipulatedVariable;
	}

	private void addFilter(final String filterExpressionString, final String inputAttributePathStringXMLEscaped, final String variable,
			final Map<String, String> filterExpressionMap, final Element rules) {

		final Element combineAsFilter = doc.createElement("combine");
		combineAsFilter.setAttribute("reset", "false");
		combineAsFilter.setAttribute("sameEntity", "true");
		combineAsFilter.setAttribute("name", "@" + variable);
		combineAsFilter.setAttribute("value", "${" + variable + MorphScriptBuilder.FILTER_VARIABLE_POSTFIX + "}");

		for (final Entry<String, String> filter : filterExpressionMap.entrySet()) {

			final Element combineAsFilterData = doc.createElement("data");
			combineAsFilterData.setAttribute("source", StringEscapeUtils.unescapeXml(filter.getKey()));

			final Element combineAsFilterDataFunction = doc.createElement("equals");
			combineAsFilterDataFunction.setAttribute("string", filter.getValue());

			combineAsFilterData.appendChild(combineAsFilterDataFunction);
			combineAsFilter.appendChild(combineAsFilterData);

		}

		final Element combineAsFilterDataOut = doc.createElement("data");
		combineAsFilterDataOut.setAttribute("name", variable + MorphScriptBuilder.FILTER_VARIABLE_POSTFIX);
		combineAsFilterDataOut.setAttribute("source", inputAttributePathStringXMLEscaped);

		combineAsFilter.appendChild(combineAsFilterDataOut);

		rules.appendChild(combineAsFilter);
	}

	private Map<String, String> extractFilterExpressions(final String filterExpressionString) {

		Map<String, String> filterExpressionMap = Maps.newHashMap();

		final ObjectMapper objectMapper = new ObjectMapper();

		if (filterExpressionString != null && !filterExpressionString.isEmpty()) {

			try {

				filterExpressionMap = objectMapper.readValue(filterExpressionString, HashMap.class);
			} catch (final IOException e) {

				MorphScriptBuilder.LOG.debug("something went wrong while deserializing filter expression" + e);
			}
		}

		return filterExpressionMap;
	}
}
