package com.google.code.plsqlmaven.plsql

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.nio.SelectChannelConnector

import com.google.code.eforceconfig.Config
import com.google.code.eforceconfig.EntityConfig
import com.google.code.eforceconfig.initializers.FileConfigInitializer
import com.google.code.eforceconfig.sources.managers.FileSourceManager

import com.google.code.plsqlgateway.servlet.PLSQLGatewayServlet
import com.google.code.plsqlgateway.servlet.DADContextListener

import oracle.jdbc.pool.OracleDataSource

import java.io.File

import javax.sql.DataSource

/**
 * Starts a java PL/SQL Gateway 
 * and allows to debug OWA apps 
 * 
 * @goal gateway
 *
 */
public class PlSqlGatewayMojo
    extends PlSqlMojo
{
    
   /**
    * Address to bind
    * @since 1.0
    * @parameter expression="${bindAddress}"
    */
   private String bindAddress= '127.0.0.1';

   /**
    * Port to listen
    * @since 1.0
    * @parameter expression="${port}"
    */
   private int port= 8080;

   /**
    * WebApp root directory
    * @since 1.0
    * @parameter expression="${webappRoot}"
    */
   private String webappRoot

   /**
    * WebApp context path
    * @since 1.0
    * @parameter expression="${webappContext}"
    */
   private String webappContext

   void execute()
   {
        if (!url)
          fail('Need an Oracle connection')
          
        webappRoot= (webappRoot ? webappRoot : project.basedir.absolutePath+File.separator+'src'+File.separator+'main'+File.separator+'webapp')
        webappContext= (webappContext ? webappContext: '/'+project.build.finalName)
        
        def webInfDir= webappRoot+File.separator+"WEB-INF"
        ant.mkdir(dir: webInfDir)
        //createSampleWebXML(webInfDir)      
        
        log.info "webappRoot: ${webappRoot}"
        log.info "webappContext: ${webappContext}"
        
        Server server = new Server();
        Connector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setHost(bindAddress);
        server.addConnector(connector);

        ant.mkdir(dir: project.build.directory+path('/gateway-config'));
        def webDefaultsFile= new File(project.build.directory+path('/gateway-config/webdefault.xml'))
        ant.truncate(file: webDefaultsFile.absolutePath)
        webDefaultsFile << configureDefaults(getTemplate('org/eclipse/jetty/webapp/webdefault.xml',[:]))

        WebAppContext wac = new WebAppContext();
        wac.setDefaultsDescriptor(webDefaultsFile.absolutePath);
        wac.setContextPath(webappContext);
        wac.setWar(webappRoot);
        wac.addServlet(PLSQLGatewayServlet.class, "/pls/*");
        configurePLSQLGateway(wac);
        server.setHandler(wac);
        server.setStopAtShutdown(true);

        server.start();
        
        log.info "webapp url: http://${bindAddress}:${port}${webappContext}/pls/${defaultPage}"

        server.join();
    }
    
    private DataSource getCurrentDS()
    {
        OracleDataSource ds= new OracleDataSource()
        ds.setUser(username)
        ds.setPassword(password)
        ds.setURL(url)
        return ds
    }
    
    private configurePLSQLGateway(WebAppContext wac)
    {
        wac.setInitParams(['com.google.code.eforceconfig.CONFIGSET_NAME': 'embedded'])
        
        def confDir= project.build.directory+path('/gateway-config/plsqlgateway');
        ant.mkdir(dir: confDir);
        def general= new File(confDir,'general.xml')
        ant.truncate(file: general.absolutePath)

        def userGeneral= new File(project.basedir.absolutePath+path('/src/main/webapp/WEB-INF/entity-config/plsqlgateway/general.xml'))

        if (!userGeneral.exists())
            general << this.getClass().getClassLoader().getResourceAsStream('com/google/code/plsqlmaven/plsqlgateway/config/plsqlgateway/general.xml')
        else
            general << userGeneral.getText()

        def embedded= new File(confDir,'embedded.xml')
        ant.truncate(file: embedded.absolutePath)
        
        def userEmbedded= new File(project.basedir.absolutePath+path('/src/main/webapp/WEB-INF/entity-config/plsqlgateway/embedded.xml'))

        if (!userEmbedded.exists())
          embedded << getTemplate('com/google/code/plsqlmaven/plsqlgateway/config/plsqlgateway/embedded.xml',['defaultPage': defaultPage])
        else
          embedded << userEmbedded.getText()

        FileConfigInitializer fci= new FileConfigInitializer();
        fci.setConfigSourceManager(new FileSourceManager(project.build.directory+path('/gateway-config')));
        new Config("embedded").init(fci);
        
        wac.setAttribute(DADContextListener.DAD_DATA_SOURCE+"|embedded", getCurrentDS())
    }

    private configureDefaults(text)
    {
        return text.replaceAll(/(?m)<init-param>[ \n]*?<param-name>useFileMappedBuffer<\/param-name>[ \n]*?<param-value>true<\/param-value>[ \n]*?<\/init-param>/,'''<init-param>
      <param-name>useFileMappedBuffer</param-name>
      <param-value>false</param-value>
    </init-param>''')
    }
        
}
