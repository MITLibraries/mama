# mama - Metadata 'Ask-Me-Anything' microservice for DSpace repositories #

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

If no items matched the query, an empty list would be returned:

    {
        "field": "dc.identifier.uri",
        "value": "http://hdl.handle.net/123456789/3",
        "items": []
    }

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
be available (probably any version 1.4 or later), but the service will not share the DSpace deployment environment
(JVM, maven, etc), so DSpace can run an older JVM.  

## Deployment ##

The server is implemented in a Java framework that utilizes an integrated, embedded web server (Jetty),
so no Tomcat (or other container) is needed. Moreover, the code builds to a so-called 'fat' jar,
which includes all the dependencies, so the service is started with this simple invocation:

    java -jar mama-all-<version>.jar

There are no configuration files to edit, but the service does need to be able to connect to the database
owned by the DSpace instance. Connection information must be passed in the following environment variables:

    MAMA_DB_URL=jdbc:postgresql://localhost/dspace
    MAMA_DB_USER=dspace
    MAMA_DB_PASSWD=imasecret

That's all you need to operate.
