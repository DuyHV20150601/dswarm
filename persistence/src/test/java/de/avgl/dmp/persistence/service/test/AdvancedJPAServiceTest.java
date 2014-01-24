package de.avgl.dmp.persistence.service.test;

import org.junit.Assert;

import de.avgl.dmp.persistence.DMPPersistenceException;
import de.avgl.dmp.persistence.model.AdvancedDMPJPAObject;
import de.avgl.dmp.persistence.model.proxy.ProxyAdvancedDMPJPAObject;
import de.avgl.dmp.persistence.service.AdvancedDMPJPAService;

public abstract class AdvancedJPAServiceTest<PROXYPOJOCLASS extends ProxyAdvancedDMPJPAObject<POJOCLASS>, POJOCLASS extends AdvancedDMPJPAObject, JPASERVICEIMPL extends AdvancedDMPJPAService<PROXYPOJOCLASS, POJOCLASS>>
		extends IDBasicJPAServiceTest<PROXYPOJOCLASS, POJOCLASS, JPASERVICEIMPL> {

	private static final org.apache.log4j.Logger	LOG	= org.apache.log4j.Logger.getLogger(AdvancedJPAServiceTest.class);

	public AdvancedJPAServiceTest(final String type, final Class<JPASERVICEIMPL> jpaServiceClass) {

		super(type, jpaServiceClass);
	}

	protected POJOCLASS createObject(final String id) {

		POJOCLASS object = null;

		try {

			object = jpaService.createObjectTransactional(id);
		} catch (final DMPPersistenceException e) {

			Assert.assertTrue("something went wrong during object creation.\n" + e.getMessage(), false);
		}

		Assert.assertNotNull(type + " shouldn't be null", object);
		Assert.assertNotNull(type + " id shouldn't be null", object.getId());

		LOG.debug("created new " + type + " with id = '" + object.getId() + "'");

		getObject(object);

		return object;
	}
}
