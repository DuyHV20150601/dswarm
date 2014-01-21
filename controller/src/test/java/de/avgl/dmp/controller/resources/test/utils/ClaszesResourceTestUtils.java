package de.avgl.dmp.controller.resources.test.utils;

import de.avgl.dmp.persistence.model.schema.Clasz;
import de.avgl.dmp.persistence.service.schema.ClaszService;

public class ClaszesResourceTestUtils extends AdvancedDMPResourceTestUtils<ClaszService, Clasz> {

	public ClaszesResourceTestUtils() {

		super("classes", Clasz.class, ClaszService.class);
	}

	@Override
	public void reset() {
		
	}
}
