package com.google.code.plsqlmaven;

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
import org.mortbay.jetty.Server
import org.mortbay.jetty.Connector
import org.mortbay.jetty.webapp.WebAppContext
import org.mortbay.jetty.nio.SelectChannelConnector

import com.google.code.eforceconfig.Config;
import com.google.code.eforceconfig.EntityConfig;
import com.google.code.eforceconfig.initializers.ClassPathConfigInitializer;
import com.google.code.eforceconfig.sources.managers.ClassPathSourceManager;

import com.google.code.plsqlgateway.servlet.PLSQLGatewayServlet
import com.google.code.plsqlgateway.servlet.DADContextListener

import oracle.jdbc.pool.OracleDataSource;

import java.io.File;

import javax.sql.DataSource;

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
        
        ant.mkdir(dir: webappRoot);
        
        log.info "webappRoot: ${webappRoot}"
        log.info "webappContext: ${webappContext}"
        
        Server server = new Server();
        Connector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setHost(bindAddress);
        server.addConnector(connector);

        WebAppContext wac = new WebAppContext();
        wac.setContextPath(webappContext);
        wac.setWar(webappRoot);
        wac.addServlet(PLSQLGatewayServlet.class, "/pls/*");
        configurePLSQLGateway(wac);
        server.setHandler(wac);
        server.setStopAtShutdown(true);

        server.start();
        
        log.info "webapp url: http://${bindAddress}:${port}${webappContext}/pls/"

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
        
        ClassPathConfigInitializer cci= new ClassPathConfigInitializer();
        cci.setConfigSourceManager(new ClassPathSourceManager(this.getClass().getClassLoader(),"com.google.code.plsqlmaven.plsqlgateway.config"));
        new Config("embedded").init(cci);
        
        wac.setAttribute(DADContextListener.DAD_DATA_SOURCE+"|embedded", getCurrentDS())
    }
    
}
