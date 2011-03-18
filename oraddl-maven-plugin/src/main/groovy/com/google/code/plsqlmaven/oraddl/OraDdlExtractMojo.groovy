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
 * @goal extract
 */
public class OraDdlExtractMojo
    extends OraDdlMojo
{
    /**
    * A comma separated list of object names to extract
    * @since 1.0
    * @parameter expression="${objects}"
    */
   private String objects;

  /**
   * Whether to force extraction even if the sources directory already exists
   * @since 1.0
   * @parameter expression="${force}"
   */
   private boolean force;

   void execute()
   {
       if (checkSourceDirectory())
       {
           if (!connectToDatabase())
           {
             fail('Need an Oracle connection')
             return
           }
             
           createSources()
           disconnectFromDatabase()
       }
   }
   
   private checkSourceDirectory()
   {
       def continueExtraction= (force||!new File(sourceDirectory).exists())
       
       if (continueExtraction)
         ant.mkdir(dir: sourceDirectory)
       else
         fail('BE CAREFUL: The source directory exists... Remove it to regenerate PL/SQL source files')
         
       return (continueExtraction);
   }

   private createSources()
   {
       def objectsFilter= '';
       
       if (objects)
         objectsFilter= " and object_name in ('"+objects.split(',').collect({ it.toUpperCase() }).join("','")+"')"

       sql.eachRow("""select object_name, 
                             object_type
                        from user_objects
                       where object_type in ('SEQUENCE','TABLE','INDEX','SYNONYM') 
                         and object_name not like 'SYS\\_%' escape '\\'"""+objectsFilter)
       {
           def type= it.object_type.toLowerCase()
           def name= it.object_name.toLowerCase()
           def sourceFilePath= path("${sourceDirectory}/${type}/${name}.xml")
           ant.mkdir(dir: sourceFilePath.substring(0,sourceFilePath.lastIndexOf(File.separator)))
           ant.truncate(file: sourceFilePath)
           log.info "extracting ${sourceFilePath}"
           FileWriter writer= new FileWriter(new File(sourceFilePath))
           writer.write('<?xml version="1.0" encoding="UTF-8"?>'+"\n")
           "extract_${type}"(writer,name)
       }
   }
   
   private extract_sequence(writer,name)
   {
       def xml = new MarkupBuilder(writer)
       xml.omitNullAttributes = true
       xml.doubleQuotes = true
       sql.eachRow("select * from user_sequences where sequence_name = upper(${name})")
       {
          def seq= it.toRowResult()
          xml.sequence('name':         name, 
                       'min-value':    val(seq.min_value,1),
                       'max-value':    val(seq.max_value,999999999999999999999999999),
                       'increment-by': val(seq.increment_by,1),
                       'cache':        val(seq.cache_size,20),
                       'cycle':        (seq.cycle_flag=='Y' ? 'true' : null),
                       'order':        (seq.order_flag=='Y' ? 'true' : null));
       }
   }
   
   private extract_synonym(writer,name)
   {
       def xml = new MarkupBuilder(writer)
       xml.omitNullAttributes = true
       xml.doubleQuotes = true
       sql.eachRow("select a.*, user current_user from user_synonyms a where synonym_name = upper(${name})")
       {
          def syn= it.toRowResult()
          xml.sequence('name':         name, 
                       'for':          syn.table_name.toLowerCase(),
                       'for-owner':    val(syn.table_owner,syn.current_user)?.toLowerCase(),
                       'db-link':      syn.db_link?.toLowerCase());
       }
   }

   private extract_table(writer,name)
   {
       def xml = new MarkupBuilder(writer)
       xml.omitNullAttributes = true
       xml.doubleQuotes = true
       xml.table('name':         name)
       { 
           xml.columns()
           {
               sql.eachRow("select * from user_tab_columns a where table_name = upper(${name})")
               {
                  def col= it.toRowResult()
                  def p= col.data_type.indexOf('(')
                  
                  xml.column('name':          col.column_name.toLowerCase(), 
                             'type':          col.data_type.toLowerCase().substring(0,(p==-1?col.data_type.length():p)),
                             'precision':     col.data_precision,
                             'scale':         col.data_scale,
                             'length':        val(col.char_length,0));
               }
           }
       }
   }

   private extract_index(writer,name)
   {
       def xml = new MarkupBuilder(writer)
       xml.omitNullAttributes = true
       xml.doubleQuotes = true
       sql.eachRow("""select * 
                        from user_indexes a
                       where index_name = upper(${name})
                         and generated= 'N'
                         and index_name not in (select index_name 
                                                  from user_constraints 
                                                 where index_name is not null)""")
       {
           def ind= it.toRowResult()
           xml.index('name':         name,
                     'table':        ind.table_name,
                     'unique':       (ind.uniqueness=='UNIQUE' ? 'true' : null))
           { 
               xml.columns()
               {
                   sql.eachRow("""select column_name,
                                         column_expression,
                                         descend
                                    from user_ind_columns a,
                                         user_ind_expressions b
                                   where b.column_position(+)= a.column_position
                                     and b.index_name(+)= a.index_name
                                     and a.index_name= upper(${name})
                                order by column_position""")
                   {
                      def col= it.toRowResult()
                      
                      if (col.column_expression)
                          xml.column('expression':   col.column_expression, 
                                     'direction':    val(col.descend,'ASC')?.toLowerCase());
                      else
                          xml.column('name':         col.column_name.toLowerCase(),
                                     'direction':    val(col.descend,'ASC')?.toLowerCase());

                   }
               }
           }
       }
   }

   private val(v,d)
   {
       return (v==d ? null : v);
   }


}
