/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

package org.livespark.backend.server;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Named;
import org.guvnor.ala.build.maven.config.MavenBuildConfig;
import org.guvnor.ala.build.maven.config.MavenBuildExecConfig;
import org.guvnor.ala.build.maven.config.MavenProjectConfig;
import org.guvnor.ala.build.maven.config.gwt.GWTCodeServerMavenExecConfig;
import org.guvnor.ala.config.BinaryConfig;
import org.guvnor.ala.config.BuildConfig;
import org.guvnor.ala.config.ProjectConfig;
import org.guvnor.ala.config.ProviderConfig;
import org.guvnor.ala.config.RuntimeConfig;
import org.guvnor.ala.config.SourceConfig;
import org.guvnor.ala.pipeline.Input;
import org.guvnor.ala.pipeline.Pipeline;
import org.guvnor.ala.pipeline.PipelineFactory;
import org.guvnor.ala.pipeline.Stage;
import static org.guvnor.ala.pipeline.StageUtil.config;
import org.guvnor.ala.registry.PipelineRegistry;
import org.guvnor.ala.registry.RuntimeRegistry;
import org.guvnor.ala.source.git.config.GitConfig;
import org.guvnor.ala.wildfly.config.WildflyProviderConfig;
import org.guvnor.ala.wildfly.config.impl.ContextAwareWildflyRuntimeExecConfig;

import org.guvnor.structure.organizationalunit.OrganizationalUnitService;
import org.guvnor.structure.repositories.Repository;
import org.guvnor.structure.repositories.RepositoryService;
import org.guvnor.structure.server.config.ConfigGroup;
import org.guvnor.structure.server.config.ConfigType;
import org.guvnor.structure.server.config.ConfigurationFactory;
import org.guvnor.structure.server.config.ConfigurationService;
import org.kie.workbench.common.services.shared.project.KieProjectService;
import org.kie.workbench.screens.workbench.backend.BaseAppSetup;
import org.uberfire.commons.services.cdi.ApplicationStarted;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;
import org.uberfire.io.IOService;

//This is a temporary solution when running in PROD-MODE as /webapp/.niogit/system.git folder
//is not deployed to the Application Servers /bin folder. This will be remedied when an
//installer is written to create the system.git repository in the correct location.
@Startup(StartupType.BOOTSTRAP)
@ApplicationScoped
public class AppSetup extends BaseAppSetup {

    // default repository section - start
    private static final String OU_NAME = "demo";
    private static final String OU_OWNER = "demo@demo.org";
    // default repository section - end

    private Event<ApplicationStarted> applicationStartedEvent;
    
    private RuntimeRegistry runtimeRegistry;
    
    private PipelineRegistry pipelineRegistry;
    

    protected AppSetup() {
    }

    @Inject
    public AppSetup( @Named("ioStrategy") final IOService ioService,
                     final RepositoryService repositoryService,
                     final OrganizationalUnitService organizationalUnitService,
                     final KieProjectService projectService,
                     final ConfigurationService configurationService,
                     final ConfigurationFactory configurationFactory,
                     final Event<ApplicationStarted> applicationStartedEvent, 
                     final RuntimeRegistry runtimeRegistry, 
                     final PipelineRegistry pipelineRegistry ) {
        super( ioService, repositoryService, organizationalUnitService, projectService, configurationService, configurationFactory );
        this.applicationStartedEvent = applicationStartedEvent;
        this.runtimeRegistry = runtimeRegistry;
        this.pipelineRegistry = pipelineRegistry;
        
    }

    
    
    @PostConstruct
    public void init() {
        try {
            configurationService.startBatch();
            final String exampleRepositoriesRoot = System.getProperty( "org.kie.example.repositories" );
            if ( !( exampleRepositoriesRoot == null || "".equalsIgnoreCase( exampleRepositoriesRoot ) ) ) {
                loadExampleRepositories( exampleRepositoriesRoot,
                                         OU_NAME,
                                         OU_OWNER,
                                         GIT_SCHEME );

            } else if ( "true".equalsIgnoreCase( System.getProperty( "org.kie.example" ) ) ) {

                Repository exampleRepo = createRepository( "repository1",
                                                           "git",
                                                           null,
                                                           "",
                                                           "" );
                createOU( exampleRepo,
                          "example",
                          "" );
                createProject( exampleRepo,
                               "org.kie.example",
                               "project1",
                               "1.0.0-SNAPSHOT" );
            }

            //Define mandatory properties
            setupConfigurationGroup( ConfigType.GLOBAL,
                                     GLOBAL_SETTINGS,
                                     getGlobalConfiguration() );

            // notify components that bootstrap is completed to start post setups
            applicationStartedEvent.fire( new ApplicationStarted() );
        } catch ( final Exception e ) {
            logger.error( "Error during update config", e );
            throw new RuntimeException( e );
        } finally {
            configurationService.endBatch();
        }
        initPipelines();
    }
    
