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

import groovy.xml.MarkupBuilder

/**
 * Extracts schema objects to xml files
 *
 * @goal sync
 */
public class OraDdlSyncMojo
    extends OraDdlMojo
{
  /**
   * Sync only changed (from last sync) schema files
   * @since 1.0
   * @parameter expression="${changedOnly}"
   */
   private boolean changedOnly;

  /**
   * Detect changes but don't apply it
   * @since 1.0
   * @parameter expression="${detectOnly}"
   */
   private boolean detectOnly;

   void execute()
   {
       if (!connectToDatabase())
       {
         fail('Need an Oracle connection')
         return
       }
         
       syncObjects()
       disconnectFromDatabase()
   }
   
   private syncObjects()
   {
       def scanner=  ant.fileScanner
       {
           fileset(dir: sourceDirectory)
           {
               include(name: '**/*.xml')
           }
       }

       def objects= [:]
       
       for (file in scanner)
       {
           def path= file.absolutePath.split(File.separator)
           def type= path[path.length-2]
           def name= path[path.length-1].split('\\.')[0]
           if (!objects[type]) objects[type]= []
           objects[type] << ['file': file, 'name': name]
       }
       
       def order= ['table','index','sequence','synonym']
       def parser= new XmlParser();
       
       order.each
       {
           type ->
           
           objects[type].each
           {
               object ->
               def helper= getHelper(type);
               if (helper)
               {
                   log.info "sync ${type} ${object.name}"
                   def xml= parser.parse(object.file);
                   
                   if (helper.exists(xml))
                   {
                       def changes= helper.detectChanges(xml)
                       applyChanges(helper,changes);
                   }
                   else
                       helper.create(xml);
               }
           }
       }
       
   } 
   
   private applyChanges(helper,changes)
   {
       if (log.debugEnabled)
         changes.each{ log.debug it.toString() }

       if (!detectOnly)
           changes.each
           {
               change ->
               
               helper."${change.type}"(change);
           }
   }
   
}
