# mama - Metadata 'Ask-Me-Anything' microservice for DSpace repositories #

[![Build Status](https://travis-ci.org/MITLibraries/mama.svg?branch=master)]
(https://travis-ci.org/MITLibraries/mama)

mama is an API server for a very simple metadata query service. The service exposes a single endpoint to which the
client passes (in query parameters) a metadata field name (e.g. _dc.identifier.uri_), and a value
(_http://hdl.handle.net/123456789/2_). If any DSpace items have metadata values matching the query, they
are returned, JSON-encoded, in a list. The items are described using one or more optional metadata field names,
again passed as query parameters. If no response field names are enumerated, the _default_ item description consists
merely of the 'dc.identitifer.uri' (DSpace handle).

For example, a client looking for the title of an item with a particular handle would issue the GET request:

    http://mama.my.edu/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/3&rf=dc.title

If an item with that handle existed in the DSpace instance, the service would reply with:

    {
        "field": "dc.identifier.uri",
        "value": "http://hdl.handle.net/123456789/3",
        "items": [
           {
             "dc.title": "A Very Important Study"
           }
        ]
    }

If no items matched the query, a 404 response would be returned.
If more display fields were requested, they would be included with each item object:

    http://mama.my.edu/item?qf=dc.identifier.uri&qv=http://hdl.handle.net/123456789/3&rf=dc.title&rf=dc.creator

    {
        "field": "dc.identifier.uri",
        "value": "http://hdl.handle.net/123456789/3",
        "items": [
            {
                "dc.title": "A Very Important Study",
                "dc.creator": "Author, First",
                "dc.creator": "Author, Another"
            }
        ]
    }

## Requirements ##

The service needs to compile and run on a Java8 VM, using Gradle as the build tool. A DSpace instance must
be available (probably any version 1.4 or later, but before the 'metadata for all' versions), but the service
will not share the DSpace deployment environment (JVM, maven, etc), so DSpace can run an older JVM.  

## Deployment ##

The server is implemented in a Java framework that utilizes an integrated, embedded web server (Jetty),
so no Tomcat (or other container) is needed. Moreover, the code can be built to a so-called 'fat' jar:

    gradle fatJar

which includes all the dependencies, so the service could be started with this simple invocation:

    java -jar mama-all-<version>.jar

There are no configuration files to edit, but the service does need to be able to connect to the database
owned by the DSpace instance. Connection information must be passed in the following environment variables:

    MAMA_DB_URL=jdbc:postgresql://localhost/dspace
    MAMA_DB_USER=dspace
    MAMA_DB_PASSWD=imasecret

By default, the service will listen on port 4567. To change this, just set another environment variable:

    MAMA_SVC_PORT=8080

That's all you need to run the service.

## Operation and Management ##

The service provides a number of affordances for reliable, performant and scalable operation.
It exposes an endpoint for application monitoring tools such as Nagios:

    http://mama.my.edu/ping

Ping will always return a 200 'pong' reply if the service is up. Mama also integrates with the external exception
monitoring service Honeybadger <https://www.honeybadger.io>. This service will capture all application exceptions
(including the full stack trace) to a remote server, and contact you or your designates for timely remediation.
Simply add an environment variable with the service-assigned API key:

    HONEYBADGER_API_KEY=34dgkd

The service also gathers internal performance metrics and exposes them via the 'metrics' endpoint. By default,
the data accumulated includes the number of API requests and average response time (returned in JSON-formatted reports),
but you can (likely only in test-mode) also capture fine-grained DB query timings. Simply define and expose the
'MAMA_DB_METRICS' environment variable to any value.

Finally, you can enable an endpoint for remote service shutdown. All you need to do is set the environment variable:

    MAMA_SHUTDOWN_KEY=solong

(you should likely select a less guessable value). If this variable is defined, the service can be remotely shut down
by requesting the URL:

    http://mama.my.edu/shutdown?key=solong

Best practice is periodically to rotate the key, etc. Remote shutdown is entirely optional.
