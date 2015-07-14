/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.livespark.build;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FilenameFilter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.guvnor.common.services.project.model.Project;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.workbench.common.services.datamodeller.core.DataModel;
import org.kie.workbench.common.services.datamodeller.core.DataObject;
import org.kie.workbench.common.services.shared.project.KieProject;
import org.livespark.client.shared.AppReady;
import org.livespark.client.shared.GwtWarBuildService;
import org.livespark.test.BaseIntegrationTest;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.java.nio.file.Path;
import org.uberfire.java.nio.file.attribute.FileTime;

@RunWith( Arquillian.class )
public class BuildAndDeploymentTest extends BaseIntegrationTest {

    private static final String DATA_OBJECT_NAME = "Foobar";
    private static final String PACKAGE = "buildtest";

    private static final Queue<AppReady> observedEvents = new ConcurrentLinkedQueue<AppReady>();

    private static final FilenameFilter deployMarkerFilter = new FilenameFilter() {
        @Override
        public boolean accept( File dir, String name ) {
            return name.endsWith( ".deployed" );
        }
    };

    /*
     * If this method is non-static, it is invoked on a different instance than the one running the tests, regardless of scopes.
     */
    public static void observeAppReadyEvent( @Observes AppReady appReady ) {
        observedEvents.add( appReady );
    }

    @Deployment
    public static WebArchive createDeployment() {
        return BaseIntegrationTest.createLiveSparkDeployment( BuildAndDeploymentTest.class.getSimpleName().toLowerCase() );
    }

    private Project project;


    @Inject
    private GwtWarBuildService buildService;

    @Before
    public void prepareForTest() {
        prepareServiceTest();
        prepareFields();
        removeDoDeployedMarkerFiles();
        prepareDataObject();
    }

    private void removeDoDeployedMarkerFiles() {
        final File deployDir = new File( "target/wildfly-8.1.0.Final/standalone/deployments/" );


        for ( final File deployMarker : deployDir.listFiles( deployMarkerFilter ) ) {
            deployMarker.delete();
        }
    }

    private void prepareDataObject() {
        final org.uberfire.java.nio.file.Path sharedPath = makePath( getSrcMainPackageHelper( project, "/" + PACKAGE + "/client/shared" ), "" );
        final Path dataObjectPath = makePath( sharedPath.toUri().toString(), DATA_OBJECT_NAME + ".java" );
        maybeCreateDataObject( Paths.convert( sharedPath ), DATA_OBJECT_NAME );

        final DataModel dataModel = dataModelerService.loadModel( (KieProject) project );
        final DataObject dataObject = dataModel.getDataObject( PACKAGE + ".client.shared." + DATA_OBJECT_NAME );
        dataObject.addProperty( "strVal", String.class.getCanonicalName() );
        dataObject.addProperty( "intVal", int.class.getCanonicalName() );
        dataObject.addProperty( "dateVal", Date.class.getCanonicalName() );
        dataObject.addProperty( "bigIntVal", BigInteger.class.getCanonicalName() );
        dataObject.addProperty( "byteVal", byte.class.getCanonicalName() );
        dataObject.addProperty( "charVal", char.class.getCanonicalName() );
        dataObject.addProperty( "boolVal", boolean.class.getCanonicalName() );
        dataObject.addProperty( "doubleVal", double.class.getCanonicalName() );
        dataObject.addProperty( "floatVal", float.class.getCanonicalName() );
        dataObject.addProperty( "longVal", long.class.getCanonicalName() );
        dataObject.addProperty( "shortVal", short.class.getCanonicalName() );
        dataObject.addProperty( "bigDecVal", BigDecimal.class.getCanonicalName() );

        final FileTime lastModified = ioService.getLastModifiedTime( dataObjectPath );
        updateDataObject( dataObject, dataObjectPath );

        // Want to make sure the model has been written before we start a test
        runAssertions( new Runnable() {
            @Override
            public void run() {
                assertNotEquals( "Precondition failed: tests require the updated data object to be saved.", lastModified, ioService.getLastModifiedTime( dataObjectPath ) );
            }
        }, 20, 1000 );
    }

    private void prepareFields() {
        observedEvents.clear();
        project = getProject();
    }

    @Test
    public void testProductionCompileAndDeploymentFiresAppReadyEvent() throws Exception {
        assertEquals( "Precondition failed: There should be no observed AppReady events before building.", 0, observedEvents.size() );

        buildService.buildAndDeploy( project );

        runAssertions( new Runnable() {
            @Override
            public void run() {
                /*
                 * FIXME Should check that exactly one event is fired. Currently file change observers are not cleaned up so multiple events may be fired.
                 */
                assertNotEquals( "There should be exactly one AppReady event observed.", 0, observedEvents.size() );
            }
        }, 60, 2000, 5000 );
    }

    @Test
    public void testDevModeDeploymentFiresAppReadyEvent() throws Exception {
        assertEquals( "Precondition failed: There should be no observed AppReady events before building.", 0, observedEvents.size() );

        buildService.buildAndDeployDevMode( project );

        runAssertions( new Runnable() {
            @Override
            public void run() {
                /*
                 * FIXME Should check that exactly one event is fired. Currently file change observers are not cleaned up so multiple events may be fired.
                 */
                assertNotEquals( "There should be exactly one AppReady event observed.", 0, observedEvents.size() );
            }
        }, 60, 2000, 5000 );
    }

}