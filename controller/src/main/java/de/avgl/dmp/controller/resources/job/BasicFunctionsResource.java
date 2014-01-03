package de.avgl.dmp.controller.resources.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import de.avgl.dmp.controller.resources.ExtendedBasicDMPResource;
import de.avgl.dmp.controller.status.DMPStatus;
import de.avgl.dmp.persistence.model.job.Function;
import de.avgl.dmp.persistence.service.job.BasicFunctionService;

/**
 * A generic resource (controller service) for {@link Function}s.
 * 
 * @author tgaengler
 * @param <POJOCLASSPERSISTENCESERVICE> the concrete {@link Function} persistence service of the resource that is related to the
 *            concrete {@link Function} class
 * @param <POJOCLASS> the concrete {@link Function} class
 */
public abstract class BasicFunctionsResource<POJOCLASSPERSISTENCESERVICE extends BasicFunctionService<POJOCLASS>, POJOCLASS extends Function> extends
		ExtendedBasicDMPResource<POJOCLASSPERSISTENCESERVICE, POJOCLASS> {

	/**
	 * Creates a new resource (controller service) for the given concrete {@link Function} class with the provider of the concrete
	 * {@link Function} persistence service, the object mapper and metrics registry.
	 * 
	 * @param clasz a concrete {@link Function} class
	 * @param persistenceServiceProviderArg the concrete persistence service that is related to the concrete {@link Function}
	 *            class
	 * @param objectMapperArg an object mapper
	 * @param dmpStatusArg a metrics registry
	 */
	public BasicFunctionsResource(final Class<POJOCLASS> clasz, final Provider<POJOCLASSPERSISTENCESERVICE> persistenceServiceProviderArg,
			final ObjectMapper objectMapper, final DMPStatus dmpStatus) {

		super(clasz, persistenceServiceProviderArg, objectMapper, dmpStatus);
	}

	/**
	 * {@inheritDoc}<br/>
	 * Updates the name, description, parameters and machine processable function description of the function.
	 */
	@Override
	protected POJOCLASS prepareObjectForUpdate(final POJOCLASS objectFromJSON, final POJOCLASS object) {

		super.prepareObjectForUpdate(objectFromJSON, object);

		object.setFunctionDescription(objectFromJSON.getFunctionDescription());
		object.setParameters(objectFromJSON.getParameters());

		return object;
	}
}
