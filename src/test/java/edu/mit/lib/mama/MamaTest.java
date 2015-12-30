/**
 * Copyright (C) 2016 MIT Libraries
 * Licensed under: http://www.apache.org/licenses/LICENSE-2.0
 */
package edu.mit.lib.mama;

import java.io.IOException;
import java.util.EnumSet;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.SimpleReportAggregator;
import guru.nidi.ramltester.core.Usage;
import guru.nidi.ramltester.core.UsageItem;
import guru.nidi.ramltester.httpcomponents.RamlHttpClient;

import static guru.nidi.ramltester.junit.RamlMatchers.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import org.h2.jdbcx.JdbcConnectionPool;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import edu.mit.lib.mama.Mama;
/**
 * MamaTest verifies the service implementation against the API definition
 * contained in a RAML file. An in-memory database is populated with sample
 * data, and the API service launched with it. Over-the-wire calls to the
 * service are compared to the contract specified by the RAML file.
 *
 * NB: a RAML 0.8 version of the file is used here until ramltester supports 1.0
 *
 * @author richardrodgers
 */
public class MamaTest {
    private static final String TEST_DB_URL = "jdbc:h2:mem:mama";
    private static final String TEST_SVC_URL = "http://localhost:4567";
    private static DBI database;
    private static RamlDefinition api = RamlLoaders.fromClasspath().load("mama8.raml");
    private static RamlHttpClient baseClient = api.createHttpClient();
    private static Thread apiService =
        new Thread(() -> Mama.main(new String[] {TEST_DB_URL, "username", "password"}));

    @BeforeClass
    public static void setupService() throws Exception {
        // Create and initialize the in-memory test DB
        database = new DBI(JdbcConnectionPool.create(TEST_DB_URL, "username", "password"));
        try (Handle hdl = database.open()) {
           hdl.execute("create table metadataschemaregistry (metadata_schema_id int primary key, short_id varchar)");
           hdl.execute("insert into metadataschemaregistry (metadata_schema_id, short_id) values(1, 'dc')");
           hdl.execute("create table metadatafieldregistry (metadata_field_id int primary key, metadata_schema_id int, element varchar, qualifier varchar)");
           hdl.execute("insert into metadatafieldregistry (metadata_field_id, metadata_schema_id, element, qualifier) values(1, 1, 'identifier', 'uri')");
           hdl.execute("insert into metadatafieldregistry (metadata_field_id, metadata_schema_id, element) values(2, 1, 'title')");
           hdl.execute("insert into metadatafieldregistry (metadata_field_id, metadata_schema_id, element) values(3, 1, 'type')");
           hdl.execute("insert into metadatafieldregistry (metadata_field_id, metadata_schema_id, element) values(4, 1, 'creator')");
           hdl.execute("create table item (item_id int primary key)");
           hdl.execute("insert into item (item_id) values(1)");
           hdl.execute("insert into item (item_id) values(2)");
           hdl.execute("create table metadatavalue (metadata_value_id int primary key, item_id int, metadata_field_id int, text_value varchar)");
           hdl.execute("insert into metadatavalue (metadata_value_id, item_id, metadata_field_id, text_value) values(1, 1, 1, 'http://hdl.handle.net/123456789/3')");
           hdl.execute("insert into metadatavalue (metadata_value_id, item_id, metadata_field_id, text_value) values(2, 2, 2, 'A Very Important Study')");
        }
        // launch api service in it's own thread - then give it time to initialize before firing tests at it
        apiService.start();
        Thread.currentThread().sleep(2000);
    }

    @Test
    public void apiSpecValidity() throws IOException {
        assertThat(api.validate(), validates());
    }

    @Test
    public void basicRequest() throws IOException {
        String url = TEST_SVC_URL + "/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/3";
        HttpResponse response = baseClient.execute(new HttpGet(url));
        assertThat(baseClient.getLastReport(), checks());
    }

    @Test
    public void usageSuite() throws IOException {
        final SimpleReportAggregator aggregator = new SimpleReportAggregator();
        final RamlHttpClient client = baseClient.aggregating(aggregator);

        // URLs must collectively trigger full set of API behaviors as described by spec
        // missing required query parameter 'qf' - should return 400 response code
        send(client, TEST_SVC_URL + "/item?qv=http://hdl.handle.net/123456789/3");
        // required query parameter 'qf' lacking value - should return 400 response code
        send(client, TEST_SVC_URL + "/item?qf=&qv=http://hdl.handle.net/123456789/3");
        // required query parameter 'qf' bogus value - should return 404 response code
        send(client, TEST_SVC_URL + "/item?qf=dc.identifier.foo&qv=http://hdl.handle.net/123456789/3");
        // no items matching query - should return 404 response code
        send(client, TEST_SVC_URL + "/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/5");
        // correct query - no response parameters
        send(client, TEST_SVC_URL + "/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/3");
        // correct query - optional response parameters
        send(client, TEST_SVC_URL + "/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/3&rf=dc.title&rf=dc.type");

        assertUsage(aggregator.getUsage(), EnumSet.allOf(UsageItem.class));
    }

    private void send(RamlHttpClient client, String request) throws IOException {
        final HttpGet get = new HttpGet(request);
        //log.info("Send:        " + request);
        final HttpResponse response = client.execute(get);
        //log.info("Result:      " + response.getStatusLine() + (response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity())));
        //log.info("Raml report: " + client.getLastReport());
    }

    private void assertUsage(Usage usage, EnumSet<UsageItem> usageItems) {
        for (UsageItem usageItem : usageItems) {
            assertEquals(usageItem.name(), 0, usageItem.get(usage).size());
        }
    }
}
