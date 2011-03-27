package com.google.code.plsqlmaven.webapp

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

import java.util.logging.Logger;

import javax.servlet.ServletContextListener
import javax.servlet.ServletContextEvent
import javax.servlet.ServletContext

import com.google.code.plsqlmaven.shared.PlSqlUtils
import com.google.code.plsqlmaven.shared.XdbUtils

import com.google.code.plsqlgateway.servlet.DADContextListener

import groovy.sql.Sql

import javax.naming.InitialContext


/**
 * Deploys plsql project contents into the 
 * webapp database connection
 */
class DeployerContextListener implements ServletContextListener
{
    private Logger log= Logger.getLogger(DeployerContextListener.class.getName())
    
    public void contextInitialized(ServletContextEvent event)
    {
        ServletContext ctx= event.getServletContext();
        
        def ant= new AntBuilder();
        def sql= getSql(ctx)
        def plsqlUtils= new PlSqlUtils(ant,log,sql);
        def xdbUtils= new XdbUtils(ant,log,sql);
        def schemaUtils= new SchemaUtils(ant,log,sql);
        plsqlUtils.compileDirectory(ctx.getRealPath('WEB-INF/plsql'))   
        xdbUtils.importDirectory(ctx.getRealPath('WEB-INF/xdb'))   
        schemaUtils.syncDirectory(ctx.getRealPath('WEB-INF/schema'))   
    }
    
    public void contextDestroyed(ServletContextEvent event)
    {
    }
    
    private getSql(ctx)
    {
        def ds= ctx.getAttribute(DADContextListener.DAD_DATA_SOURCE+"|embedded")
        return new Sql(ds.getConnection())        
    }
}