    public void initPipelines(){
        // Create Wildfly Pipeline Configuration
        Stage<Input, SourceConfig> sourceConfig = config( "Git Source", (s) -> new GitConfig() {} );
        Stage<SourceConfig, ProjectConfig> projectConfig = config( "Maven Project", (s) -> new MavenProjectConfig() {} );
         Stage<ProjectConfig, BuildConfig> buildConfig = config( "Maven Build Config", (s) -> new MavenBuildConfig() {
            @Override
            public List<String> getGoals() {
                final List<String> result = new ArrayList<>();
                result.add( "package" );
                result.add( "-DfailIfNoTests=false" );
                return result;
            }

        } );
        Stage<ProjectConfig, BuildConfig> buildSDMConfig = config( "Maven Build Config", (s) -> new MavenBuildConfig() {
            @Override
            public List<String> getGoals() {
                final List<String> result = new ArrayList<>();
                result.add( "package" );
                result.add( "-DfailIfNoTests=false" );
                result.add( "-Dgwt.compiler.skip=true" );
                return result;
            }

        } );
        Stage<BuildConfig, BuildConfig> codeServerExec = config( "Start Code Server", (s) -> new GWTCodeServerMavenExecConfig() {} );
        Stage<BuildConfig, BinaryConfig> buildExec = config( "Maven Build", (s) -> new MavenBuildExecConfig() {} );
        Stage<BinaryConfig, ProviderConfig> providerConfig = config( "Wildfly Provider Config", (s) -> new WildflyProviderConfig() {} );
        Stage<ProviderConfig, RuntimeConfig> runtimeExec = config( "Wildfly Runtime Exec", (s) -> new ContextAwareWildflyRuntimeExecConfig() );
        
        Pipeline wildflyPipeline = PipelineFactory
                .startFrom( sourceConfig )
                .andThen( projectConfig )
                .andThen( buildConfig )
                .andThen( buildExec )
                .andThen( providerConfig )
                .andThen( runtimeExec ).buildAs( "wildfly pipeline" );
        //Registering the Wildfly Pipeline to be available to the whole workbench
        pipelineRegistry.registerPipeline(wildflyPipeline);
        
        Pipeline wildflySDMPipeline = PipelineFactory
                .startFrom( sourceConfig )
                .andThen( projectConfig )
                .andThen( buildSDMConfig )
                .andThen( codeServerExec )
                .andThen( buildExec )
                .andThen( providerConfig )
                .andThen( runtimeExec ).buildAs( "wildfly sdm pipeline" );
        //Registering the Wildfly Pipeline to be available to the whole workbench
        pipelineRegistry.registerPipeline(wildflySDMPipeline);
        
        
//        sourceConfig = config( "Git Source", (s) -> new GitConfigImpl() );
//        projectConfig = config( "Maven Project", (s) -> new MavenProjectConfigImpl() );
//        buildConfig = config( "Maven Build Config", (s) -> new MavenBuildConfigImpl() );
//        Stage<BuildConfig, BuildConfig> dockerBuildConfig = config( "Docker Build Config", (s) -> new DockerBuildConfigImpl() );
//        buildExec = config( "Maven Build", (s) -> new MavenBuildExecConfigImpl() );
//        providerConfig = config( "Docker Provider Config", (s) -> new DockerProviderConfigImpl() );
//        Stage<ProviderConfig, ProvisioningConfig> runtimeConfig = config( "Docker Runtime Config", (s) -> new ContextAwareDockerProvisioningConfig() );
//        Stage<ProvisioningConfig, RuntimeConfig> dockerRuntimeExec = config( "Docker Runtime Exec", (s) -> new ContextAwareDockerRuntimeExecConfig() );
//
//        final Pipeline dockerPipeline = PipelineFactory
//                .startFrom( sourceConfig )
//                .andThen( projectConfig )
//                .andThen( buildConfig )
//                .andThen( dockerBuildConfig )
//                .andThen( buildExec )
//                .andThen( providerConfig )
//                .andThen( runtimeConfig )
//                .andThen( dockerRuntimeExec ).buildAs( "docker pipeline" );
//        //Registering the Docker Pipeline to be available to the whole workbench
//        pipelineRegistry.registerPipeline(dockerPipeline);
    }

    private ConfigGroup getGlobalConfiguration() {
        //Global Configurations used by many of Drools Workbench editors
        final ConfigGroup group = configurationFactory.newConfigGroup( ConfigType.GLOBAL,
                                                                       GLOBAL_SETTINGS,
                                                                       "" );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.dateformat",
                                                                 "dd-MMM-yyyy" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.datetimeformat",
                                                                 "dd-MMM-yyyy hh:mm:ss" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.defaultlanguage",
                                                                 "en" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.defaultcountry",
                                                                 "US" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "build.enable-incremental",
                                                                 "true" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "rule-modeller-onlyShowDSLStatements",
                                                                 "false" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "support.runtime.deploy",
                                                                 "false" ) );
        return group;
    }
}
