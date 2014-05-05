package de.avgl.dmp.controller.resources.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.net.HttpHeaders;
import com.google.inject.servlet.RequestScoped;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

@RequestScoped
@Api(value = "/_stats", description = "Get statistics about the server.")
@Path("_stats")
public class MetricsResource {

	private final MetricRegistry	registry;
	private final ObjectMapper		mapper;

	@Inject
	public MetricsResource(final MetricRegistry registry) {

		this.registry = registry;

		final TimeUnit rateUnit = TimeUnit.SECONDS;
		final TimeUnit durationUnit = TimeUnit.MILLISECONDS;
		final boolean showSamples = false;

		final MetricsModule metricsModule = new MetricsModule(rateUnit, durationUnit, showSamples);

		mapper = new ObjectMapper().registerModule(metricsModule);
	}

	@ApiOperation("get a bunch of metrics and gauges and timers since the last server restart. rates are in per-second, durations in milliseconds.")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getStats(
			@ApiParam(value = "json pretty print", required = false, defaultValue = "false", name = "pretty") @DefaultValue("false") @QueryParam("pretty") final boolean pretty)
			throws IOException {

		final ByteArrayOutputStream stream = new ByteArrayOutputStream(1024);

		final ObjectWriter writer = pretty ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();

		writer.writeValue(stream, registry);

		return Response.ok(stream.toString("UTF-8")).header(HttpHeaders.CACHE_CONTROL, "must-revalidate,no-cache,no-store").build();
	}

}
