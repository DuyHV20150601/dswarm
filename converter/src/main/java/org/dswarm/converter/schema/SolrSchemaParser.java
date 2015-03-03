/**
 * Copyright (C) 2013 – 2015 SLUB Dresden & Avantgarde Labs GmbH (<code@dswarm.org>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dswarm.converter.schema;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.dswarm.persistence.DMPPersistenceException;
import org.dswarm.persistence.model.schema.Attribute;
import org.dswarm.persistence.model.schema.AttributePath;
import org.dswarm.persistence.model.schema.Schema;
import org.dswarm.persistence.model.schema.SchemaAttributePathInstance;
import org.dswarm.persistence.model.schema.proxy.ProxyAttribute;
import org.dswarm.persistence.model.schema.proxy.ProxyAttributePath;
import org.dswarm.persistence.model.schema.proxy.ProxySchema;
import org.dswarm.persistence.model.schema.utils.SchemaUtils;
import org.dswarm.persistence.service.UUIDService;
import org.dswarm.persistence.service.schema.AttributePathService;
import org.dswarm.persistence.service.schema.AttributeService;
import org.dswarm.persistence.service.schema.ClaszService;
import org.dswarm.persistence.service.schema.SchemaService;

/**
 * Transforms a given Solr schema file (schema.xml) to a d:swarm schema.
 *
 * @author tgaengler
 */
public class SolrSchemaParser {

	private static final Logger LOG = LoggerFactory.getLogger(SolrSchemaParser.class);

	private final Provider<SchemaService> schemaServiceProvider;

	private final Provider<ClaszService> classServiceProvider;

	private final Provider<AttributePathService> attributePathServiceProvider;

	private final Provider<AttributeService> attributeServiceProvider;

	private static final String SCHEMA_IDENTIFIER               = "schema";
	private static final String FIELDS_IDENTIFIER               = "fields";
	private static final String FIELD_IDENTIFIER                = "field";
	private static final String FIELDS_XPATH_EXPRESSION         =
			SchemaUtils.SLASH + SCHEMA_IDENTIFIER + SchemaUtils.SLASH + FIELDS_IDENTIFIER + SchemaUtils.SLASH + FIELD_IDENTIFIER;
	private static final String NAME_IDENTIFIER                 = "name";
	private static final String DEFAULT_RECORD_CLASS_LOCAL_NAME = "RecordType";

	private static final DocumentBuilderFactory documentBuilderFactory;
	private static final XPathFactory           xPathfactory;

	static {

		documentBuilderFactory = DocumentBuilderFactory.newInstance();
		xPathfactory = XPathFactory.newInstance();
	}

	@Inject
	public SolrSchemaParser(final Provider<SchemaService> schemaServiceProviderArg,
			final Provider<ClaszService> classServiceProviderArg, final Provider<AttributePathService> attributePathServiceProviderArg,
			final Provider<AttributeService> attributeServiceProviderArg) {

		schemaServiceProvider = schemaServiceProviderArg;
		classServiceProvider = classServiceProviderArg;
		attributePathServiceProvider = attributePathServiceProviderArg;
		attributeServiceProvider = attributeServiceProviderArg;
	}

	public Optional<Schema> parse(final String solrSchemaFilePath, final String schemaUUID, final String schemaName)
			throws DMPPersistenceException {

		final Optional<Document> optionalDocument = readXML(solrSchemaFilePath);

		if (!optionalDocument.isPresent()) {

			LOG.error("parsed Solr schema (from '{}') is not present", solrSchemaFilePath);

			return Optional.empty();
		}

		final Document document = optionalDocument.get();

		final Optional<NodeList> optionalFields = getFields(document, solrSchemaFilePath);

		if (!optionalFields.isPresent()) {

			LOG.error("couldn't find fields in the Solr schema (from '{}')", solrSchemaFilePath);

			return Optional.empty();
		}

		final Schema schema = createSchema(schemaUUID, schemaName);
		final String schemaBaseURI = SchemaUtils.determineSchemaNamespaceURI(schema.getUuid());

		final NodeList fields = optionalFields.get();
		final List<Attribute> attributes = determineAndCreateAttributes(fields, schemaBaseURI);

		if (attributes.isEmpty()) {

			LOG.error("could not extract any attribute from the Solr schema at '{}'", solrSchemaFilePath);

			return Optional.empty();
		}

		// attribute paths
		final List<AttributePath> attributePaths = createAttributePaths(attributes);

		if (attributePaths.isEmpty()) {

			LOG.error("couldn't create any attribute path from the extracted attributes from the Solr schem at '{}'", solrSchemaFilePath);

			return Optional.empty();
		}

		// schema attribute paths
		for (final AttributePath attributePath : attributePaths) {

			final SchemaAttributePathInstance schemaAttributePathInstance = createSchemaAttributePathInstance(attributePath);

			schema.addAttributePath(schemaAttributePathInstance);
		}

		// record class
		final String recordClassURI = schemaBaseURI + DEFAULT_RECORD_CLASS_LOCAL_NAME;

		SchemaUtils.addRecordClass(schema, recordClassURI, classServiceProvider);

		final Optional<ProxySchema> optionalProxySchema = Optional.ofNullable(schemaServiceProvider.get().createObjectTransactional(schema));

		if (!optionalProxySchema.isPresent()) {

			return Optional.empty();
		}

		return Optional.ofNullable(optionalProxySchema.get().getObject());
	}

