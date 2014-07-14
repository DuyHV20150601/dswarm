package org.dswarm.persistence.model.schema.test;

import org.dswarm.persistence.GuicedTest;
import org.dswarm.persistence.model.schema.Attribute;
import org.dswarm.persistence.model.schema.AttributePath;
import org.dswarm.persistence.model.schema.Clasz;
import org.dswarm.persistence.model.schema.Schema;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaTest extends GuicedTest {

	private static final Logger	LOG				= LoggerFactory.getLogger(SchemaTest.class);

	private final ObjectMapper	objectMapper	= GuicedTest.injector.getInstance(ObjectMapper.class);

	@Test
	public void simpleSchemaTest() {

		// first attribute path

		final String dctermsTitleId = "http://purl.org/dc/terms/title";
		final String dctermsTitleName = "title";

		final Attribute dctermsTitle = createAttribute(dctermsTitleId, dctermsTitleName);

		final String dctermsHasPartId = "http://purl.org/dc/terms/hasPart";
		final String dctermsHasPartName = "hasPart";

		final Attribute dctermsHasPart = createAttribute(dctermsHasPartId, dctermsHasPartName);

		final AttributePath attributePath1 = new AttributePath();
		// attributePath1.setId(UUID.randomUUID().toString());

		attributePath1.addAttribute(dctermsTitle);
		attributePath1.addAttribute(dctermsHasPart);
		attributePath1.addAttribute(dctermsTitle);

		// second attribute path

		final String dctermsCreatorId = "http://purl.org/dc/terms/creator";
		final String dctermsCreatorName = "creator";

		final Attribute dctermsCreator = createAttribute(dctermsCreatorId, dctermsCreatorName);

		final String foafNameId = "http://xmlns.com/foaf/0.1/name";
		final String foafNameName = "name";

		final Attribute foafName = createAttribute(foafNameId, foafNameName);

		final AttributePath attributePath2 = new AttributePath();
		// attributePath2.setId(UUID.randomUUID().toString());

		attributePath2.addAttribute(dctermsCreator);
		attributePath2.addAttribute(foafName);

		// third attribute path

		final String dctermsCreatedId = "http://purl.org/dc/terms/created";
		final String dctermsCreatedName = "created";

		final Attribute dctermsCreated = createAttribute(dctermsCreatedId, dctermsCreatedName);

		final AttributePath attributePath3 = new AttributePath();
		// attributePath3.setId(UUID.randomUUID().toString());

		attributePath3.addAttribute(dctermsCreated);

		// record class

		final String biboDocumentId = "http://purl.org/ontology/bibo/Document";
		final String biboDocumentName = "document";

		final Clasz biboDocument = new Clasz(biboDocumentId, biboDocumentName);

		// schema

		final Schema schema = new Schema();
		// schema.setId(UUID.randomUUID().toString());

		schema.addAttributePath(attributePath1);
		schema.addAttributePath(attributePath2);
		schema.addAttributePath(attributePath3);
		schema.setRecordClass(biboDocument);

		String json = null;

		try {

			json = objectMapper.writeValueAsString(schema);
		} catch (final JsonProcessingException e) {

			e.printStackTrace();
		}

		SchemaTest.LOG.debug("schema json: " + json);
	}

	private Attribute createAttribute(final String id, final String name) {

		final Attribute attribute = new Attribute(id);
		attribute.setName(name);

		Assert.assertNotNull("the attribute id shouldn't be null", attribute.getUri());
		Assert.assertEquals("the attribute ids are not equal", id, attribute.getUri());
		Assert.assertNotNull("the attribute name shouldn't be null", attribute.getName());
		Assert.assertEquals("the attribute names are not equal", name, attribute.getName());

		return attribute;
	}

}
