package de.avgl.dmp.controller.guice;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.common.io.Resources;
import com.google.inject.name.Names;
import com.google.inject.persist.PersistFilter;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.servlet.ServletModule;

import de.avgl.dmp.controller.doc.SwaggerConfig;
import de.avgl.dmp.controller.servlet.HeartbeatServlet;
import de.avgl.dmp.controller.servlet.MetricsServlet;
import de.avgl.dmp.controller.servlet.filter.MetricsFilter;

public class DMPServletModule extends ServletModule {

	private static final org.apache.log4j.Logger	LOG	= org.apache.log4j.Logger.getLogger(DMPServletModule.class);

	@Override
	protected void configureServlets() {

		install(new JpaPersistModule("DMPApp"));

		filter("/*").through(PersistFilter.class);

		bind(TimeUnit.class).annotatedWith(Names.named("RateUnit")).toInstance(TimeUnit.SECONDS);
		bind(TimeUnit.class).annotatedWith(Names.named("DurationUnit")).toInstance(TimeUnit.MILLISECONDS);
		bind(String.class).annotatedWith(Names.named("AllowedOrigin")).toInstance("*");
		bind(Boolean.class).annotatedWith(Names.named("ShowSamples")).toInstance(false);

		bind(String.class).annotatedWith(Names.named("ApiVersion")).toInstance("1.0.1");

		final Properties properties = loadProperties();

		bind(String.class).annotatedWith(Names.named("ApiBaseUrl")).toInstance(properties.getProperty("swagger.base_url", "http://localhost:8087/dmp"));

		serve("/_stats").with(MetricsServlet.class);
		serve("/_ping").with(HeartbeatServlet.class);

		serve("/api-docs").with(SwaggerConfig.class);

		filter("/*").through(MetricsFilter.class);
	}

	protected Properties loadProperties() {
		return loadProperties("dmp.properties");
	}

	protected Properties loadProperties(final String fileName) {
		final URL resource = Resources.getResource(fileName);
		final Properties properties = new Properties();
		try {
			properties.load(resource.openStream());
		} catch (final IOException e) {
			LOG.error("Could not load dmp.properties", e);
		}

		return properties;
	}
}


