/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at http://www.eclipse.org/legal/epl-v10.html
 */
package io.liveoak.filesystem.aggregating;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.liveoak.common.codec.DefaultResourceState;
import io.liveoak.filesystem.aggregating.extension.AggregatingFilesystemExtension;
import io.liveoak.spi.resource.RootResource;
import io.liveoak.spi.resource.async.Resource;
import io.liveoak.spi.state.ResourceState;
import io.liveoak.testtools.AbstractHTTPResourceTestCase;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author <a href="mailto:marko.strukelj@gmail.com">Marko Strukelj</a>
 */
public class HTTPAggregatingFilesystemResourceTest extends AbstractHTTPResourceTestCase {


    @Override
    public void loadExtensions() throws Exception {
        loadExtension( "aggr-fs", new AggregatingFilesystemExtension() );
        installResource( "aggr-fs", "aggr", JsonNodeFactory.instance.objectNode() );
    }

    @Override
    protected File applicationDirectory() {
        return this.projectRoot;
    }

    @Before
    public void before() {
        File dataDir = new File( this.projectRoot, "aggr" );

        dataDir.mkdirs();

        // create some files in there
        try {
            FileWriter out = new FileWriter(new File(dataDir, "aggregate.js.aggr"));
            out.write("require first.js\n");
            out.write("require second.js\n");
            out.close();

            out = new FileWriter(new File(dataDir, "first.js"));
            out.write("// first.js\n");
            out.close();

            out = new FileWriter(new File(dataDir, "second.js"));
            out.write("// second.js\n");
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create a test file: ", e);
        }
    }

    @After
    public void after() {
        File dataDir = new File( this.projectRoot, "aggr" );
        new File(dataDir, "aggregate.js.aggr").delete();
        new File(dataDir, "first.js").delete();
        new File(dataDir, "second.js").delete();
    }

    @Test
    public void testReadAggregate() throws Exception {

        HttpGet get = new HttpGet("http://localhost:8080/testApp/aggr/aggregate.js");
        get.addHeader("Accept", "*/*");

        try {
            System.err.println("DO GET");
            CloseableHttpResponse result = httpClient.execute(get);
            System.err.println("=============>>>");
            System.err.println(result);

            HttpEntity entity = result.getEntity();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (entity.getContentLength() > 0) {
                entity.writeTo(out);
            }
            String content = new String(out.toByteArray());
            System.err.println(content);
            System.err.println("\n<<<=============");

            assertThat(result.getStatusLine().getStatusCode()).isEqualTo(200);
            assertThat(content).isEqualTo("// first.js\n// second.js\n");

        } finally {
            httpClient.close();
        }
    }
}