	private Optional<Document> readXML(final String solrSchemaFilePath) {

		final DocumentBuilder documentBuilder;

		try {

			documentBuilder = documentBuilderFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {

			LOG.error("couldn't create document builder for parsing the Solr schema file", e);

			return Optional.empty();
		}

		final InputStream inputStream = ClassLoader.getSystemResourceAsStream(solrSchemaFilePath);

		final Document document;

		try {
			document = documentBuilder.parse(inputStream);

			return Optional.ofNullable(document);
		} catch (final SAXException e) {

			LOG.error("couldn't parse the Solr schema file at '{}'", solrSchemaFilePath, e);

			return Optional.empty();
		} catch (final IOException e) {

			LOG.error("couldn't read the Solr schema fila at '{}'", solrSchemaFilePath, e);

			return Optional.empty();
		}
	}

	private Optional<NodeList> getFields(final Document document, final String solrSchemaFilePath) {

		final XPath xpath = xPathfactory.newXPath();

		final XPathExpression expr;

		try {

			expr = xpath.compile(FIELDS_XPATH_EXPRESSION);
		} catch (final XPathExpressionException e) {

			LOG.error("could not compile xpath", e);

			return Optional.empty();
		}

		final NodeList result;

		try {

			result = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
		} catch (final XPathExpressionException e) {

			LOG.error("could not execute xpath query against document (from '{}')", solrSchemaFilePath, e);

			return Optional.empty();
		}

		return Optional.ofNullable(result);
	}

	private List<Attribute> determineAndCreateAttributes(final NodeList fields, final String schemaBaseURI) throws DMPPersistenceException {

		final List<Attribute> attributes = new ArrayList<>();

		// determine and mint attributes
		for (int i = 0; i < fields.getLength(); i++) {

			final Node field = fields.item(i);

			if (!field.hasAttributes()) {

				if (LOG.isDebugEnabled()) {

					LOG.debug("field ('{}') has no attributes, cannot parse an attribute from it", ToStringBuilder.reflectionToString(field));
				}

				continue;
			}

			final NamedNodeMap fieldAttributes = field.getAttributes();

			final Optional<Node> optionalNameNode = Optional.ofNullable(fieldAttributes.getNamedItem(NAME_IDENTIFIER));

			if (!optionalNameNode.isPresent()) {

				if (LOG.isDebugEnabled()) {

					LOG.debug("field ('{}') has no name XML attribute, cannot parse an attribute from it", ToStringBuilder.reflectionToString(field));
				}

				continue;
			}

			final Node nameNode = optionalNameNode.get();

			final Optional<String> optionalName = Optional.ofNullable(nameNode.getNodeValue());

			if (!optionalName.isPresent()) {

				if (LOG.isDebugEnabled()) {

					LOG.debug("field ('{}') has no name, cannot parse an attribute from it", ToStringBuilder.reflectionToString(field));
				}

				continue;
			}

			final Optional<Attribute> optionalAttribute = createAttribute(schemaBaseURI, optionalName.get());

			if (optionalAttribute.isPresent()) {

				attributes.add(optionalAttribute.get());
			}
		}

		return attributes;
	}

	private List<AttributePath> createAttributePaths(final List<Attribute> attributes) throws DMPPersistenceException {

		final List<AttributePath> attributePaths = new ArrayList<>();

		for (final Attribute attribute : attributes) {

			final Optional<AttributePath> optionalAttributePath = createAttributePath(attribute);

			if (optionalAttributePath.isPresent()) {

				attributePaths.add(optionalAttributePath.get());
			}
		}
		return attributePaths;
	}

	private Schema createSchema(final String uuid, final String name) {

		final String finalUUID;

		if (uuid != null && !uuid.trim().isEmpty()) {

			finalUUID = uuid;
		} else {

			finalUUID = UUIDService.getUUID(Schema.class.getSimpleName());
		}

		final Schema schema = new Schema(finalUUID);

		if (name != null && !name.trim().isEmpty()) {

			schema.setName(name);
		}

		return schema;
	}

	private Optional<Attribute> createAttribute(final String schemaBaseURI, final String attributeName) throws DMPPersistenceException {

		final String uuid = UUIDService.getUUID(Attribute.class.getSimpleName());
		final String uri = SchemaUtils.mintAttributeURI(attributeName, schemaBaseURI);

		final Attribute attribute = new Attribute(uuid, uri, attributeName);

		final Optional<ProxyAttribute> optionalProxyAttribute = Optional
				.ofNullable(attributeServiceProvider.get().createObjectTransactional(attribute));

		if (!optionalProxyAttribute.isPresent()) {

			return Optional.empty();
		}

		return Optional.ofNullable(optionalProxyAttribute.get().getObject());
	}

	private Optional<AttributePath> createAttributePath(final Attribute attribute) throws DMPPersistenceException {

		final String uuid = UUIDService.getUUID(AttributePath.class.getSimpleName());

		final AttributePath attributePath = new AttributePath(uuid);
		attributePath.addAttribute(attribute);

		final Optional<ProxyAttributePath> optionalProxyAttributePath = Optional
				.ofNullable(attributePathServiceProvider.get().createObject(attributePath));

		if (!optionalProxyAttributePath.isPresent()) {

			return Optional.empty();
		}

		return Optional.ofNullable(optionalProxyAttributePath.get().getObject());
	}

	private SchemaAttributePathInstance createSchemaAttributePathInstance(final AttributePath attributePath)
			throws DMPPersistenceException {

		final String uuid = UUIDService.getUUID(SchemaAttributePathInstance.class.getSimpleName());

		final SchemaAttributePathInstance schemaAttributePathInstance = new SchemaAttributePathInstance(uuid);
		schemaAttributePathInstance.setAttributePath(attributePath);

		return schemaAttributePathInstance;
	}
}
