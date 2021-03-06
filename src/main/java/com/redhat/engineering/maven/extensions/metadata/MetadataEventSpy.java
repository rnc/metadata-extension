/*
 * Copyright (C) 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.engineering.maven.extensions.metadata;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;

@Named
@Singleton
public class MetadataEventSpy extends AbstractEventSpy
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final String DISABLE_METADATA_EXTENSION = "metadata.extension.disable";

    private final MetadataInjection injector;

    private final HashSet<MavenProject> injected = new HashSet<>( );

    @Inject
    public MetadataEventSpy(MetadataInjection injector)
    {
        this.injector = injector;
    }

    @Override
    public void onEvent( Object event )
    {
        if ( isEventSpyDisabled() )
        {
            return;
        }

        if ( event instanceof ExecutionEvent )
        {
            final ExecutionEvent ee = (ExecutionEvent) event;
            final ExecutionEvent.Type type = ee.getType();

            // Ideally we want to use SessionStarted or ProjectDiscoveryStarted but the Project Model has not
            // been constructed. Anything after that (e.g. ProjectStarted) the Model has been constructed
            // but the phases might not be available yet. Note that ee.getMojoExecution is only valid after
            // MojoStarted phase.
            if ( type != ExecutionEvent.Type.SessionStarted && type != ExecutionEvent.Type.ProjectDiscoveryStarted )
            {
                if ( ee.getSession() != null && ee.getMojoExecution() != null && !injected.contains( ee.getProject() ) )
                {
                    try
                    {
//                        logger.info( "### Phase is {} and mojo {} ", ee.getMojoExecution().getLifecyclePhase(), ee.getMojoExecution() );

                        // Ensure we only run this once for each project once the clean* phases have completed.
                        if ( StringUtils.isNotBlank( ee.getMojoExecution().getLifecyclePhase() ) && !ee.getMojoExecution().getLifecyclePhase().contains( "clean" ) )
                        {
                            logger.info( "Activating metadata extension" );
                            logger.debug( "Running metadata extension in phase {} ", ee.getMojoExecution().getLifecyclePhase() );

                            injector.createMetadata( ee.getSession() );
                            injected.add( ee.getProject() );
                        }
                    }
                    catch ( final IOException e )
                    {
                        ee.getSession().getResult().addException( e );
                        injected.add( ee.getProject() );
                    }
                    catch ( final RuntimeException e )
                    {
                        ee.getSession().getResult().addException( e );
                        injected.add( ee.getProject() );
                    }
                }
            }
        }
    }


    private boolean isEventSpyDisabled(){
        return "true".equalsIgnoreCase(System.getProperty(DISABLE_METADATA_EXTENSION)) ||
                "true".equalsIgnoreCase(System.getenv(DISABLE_METADATA_EXTENSION));
     }
}
