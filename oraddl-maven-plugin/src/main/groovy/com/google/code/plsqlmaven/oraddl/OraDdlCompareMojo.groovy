package com.google.code.plsqlmaven.oraddl

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

import org.apache.maven.project.MavenProject
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import com.google.code.plsqlmaven.shared.DDLWriterThread
import org.apache.maven.plugin.logging.Log

/**
 * Compare two different project trees
 * and produce the DDL to bring the compared tree
 * to the current one
 * 
 * @goal compare
 */
public class OraDdlCompareMojo
    extends OraDdlMojo
{

   /**
    * Maven project (directory) to compare with the current project
    * @since 1.9
    * @required
    * @parameter expression="${to}"
    */
   protected String to;
   
   /**
    * DDL file writer
    * @component
    */
   private DDLWriterThread ddlWriter;
   
   private static MavenProject compareProject;
   
   public static changes= [];
   
   private parser= new XmlParser();
   
   private order= ['table','sequence','synonym','view','materialized view','index']
   
   private helpers= [];
   
   private helperCache= [:];
   
   void execute()
   {
       if (!compareProject) 
       {
            compareProject= dirToProject(to);
            ddlWriter.getInstance('target','compare.sql').registerMojo(this);
       }
       
       log.info project.basedir.absolutePath

       def files= getSchemaSourceFiles()
       def objects= [:]

       for (file in files)
       {
          def target= getSourceDescriptor(file);
          def source= findSource(target,compareProject);

          if (!objects[target.type]) objects[target.type]= []

          objects[target.type] << [ 'target': target, 'source': source ]
       }

       order.each
       {
           type ->
           def helper= schemaUtils.getHelper(type)
           helpers << helper
           helperCache[type]= helper

           objects[type].each
           {
              object ->
               generateDDL(object.source,object.target);           
           }

       }
       
       
       log.info 'changes: '+changes.size()
   }
   
   private findSource(source,project)
   {
       def filePath= project.basedir.absolutePath+File.separator+
                     "src"+File.separator+
                     "main"+File.separator+
                     "schema"+File.separator+
                     source.type.replaceAll(' ','_')+File.separator+
                     source.name+'.xml';
       def file= new File(filePath);              
       def exists= file.exists();
       log.debug filePath+' exists? '+(exists ? 'yes' : 'no')
       
       if (exists)
         return schemaUtils.getSourceDescriptor(file);
       else
       {
           for (module in project.modules)
           {
               log.debug module
               def compareToSource= findSource(source,dirToProject(project.basedir.absolutePath+File.separator+module));
               
               if (compareToSource)
                 return compareToSource;
           }
           
           return null;
       }
   }
   
   private dirToProject(dir)
   {
       Model model = null
       FileReader reader = null
       MavenXpp3Reader mavenreader = new MavenXpp3Reader()
       def pomfile= new File(dir,'pom.xml')
       
       if (!pomfile.exists())
          fail('Compare project not found: '+pomfile.absolutePath);

       reader = new FileReader(pomfile)
       model = mavenreader.read(reader)
       reader.close()
       def project= new MavenProject(model)
       project.basedir= new File(dir)
       return project
   }
   
   private generateDDL(source, target)
   {
       def helper= helperCache[target.type]
       def targetXml= parser.parse(target.file)
       
       log.debug "target: ${target.file.absolutePath}"
              
       if (!source)
       {
           changes += ensureList(helper.create(targetXml))
       }
       else
       {
           def sourceXml= parser.parse(source.file);
           log.debug "source: ${source.file.absolutePath}"
           
           changes += ensureList(helper.detectChanges(sourceXml,targetXml))
       }
   }
   
   private ensureList(o)
   {
       if (o instanceof List)
         return o;
       else
         return [o];
   }

   public Log getLog()
   {
      return super.log
   }

   public reorder(changes)
   {
         helpers.each { changes = it.reorder(changes) }
         return changes
   }

}
